package io.ecosync.integrations;

import io.ecosync.credentials.McpCredentials;
import io.ecosync.mcp.*;
import io.ecosync.organizations.TenantDirectory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnProperty(name="ecosync.mcp.enabled",havingValue="true")
public class McpRegistry {
 public record Registration(String displayName,String endpoint,String protocolPolicy,String credential){
  @Override public String toString(){return "Registration[credential=REDACTED]";}
 }
 private record Revision(UUID org,UUID id,long version,String endpoint,UUID credential,String policy,String fingerprint,String state){}
 private record Lease(long version,NativeMcpClient.Handle handle){}
 private final JdbcTemplate db;private final TenantDirectory tenants;private final McpCredentials secrets;
 private final NativeMcpClient client;private final McpSchemas schemas;private final JsonMapper json;private final TransactionTemplate tx;
 private final Set<String> endpoints;private final boolean development;
 private final String owner=UUID.randomUUID().toString();
 private final Map<String,Lease> leases=new ConcurrentHashMap<>();
 private final Object[] locks=new Object[64];
 public McpRegistry(JdbcTemplate db,TenantDirectory tenants,McpCredentials secrets,NativeMcpClient client,McpSchemas schemas,JsonMapper json,PlatformTransactionManager transactions,
   @Value("${ecosync.mcp.allowed-endpoints:}")String allowed,@Value("${ecosync.mcp.environment:production}")String environment){
  this.db=db;this.tenants=tenants;this.secrets=secrets;this.client=client;this.schemas=schemas;this.json=json;tx=new TransactionTemplate(transactions);
  endpoints=Set.copyOf(Arrays.stream(allowed.split(",")).filter(s->!s.isEmpty()).toList());development=environment.equals("development");Arrays.setAll(locks,i->new Object());
 }
 private Object lock(UUID org,UUID id){return locks[Math.floorMod(Objects.hash(org,id),locks.length)];}
 private String key(UUID org,UUID id){return org+"/"+id;}
 private List<String> roles(UUID actor,UUID org){return tenants.requireMembership(actor,org).roles();}
 private void admin(UUID actor,UUID org){if(!roles(actor,org).contains("INTEGRATION_ADMIN"))throw new AccessDeniedException("Integration administrator required");}
 private Revision revision(UUID org,UUID id){
  var rows=db.query("SELECT c.*,i.state FROM app.mcp_integration i JOIN app.mcp_configuration c ON c.organization_id=i.organization_id AND c.integration_id=i.id AND c.version=i.configuration_version WHERE i.organization_id=? AND i.id=? AND NOT i.removed",
   (rs,n)->new Revision(org,id,rs.getLong("version"),rs.getString("endpoint"),rs.getObject("credential_id",UUID.class),rs.getString("protocol_policy"),rs.getString("fingerprint"),rs.getString("state")),org,id);
  if(rows.size()!=1)throw new AccessDeniedException("Integration unavailable");return rows.getFirst();
 }
 private void validateRegistration(Registration input){
  if(input==null||input.displayName()==null||input.displayName().isBlank()||input.displayName().length()>100||input.displayName().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException();
  if(input.protocolPolicy()==null||!Set.of("AUTO_DUAL","LEGACY_2025","MODERN_2026").contains(input.protocolPolicy()))throw new IllegalArgumentException();
  try{
   URI url=URI.create(input.endpoint());
   if(input.endpoint().length()>512||!endpoints.contains(input.endpoint())||url.getHost()==null||url.getUserInfo()!=null||url.getQuery()!=null||url.getFragment()!=null||!url.normalize().toString().equals(input.endpoint())||!url.getScheme().equals("https")&&!(development&&url.getScheme().equals("http")))throw new IllegalArgumentException();
  }catch(Exception e){throw new McpFailure("MCP_DESTINATION_DENIED");}
 }
 private String fingerprint(UUID credential,Registration r){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((r.endpoint()+"\n"+r.protocolPolicy()+"\n"+credential).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException();}
 }
 public Map<String,Object> list(UUID actor,UUID org){
  var roles=roles(actor,org);
  var items=db.queryForList("""
   SELECT i.id,i.display_name,i.state,i.health,i.configuration_version,i.updated_at,i.last_connected_at,i.last_discovered_at,c.endpoint,c.protocol_policy,
    (SELECT count(*) FROM app.mcp_capability t WHERE t.organization_id=i.organization_id AND t.integration_id=i.id AND t.version=i.configuration_version AND t.present) AS discovered_tools,
    (SELECT count(DISTINCT t.id) FROM app.mcp_capability t JOIN app.mcp_approval a ON a.organization_id=t.organization_id AND a.capability_id=t.id WHERE t.organization_id=i.organization_id AND t.integration_id=i.id AND t.version=i.configuration_version AND t.present AND a.status='APPROVED') AS approved_tools,
    (SELECT max(t.protocol) FROM app.mcp_capability t WHERE t.organization_id=i.organization_id AND t.integration_id=i.id AND t.version=i.configuration_version AND t.present) AS protocol,
    (SELECT e.error_code FROM app.mcp_event e WHERE e.organization_id=i.organization_id AND e.integration_id=i.id AND e.error_code IS NOT NULL ORDER BY e.started_at DESC LIMIT 1) AS recent_error
   FROM app.mcp_integration i JOIN app.mcp_configuration c ON c.organization_id=i.organization_id AND c.integration_id=i.id AND c.version=i.configuration_version
   WHERE i.organization_id=? AND NOT removed ORDER BY i.created_at,i.id
   """,org);
  return Map.of("items",items,"available",true,"canManage",roles.contains("INTEGRATION_ADMIN"));
 }
 public Map<String,Object> detail(UUID actor,UUID org,UUID id){roles(actor,org);return view(org,id);}
 public long count(UUID actor,UUID org){roles(actor,org);return db.queryForObject("SELECT count(*) FROM app.mcp_integration WHERE organization_id=? AND NOT removed",Long.class,org);}
 private Map<String,Object> view(UUID org,UUID id){
  var rev=revision(org,id);
  Map<String,Object> result=new LinkedHashMap<>(db.queryForMap("SELECT id,display_name,state,health,configuration_version,created_by,created_at,updated_at,last_connected_at,last_discovered_at FROM app.mcp_integration WHERE organization_id=? AND id=?",org,id));
  result.put("organizationId",org);result.put("connectionType","NATIVE_MCP");result.put("endpoint",rev.endpoint);result.put("transport","STREAMABLE_HTTP");result.put("protocolPolicy",rev.policy);result.put("configurationFingerprint",rev.fingerprint);result.put("credentialConfigured",true);result.put("credentialReference",rev.credential);
  result.put("revisions",db.queryForList("SELECT version,endpoint,protocol_policy,fingerprint,created_by,created_at FROM app.mcp_configuration WHERE organization_id=? AND integration_id=? ORDER BY version DESC LIMIT 20",org,id));
  result.put("tools",capabilities(org,id,rev.version));
  result.put("events",db.queryForList("SELECT id,actor,version,capability_id,protocol,kind,status,authorization_outcome,error_code,started_at,ended_at,duration_ms FROM app.mcp_event WHERE organization_id=? AND integration_id=? ORDER BY started_at DESC,id LIMIT 30",org,id));
  return result;
 }
 private List<Map<String,Object>> capabilities(UUID org,UUID id,long version){
  return db.query("SELECT * FROM app.mcp_capability WHERE organization_id=? AND integration_id=? AND version=? ORDER BY present DESC,remote_name,discovered_at DESC LIMIT 200",(rs,n)->{
   UUID cap=rs.getObject("id",UUID.class);Map<String,Object> row=new LinkedHashMap<>();
   row.put("id",cap);row.put("alias","mcp_"+cap.toString().replace("-",""));row.put("tool",json.readValue(rs.getString("tool_json"),NativeMcpClient.Tool.class));
   row.put("protocol",rs.getString("protocol"));row.put("server",json.readValue(rs.getString("server_json"),Map.class));row.put("present",rs.getBoolean("present"));
   row.put("integrationId",id);row.put("configurationVersion",version);row.put("discoveredAt",rs.getTimestamp("discovered_at").toInstant().toString());row.put("allowedAction","TOOLS_CALL");
   row.put("approvals",db.queryForList("SELECT role,status,actor,created_at FROM app.mcp_approval WHERE organization_id=? AND capability_id=? ORDER BY (status='APPROVED') DESC,created_at DESC LIMIT 20",org,cap));return row;
  },org,id,version);
 }
 public Map<String,Object> register(UUID actor,UUID org,Registration input){
  admin(actor,org);validateRegistration(input);UUID id=UUID.randomUUID();
  try{tx.executeWithoutResult(status->{
   db.execute("SELECT pg_advisory_xact_lock(33819003)");
   Integer count=db.queryForObject("SELECT count(*) FROM app.mcp_integration WHERE NOT removed",Integer.class);if(count>=64)throw new McpFailure("MCP_CLIENT_LIMIT");
   UUID credential=secrets.store(org,input.credential());
   db.update("INSERT INTO app.mcp_integration(organization_id,id,display_name,state,configuration_version,created_by) VALUES(?,?,?,'DRAFT',1,?)",org,id,input.displayName().trim(),actor);
   insertRevision(actor,org,id,1,credential,input);event(actor,org,id,1,null,null,"REGISTER","SUCCESS","ALLOWED",null,null);
  });}catch(DuplicateKeyException e){throw new IllegalArgumentException("Duplicate integration name");}
  return view(org,id);
 }
 private void insertRevision(UUID actor,UUID org,UUID id,long version,UUID credential,Registration input){
  db.update("INSERT INTO app.mcp_configuration(organization_id,integration_id,version,endpoint,credential_id,protocol_policy,fingerprint,created_by) VALUES(?,?,?,?,?,?,?,?)",org,id,version,input.endpoint(),credential,input.protocolPolicy(),fingerprint(credential,input),actor);
 }
 public Map<String,Object> edit(UUID actor,UUID org,UUID id,Registration input){
  admin(actor,org);validateRegistration(input);
  synchronized(lock(org,id)){
   var old=revision(org,id);
   try{tx.executeWithoutResult(status->{
    UUID credential=input.credential()==null||input.credential().isEmpty()?old.credential:secrets.store(org,input.credential());
    if(db.update("UPDATE app.mcp_integration SET display_name=?,configuration_version=configuration_version+1,state='DRAFT',health='UNKNOWN',updated_at=CURRENT_TIMESTAMP WHERE organization_id=? AND id=? AND configuration_version=? AND NOT removed",input.displayName().trim(),org,id,old.version)!=1)throw new McpFailure("MCP_CLIENT_RETIRED");
    insertRevision(actor,org,id,old.version+1,credential,input);stale(org,id);if(!credential.equals(old.credential))secrets.revoke(org,old.credential);
    event(actor,org,id,old.version+1,null,null,"EDIT","SUCCESS","ALLOWED",null,null);
   });}catch(DuplicateKeyException e){throw new IllegalArgumentException("Duplicate integration name");}
   retire(org,id,true);
  }
  return view(org,id);
 }
 private void stale(UUID org,UUID id){
  db.update("UPDATE app.mcp_approval SET status='STALE' WHERE organization_id=? AND status='APPROVED' AND capability_id IN(SELECT id FROM app.mcp_capability WHERE organization_id=? AND integration_id=?)",org,org,id);
 }
 private void retire(UUID org,UUID id,boolean revoke){var lease=leases.remove(key(org,id));if(lease!=null)try{client.close(lease.handle,revoke);}catch(McpFailure ignored){/* DB remains authoritative; adapter TTL bounds cleanup. */}}
 public Map<String,Object> disable(UUID actor,UUID org,UUID id,boolean remove){
  admin(actor,org);synchronized(lock(org,id)){
   var rev=revision(org,id);tx.executeWithoutResult(status->{
    db.update("UPDATE app.mcp_integration SET state='DISABLED',removed=?,updated_at=CURRENT_TIMESTAMP WHERE organization_id=? AND id=?",remove,org,id);stale(org,id);
    if(remove)secrets.revoke(org,rev.credential);event(actor,org,id,rev.version,null,null,remove?"REMOVE":"DISABLE","SUCCESS","ALLOWED",null,null);
   });retire(org,id,true);
  }return remove?Map.of("removed",true):view(org,id);
 }
 private NativeMcpClient.Snapshot connect(Revision r){
  try{
   var snapshot=client.connect(new NativeMcpClient.Connect(r.org,r.id,r.version,r.fingerprint,owner,r.endpoint,secrets.resolve(r.org,r.credential),r.policy));
   leases.compute(key(r.org,r.id),(key,previous)->previous==null||previous.version<=r.version?new Lease(r.version,snapshot.handle()):previous);return snapshot;
  }catch(McpFailure e){throw e;}catch(Exception e){throw new McpFailure("MCP_UNAVAILABLE");}
 }
 public Map<String,Object> validate(UUID actor,UUID org,UUID id){
  admin(actor,org);Revision rev;
  synchronized(lock(org,id)){
   rev=revision(org,id);if(rev.state.equals("DISABLED"))throw new McpFailure("MCP_DISABLED");
   if(db.update("UPDATE app.mcp_integration SET state='VALIDATING',updated_at=CURRENT_TIMESTAMP WHERE organization_id=? AND id=? AND configuration_version=? AND state<>'VALIDATING' AND state<>'DISABLED' AND NOT removed",org,id,rev.version)!=1)throw new McpFailure("MCP_CONNECTION_BUSY");
  }
  try{
   var snapshot=connect(rev);snapshot=client.discover(snapshot.handle());
   synchronized(lock(org,id)){persistDiscovery(actor,rev,snapshot,true);}
   return view(org,id);
  }catch(McpFailure e){
   db.update("UPDATE app.mcp_integration SET state='DEGRADED',health='UNREACHABLE',updated_at=CURRENT_TIMESTAMP WHERE organization_id=? AND id=? AND configuration_version=? AND state<>'DISABLED' AND NOT removed",org,id,rev.version);
   event(actor,org,id,rev.version,null,null,"VALIDATE","FAILED","ALLOWED",e.code(),null);throw e;
  }
 }
 private void persistDiscovery(UUID actor,Revision rev,NativeMcpClient.Snapshot snapshot,boolean validation){
  tx.executeWithoutResult(status->{
   String current=validation?"VALIDATING":"ACTIVE";
   if(db.update("UPDATE app.mcp_integration SET health='HEALTHY',state=CASE WHEN state='VALIDATING' THEN 'DRAFT' ELSE state END,last_connected_at=CURRENT_TIMESTAMP,last_discovered_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE organization_id=? AND id=? AND configuration_version=? AND state=? AND NOT removed",rev.org,rev.id,rev.version,current)!=1){try{client.close(snapshot.handle(),true);}catch(McpFailure ignored){}throw new McpFailure("MCP_CLIENT_RETIRED");}
   db.update("UPDATE app.mcp_capability SET present=false WHERE organization_id=? AND integration_id=? AND version=?",rev.org,rev.id,rev.version);
   for(var remote:snapshot.tools()){
    boolean supported=remote.supported()&&schemas.supported(remote.inputSchema())&&(remote.outputSchema()==null||schemas.supported(remote.outputSchema()));
    var tool=new NativeMcpClient.Tool(remote.name(),remote.description(),remote.inputSchema(),remote.outputSchema(),remote.annotations(),remote.metadata(),remote.fingerprint(),supported,supported?null:"Host schema policy rejected this capability");
    db.update("INSERT INTO app.mcp_capability(organization_id,integration_id,version,id,remote_name,fingerprint,tool_json,protocol,server_json) VALUES(?,?,?,?,?,?,?::jsonb,?,?::jsonb) ON CONFLICT(organization_id,integration_id,version,remote_name,fingerprint) DO UPDATE SET present=true,discovered_at=CURRENT_TIMESTAMP",rev.org,rev.id,rev.version,UUID.randomUUID(),tool.name(),tool.fingerprint(),json.writeValueAsString(tool),snapshot.protocol(),json.writeValueAsString(snapshot.serverInfo()));
   }
   db.update("UPDATE app.mcp_approval SET status='STALE' WHERE organization_id=? AND status='APPROVED' AND capability_id IN(SELECT id FROM app.mcp_capability WHERE organization_id=? AND integration_id=? AND version=? AND NOT present)",rev.org,rev.org,rev.id,rev.version);
   event(actor,rev.org,rev.id,rev.version,null,snapshot.protocol(),"DISCOVER","SUCCESS","ALLOWED",null,null);
  });
 }
 public Map<String,Object> approve(UUID actor,UUID org,UUID id,UUID capability,String role,boolean approve){
  admin(actor,org);if(!Set.of("INTEGRATION_ADMIN","USER").contains(role))throw new IllegalArgumentException();
  synchronized(lock(org,id)){var rev=revision(org,id);
   if(rev.state.equals("DISABLED")||rev.state.equals("VALIDATING"))throw new McpFailure("MCP_CLIENT_RETIRED");
   var tool=tool(org,id,rev.version,capability);
   if(approve&&(!tool.supported()||!schemas.supported(tool.inputSchema())))throw new McpFailure("MCP_SCHEMA_UNSUPPORTED");
   tx.executeWithoutResult(status->{
    db.update("UPDATE app.mcp_approval SET status='REVOKED' WHERE organization_id=? AND capability_id=? AND role=? AND status='APPROVED'",org,capability,role);
    if(approve)db.update("INSERT INTO app.mcp_approval(organization_id,id,capability_id,role,status,actor) VALUES(?,?,?,?,'APPROVED',?)",org,UUID.randomUUID(),capability,role,actor);
    event(actor,org,id,rev.version,capability,null,approve?"APPROVE":"REVOKE","SUCCESS","ALLOWED",null,null);
   });
  }return view(org,id);
 }
 private NativeMcpClient.Tool tool(UUID org,UUID id,long version,UUID capability){
  var rows=db.query("SELECT tool_json FROM app.mcp_capability WHERE organization_id=? AND integration_id=? AND version=? AND id=? AND present",(rs,n)->json.readValue(rs.getString(1),NativeMcpClient.Tool.class),org,id,version,capability);
  if(rows.size()!=1)throw new AccessDeniedException("Capability unavailable");return rows.getFirst();
 }
 public Map<String,Object> activate(UUID actor,UUID org,UUID id){
  admin(actor,org);synchronized(lock(org,id)){
   var rev=revision(org,id);
   if(db.update("UPDATE app.mcp_integration i SET state='ACTIVE',updated_at=CURRENT_TIMESTAMP WHERE organization_id=? AND id=? AND configuration_version=? AND state='DRAFT' AND health='HEALTHY' AND EXISTS(SELECT 1 FROM app.mcp_capability c JOIN app.mcp_approval a ON a.organization_id=c.organization_id AND a.capability_id=c.id WHERE c.organization_id=i.organization_id AND c.integration_id=i.id AND c.version=i.configuration_version AND c.present AND a.status='APPROVED')",org,id,rev.version)!=1)throw new McpFailure("MCP_CLIENT_RETIRED");
   event(actor,org,id,rev.version,null,null,"ACTIVATE","SUCCESS","ALLOWED",null,null);
  }return view(org,id);
 }
 private NativeMcpClient.Handle ensure(Revision rev,UUID actor){
  var lease=leases.get(key(rev.org,rev.id));
  if(lease!=null&&lease.version==rev.version)return lease.handle;
  var snapshot=connect(rev);snapshot=client.discover(snapshot.handle());persistDiscovery(actor,rev,snapshot,false);return snapshot.handle();
 }
 public Map<String,Object> invoke(UUID actor,UUID org,UUID id,UUID capability,UUID invocation,Map<String,Object> arguments){
  var roles=roles(actor,org);Revision rev;NativeMcpClient.Tool tool;NativeMcpClient.Handle handle;
  Instant start=Instant.now();
  synchronized(lock(org,id)){
   rev=revision(org,id);
   if(!rev.state.equals("ACTIVE")){event(actor,org,id,rev.version,capability,null,"INVOKE","DENIED","DENIED","MCP_CLIENT_RETIRED",start);throw new McpFailure("MCP_CLIENT_RETIRED");}
   tool=tool(org,id,rev.version,capability);
   List<String> granted=db.queryForList("SELECT role FROM app.mcp_approval WHERE organization_id=? AND capability_id=? AND status='APPROVED'",String.class,org,capability);
   if(roles.stream().noneMatch(granted::contains)){event(actor,org,id,rev.version,capability,null,"INVOKE","DENIED","DENIED",null,start);throw new AccessDeniedException("Capability role grant required");}
   schemas.arguments(tool.inputSchema(),arguments);handle=ensure(rev,actor);
   // Recovery discovery can invalidate the previously loaded capability.
   tool=tool(org,id,rev.version,capability);
   try{db.update("INSERT INTO app.mcp_event(organization_id,integration_id,id,actor,version,capability_id,kind,status,authorization_outcome) VALUES(?,?,?,?,?,?,'INVOKE','RUNNING','ALLOWED')",org,id,invocation,actor,rev.version,capability);}catch(DuplicateKeyException e){throw new McpFailure("MCP_CONNECTION_BUSY");}
  }
  try{
   var outcome=client.invoke(handle,tool.name(),tool.fingerprint(),arguments,invocation);
   if(tool.outputSchema()!=null)schemas.output(tool.outputSchema(),outcome.result().get("structuredContent"));
   finish(org,invocation,"SUCCESS",outcome.protocol(),null,start);
   return Map.of("invocationId",invocation,"protocol",outcome.protocol(),"durationMs",java.time.Duration.between(start,Instant.now()).toMillis(),"result",outcome.result(),"untrusted",true);
  }catch(McpFailure e){
   finish(org,invocation,e.code().equals("MCP_CANCELLED")?"CANCELLED":"FAILED",null,e.code(),start);
   if(e.code().equals("MCP_SCHEMA_CHANGED"))synchronized(lock(org,id)){
    stale(org,id);try{var snapshot=client.discover(handle);persistDiscovery(actor,rev,snapshot,false);}catch(McpFailure ignored){}
   }
   else if(!Set.of("MCP_CANCELLED","MCP_CLIENT_RETIRED","MCP_INVALID_ARGUMENTS").contains(e.code()))db.update("UPDATE app.mcp_integration SET health='UNREACHABLE' WHERE organization_id=? AND id=? AND configuration_version=? AND state='ACTIVE'",org,id,rev.version);
   throw e;
  }
 }
 public Map<String,Object> cancel(UUID actor,UUID org,UUID id,UUID invocation){
  var roles=roles(actor,org);revision(org,id);
  var actors=db.queryForList("SELECT actor FROM app.mcp_event WHERE organization_id=? AND integration_id=? AND id=? AND kind='INVOKE' AND status='RUNNING'",UUID.class,org,id,invocation);
  if(actors.size()!=1||!actors.getFirst().equals(actor)&&!roles.contains("INTEGRATION_ADMIN"))throw new AccessDeniedException("Invocation unavailable");
  var lease=leases.get(key(org,id));return Map.of("cancelled",lease!=null&&client.cancel(lease.handle,invocation));
 }
 private void finish(UUID org,UUID invocation,String status,String protocol,String code,Instant start){db.update("UPDATE app.mcp_event SET status=?,protocol=?,error_code=?,ended_at=CURRENT_TIMESTAMP,duration_ms=? WHERE organization_id=? AND id=? AND status='RUNNING'",status,protocol,code,java.time.Duration.between(start,Instant.now()).toMillis(),org,invocation);}
 private void event(UUID actor,UUID org,UUID id,long version,UUID cap,String protocol,String kind,String status,String authorization_outcome,String code,Instant start){
  db.update("INSERT INTO app.mcp_event(organization_id,integration_id,id,actor,version,capability_id,protocol,kind,status,authorization_outcome,error_code,ended_at,duration_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",org,id,UUID.randomUUID(),actor,version,cap,protocol,kind,status,authorization_outcome,code,start==null?0:java.time.Duration.between(start,Instant.now()).toMillis());
 }
 @jakarta.annotation.PostConstruct public void recover(){
  db.update("UPDATE app.mcp_event SET status='INTERRUPTED',error_code='MCP_UNAVAILABLE',ended_at=CURRENT_TIMESTAMP,duration_ms=EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-started_at))*1000 WHERE status='RUNNING'");
 }
 @jakarta.annotation.PreDestroy public void shutdown(){
  try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=leases.values().stream().<java.util.concurrent.Callable<Void>>map(lease->()->{try{client.close(lease.handle,false);}catch(Exception ignored){}return null;}).toList();
   executor.invokeAll(tasks,2,java.util.concurrent.TimeUnit.SECONDS);
  }catch(InterruptedException e){Thread.currentThread().interrupt();}finally{leases.clear();}
 }
}
