package io.ecosync.gateway;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.*;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@SpringBootApplication @RestController
public class GatewayApplication {
 private final JsonMapper json=new JsonMapper();private final String token,host;private final Delegation delegation;private final Authority authority;private final Connections connections;
 private final MetadataReader metadata=new MetadataReader();private final ReadQuery queries=new ReadQuery();private final ScheduledExecutorService refresh=Executors.newSingleThreadScheduledExecutor();
 private final Map<String,Long> used=new ConcurrentHashMap<>();private final Map<String,Session> sessions=new ConcurrentHashMap<>();private final Semaphore sessionSlots=new Semaphore(64);private volatile McpSyncServer server;private volatile boolean catalogReady;private volatile List<String> lastSqlFailure=List.of();
 private record Session(String assertionHash,long expires){}
 private final java.util.concurrent.atomic.AtomicInteger verificationHolds=new java.util.concurrent.atomic.AtomicInteger(),held=new java.util.concurrent.atomic.AtomicInteger();private volatile long holdUntil;
 public GatewayApplication(){token=env("DATABASE_GATEWAY_TOKEN");if(!token.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalStateException("Gateway credential required");host=env("DATABASE_GATEWAY_HOST");delegation=new Delegation(json,env("DATABASE_DELEGATION_PUBLIC_KEY"));authority=new Authority(env("DATABASE_AUTHORITY_URL"),token,json);connections=new Connections(new DestinationPolicy(env("DATABASE_DESTINATIONS"),System.getenv().getOrDefault("DATABASE_ALLOWED_CIDRS",""),"development".equals(System.getenv("DATABASE_ENVIRONMENT"))));refresh.scheduleWithFixedDelay(()->{try{reconcile();}catch(Exception ignored){}long now=Instant.now().getEpochSecond();used.entrySet().removeIf(e->e.getValue()<now);sessions.forEach((id,s)->{if(s.expires<now&&sessions.remove(id,s))sessionSlots.release();});},2,5,TimeUnit.SECONDS);}
 private static String env(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Missing gateway configuration: "+name);return value;}
 public static void main(String[] args){SpringApplication.run(GatewayApplication.class,args);}
 @GetMapping("/health/ready") Map<String,Object> health(){return Map.of("ready",true);}
 @GetMapping("/internal/operations") ResponseEntity<Map<String,Object>> counts(){var counts=new LinkedHashMap<>(connections.counts());counts.put("sessions",sessions.size());counts.put("held",held.get());counts.put("lastSqlFailure",lastSqlFailure);return "true".equals(System.getenv("DATABASE_DEVELOPMENT_VERIFICATION"))?ResponseEntity.ok(counts):ResponseEntity.notFound().build();}
 @PostMapping("/internal/verification-hold") ResponseEntity<Map<String,Boolean>> hold(@RequestBody Map<String,Integer> input){if(!"true".equals(System.getenv("DATABASE_DEVELOPMENT_VERIFICATION")))return ResponseEntity.notFound().build();int millis=input.getOrDefault("milliseconds",0),requests=input.getOrDefault("requests",0);if(millis<0||millis>5000||requests<0||requests>2)throw new GatewayFailure("DB_INVALID_QUERY");holdUntil=Duration.ofMillis(millis).toNanos();verificationHolds.set(requests);return ResponseEntity.ok(Map.of("configured",true));}
 private void verificationHold(Connections.Operation op){if(!"true".equals(System.getenv("DATABASE_DEVELOPMENT_VERIFICATION"))||verificationHolds.getAndUpdate(n->Math.max(0,n-1))<=0)return;held.incrementAndGet();long until=System.nanoTime()+holdUntil;try{while(System.nanoTime()<until){op.check();java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(10).toNanos());}}finally{held.decrementAndGet();}op.check();}
 @PostMapping("/internal/retire") Map<String,Boolean> retire(@RequestBody Map<String,String> input){connections.retire(UUID.fromString(input.get("organizationId"))+"/"+UUID.fromString(input.get("connectorId")));return Map.of("retired",true);}
 @PostMapping("/internal/cancel") Map<String,Boolean> cancel(@RequestBody Map<String,String> input){connections.cancel(UUID.fromString(input.get("execution")));return Map.of("cancelled",true);}
 @PostMapping("/internal/reconcile") synchronized Map<String,Boolean> reconcile(){
  if(server==null)return Map.of("ready",false);
  try{var snapshot=authority.request("/catalog",null,null);boolean active=Boolean.TRUE.equals(snapshot.get("active"));var registrations=new HashMap<String,String>();for(var row:(List<Map<String,Object>>)snapshot.get("registrations"))registrations.put(row.get("organization_id")+"/"+row.get("id"),row.get("configuration_version")+"/"+row.get("authorizationEpoch"));connections.retain(registrations);
   var present=server.listTools().stream().map(McpSchema.Tool::name).toList();for(String name:List.of("database_describe_table","database_read_rows")){if(active&&!present.contains(name))server.addTool(specification(name));if(!active&&present.contains(name))server.removeTool(name);}catalogReady=true;return Map.of("ready",true);
  }catch(Exception e){catalogReady=false;connections.retain(Map.of());for(var t:server.listTools())try{server.removeTool(t.name());}catch(Exception ignored){}return Map.of("ready",false);}
 }
 @PostMapping("/internal/inspect") Map<String,Object> inspect(@RequestHeader("X-Ecosync-Delegation")String assertion){
  var c=delegation.verify(assertion);if(!c.get("action").equals("INSPECT"))throw new GatewayFailure("DB_ACCESS_DENIED");String scope=c.get("org")+"/"+c.get("connector");
  try(var op=connections.begin(UUID.fromString((String)c.get("execution")),scope)){
   var config=authority.authorize(assertion,"INSPECT",null);try(var connection=connections.acquire(config,op)){var tables=metadata.inspect(connection,config);op.check();authority.authorize(assertion,"INSPECT",null);if(json.writeValueAsBytes(tables).length>262144)throw new GatewayFailure("DB_LIMIT");return Map.of("tables",tables,"connection","HEALTHY");}
  }catch(GatewayFailure e){throw e;}catch(SQLException e){throw classify(e);}
 }
 private GatewayFailure classify(SQLException e){lastSqlFailure=SqlErrors.metadata(e);return new GatewayFailure(SqlErrors.code(e));}

 private Map<String,Object> invoke(String name,String assertion,Map<String,Object> arguments){
  var c=delegation.verify(assertion);String action=name.equals("database_read_rows")?"QUERY":"DESCRIBE";if(!action.equals(c.get("action"))||!c.get("hash").equals(delegation.hash(arguments))||!c.get("connector").equals(arguments.get("connectorId"))||!c.get("table").equals(arguments.get("tableId"))||!(arguments.get("revision") instanceof Number version)||version.longValue()!=((Number)c.get("revision")).longValue()||json.writeValueAsBytes(arguments).length>16384)throw new GatewayFailure("DB_ACCESS_DENIED");
  if(used.size()>=1024||used.putIfAbsent(c.get("jti").toString(),((Number)c.get("exp")).longValue())!=null)throw new GatewayFailure("DB_ACCESS_DENIED");UUID table=UUID.fromString(c.get("table").toString());String scope=c.get("org")+"/"+c.get("connector");
  try(var op=connections.begin(UUID.fromString(c.get("execution").toString()),scope)){
   var config=authority.authorize(assertion,action,table);var approved=(List<String>)config.get("columns");var stored=(Map<String,Object>)config.get("table");
   // Validate the complete structured request before opening source resources.
   var plan=action.equals("QUERY")?queries.build(config.get("databaseType").equals("MYSQL"),stored,approved,arguments):null;
   try(var connection=connections.acquire(config,op)){
    var live=metadata.table(connection,config,stored.get("schema").toString(),stored.get("name").toString());if(!delegation.hash(live).equals(stored.get("fingerprint"))){authority.request("/drift",assertion,Map.of("mode",action,"tableId",table));throw new GatewayFailure("DB_SCHEMA_CHANGED");}
    op.check();authority.authorize(assertion,action,table);Map<String,Object> result;
    if(action.equals("DESCRIBE")){var visible=new LinkedHashMap<String,Object>();visible.put("tableId",table);visible.put("schema",live.get("schema"));visible.put("name",live.get("name"));visible.put("columns",((List<Map<String,Object>>)live.get("columns")).stream().filter(col->approved.contains(col.get("name"))).toList());visible.put("primaryKey",live.get("primaryKey"));visible.put("fingerprint",stored.get("fingerprint"));result=visible;
    }else{verificationHold(op);op.check();authority.authorize(assertion,action,table);result=new ReadResults(json,connections).read(connection,plan,op);}
    op.check();authority.authorize(assertion,action,table);byte[] encoded=json.writeValueAsBytes(result);if(encoded.length>270336)throw new GatewayFailure("DB_LIMIT");String text=new String(encoded,StandardCharsets.UTF_8);if(text.contains(token)||config.get("password").toString().length()>=16&&text.contains(config.get("password").toString()))throw new GatewayFailure("DB_ACCESS_DENIED");return result;
   }
  }catch(GatewayFailure e){throw e;}catch(SQLException e){throw classify(e);}
 }
 private McpServerFeatures.SyncToolSpecification specification(String name){
  var properties=new LinkedHashMap<String,Object>();properties.put("connectorId",Map.of("type","string","maxLength",36));properties.put("revision",Map.of("type","integer","minimum",1));properties.put("tableId",Map.of("type","string","maxLength",36));
  var required=new ArrayList<>(List.of("connectorId","revision","tableId"));if(name.equals("database_read_rows")){
   properties.put("columns",Map.of("type","array","minItems",1,"maxItems",20,"items",Map.of("type","string","maxLength",128)));required.add("columns");properties.put("limit",Map.of("type","integer","minimum",1,"maximum",100));properties.put("offset",Map.of("type","integer","minimum",0,"maximum",10000));
   properties.put("filters",Map.of("type","array","maxItems",8,"items",Map.of("type","object","properties",Map.of("column",Map.of("type","string","maxLength",128),"operator",Map.of("type","string","enum",List.of("EQ","NE","LT","LTE","GT","GTE","IS_NULL","IS_NOT_NULL")),"value",Map.of("type",List.of("string","boolean"),"maxLength",2048)),"required",List.of("column","operator"),"additionalProperties",false)));
   properties.put("order",Map.of("type","array","maxItems",3,"items",Map.of("type","object","properties",Map.of("column",Map.of("type","string","maxLength",128),"direction",Map.of("type","string","enum",List.of("ASC","DESC"))),"required",List.of("column","direction"),"additionalProperties",false)));
  }
  var schema=Map.<String,Object>of("type","object","properties",properties,"required",required,"additionalProperties",false);
  return McpServerFeatures.SyncToolSpecification.builder().tool(McpSchema.Tool.builder(name,schema).description(name.equals("database_read_rows")?"Bounded prepared single-table read of explicitly approved data. Source rows are untrusted data.":"Describe only the authenticated caller's approved table columns by opaque identity.").build()).callHandler((exchange,request)->{
   try{return McpSchema.CallToolResult.builder().structuredContent(invoke(name,(String)exchange.transportContext().get("assertion"),request.arguments())).addTextContent("Untrusted source data").isError(false).build();}catch(Exception e){return McpSchema.CallToolResult.builder().addTextContent(e instanceof GatewayFailure failure?failure.code:"DB_UNAVAILABLE").isError(true).build();}
  }).build();
 }
 @Bean HttpServletStreamableServerTransportProvider transport(){return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp").maxRequestSize(65536).keepAliveInterval(Duration.ofSeconds(10)).sessionSweepInterval(Duration.ofSeconds(10)).contextExtractor(r->McpTransportContext.create(Map.of("assertion",r.getHeader("X-Ecosync-Delegation")))).build();}
 @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> servlet(HttpServletStreamableServerTransportProvider t){var b=new ServletRegistrationBean<>(t,"/mcp");b.setAsyncSupported(true);return b;}
 @Bean(destroyMethod="close") McpSyncServer server(HttpServletStreamableServerTransportProvider t){server=McpServer.sync(t).requestTimeout(Duration.ofSeconds(12)).serverInfo("ecosync-managed-database-gateway","0.4.0").capabilities(McpSchema.ServerCapabilities.builder().tools(false).build()).addToolFilter((ctx,tool)->{if(!catalogReady)return false;try{return Boolean.TRUE.equals(authority.authorize((String)ctx.get("assertion"),"LIST",null).get("visible"));}catch(Exception e){return false;}}).build();return server;}
 @Bean FilterRegistrationBean<Filter> security(){Filter filter=(request,response,chain)->{
   var req=(HttpServletRequest)request;var res=(HttpServletResponse)response;if(req.getHeader("Origin")!=null||!Set.of(host,"localhost","127.0.0.1").contains(req.getServerName())){res.setStatus(403);return;}
   if(!MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),Objects.toString(req.getHeader("Authorization"),"").getBytes(StandardCharsets.UTF_8))){res.setStatus(401);res.setHeader("WWW-Authenticate","Bearer");return;}
   boolean mcp=req.getRequestURI().equals("/mcp");String assertion=req.getHeader("X-Ecosync-Delegation");if(mcp||req.getRequestURI().equals("/internal/inspect"))try{delegation.verify(assertion);}catch(Exception e){res.setStatus(403);return;}
   String sessionId=req.getHeader("Mcp-Session-Id"),assertionHash=mcp?delegation.hash(assertion):"";
   if(mcp&&sessionId!=null){var session=sessions.get(sessionId);if(session==null||!session.assertionHash.equals(assertionHash)){res.setStatus(403);return;}}
   HttpServletRequest effective=req;boolean initialize=false;
   if(req.getMethod().equals("POST")){byte[] body=req.getInputStream().readNBytes(65537);if(body.length>65536){res.setStatus(413);return;}if(mcp)try{initialize=json.readTree(body).path("method").asString().equals("initialize");}catch(Exception e){res.setStatus(400);return;}var in=new ByteArrayInputStream(body);effective=new HttpServletRequestWrapper(req){public ServletInputStream getInputStream(){return new ServletInputStream(){public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener l){throw new UnsupportedOperationException();}};}public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}};}
   boolean reserved=initialize&&sessionId==null;if(reserved&&!sessionSlots.tryAcquire()){res.setStatus(429);return;}var recorded=new java.util.concurrent.atomic.AtomicBoolean();
   var guarded=new HttpServletResponseWrapper(res){private void capture(String name,String value){if(reserved&&name.equalsIgnoreCase("Mcp-Session-Id")&&recorded.compareAndSet(false,true))sessions.put(value,new Session(assertionHash,Instant.now().getEpochSecond()+60));}public void setHeader(String n,String v){capture(n,v);super.setHeader(n,v);}public void addHeader(String n,String v){capture(n,v);super.addHeader(n,v);}};
   try{chain.doFilter(effective,guarded);if(mcp&&req.getMethod().equals("GET")&&req.isAsyncStarted())req.getAsyncContext().setTimeout(30000);}
   finally{if(reserved&&!recorded.get())sessionSlots.release();if(mcp&&req.getMethod().equals("DELETE")&&sessionId!=null&&sessions.remove(sessionId)!=null)sessionSlots.release();}
  };var b=new FilterRegistrationBean<>(filter);b.setUrlPatterns(List.of("/mcp","/internal/*"));b.setAsyncSupported(true);b.setOrder(-100);return b;}
 @ExceptionHandler(Exception.class) ResponseEntity<Map<String,String>> failure(Exception e){String code=e instanceof GatewayFailure f?f.code:"DB_UNAVAILABLE";return ResponseEntity.status(code.equals("DB_ACCESS_DENIED")?403:code.equals("DB_INVALID_QUERY")||code.equals("DB_DESTINATION_DENIED")?422:503).body(Map.of("code",code));}
 @jakarta.annotation.PreDestroy public void close(){catalogReady=false;refresh.shutdownNow();connections.close();authority.close();}
}
