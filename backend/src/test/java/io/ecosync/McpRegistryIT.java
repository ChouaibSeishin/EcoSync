package io.ecosync;

import io.ecosync.credentials.McpCredentials;
import io.ecosync.integrations.McpRegistry;
import io.ecosync.mcp.*;
import io.ecosync.organizations.TenantDirectory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL and crypto; controlled client port isolates registry races, not a wire-conformance claim. */
@Testcontainers @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpRegistryIT {
 @Container static final PostgreSQLContainer pg=new PostgreSQLContainer(DockerImageName.parse("postgres:17.11-bookworm@sha256:2cd2c0f809604aaeff6f0ec8f40aecbd025613107561015c69369a1a7f1d3416").asCompatibleSubstituteFor("postgres"));
 @TempDir Path temp;
 JdbcTemplate db;DriverManagerDataSource ds;JsonMapper json=JsonMapper.builder().build();
 McpCredentials secrets;McpRegistry registry;Peer peer;UUID org,other,admin,user,auditor;Path keyring;
 @BeforeAll void database(){
  pg.start();ds=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());db=new JdbcTemplate(ds);
  Flyway.configure().dataSource(ds).schemas("app").defaultSchema("app").load().migrate();
 }
 @BeforeEach void setup()throws Exception{
  org=UUID.randomUUID();other=UUID.randomUUID();admin=UUID.randomUUID();user=UUID.randomUUID();auditor=UUID.randomUUID();
  db.update("INSERT INTO app.organization(id,slug,display_name) VALUES(?,?,?),(?,?,?)",org,org.toString(),"First",other,other.toString(),"Other");
  for(var entry:Map.of(admin,"INTEGRATION_ADMIN",user,"USER",auditor,"AUDITOR").entrySet()){
   UUID member=UUID.randomUUID();db.update("INSERT INTO app.app_user(id,login,display_name,password_hash,platform_admin) VALUES(?,?,?,?,?)",entry.getKey(),entry.getKey().toString(),"Fixture","disabled-fixture",entry.getKey().equals(admin));
   db.update("INSERT INTO app.membership VALUES(?,?,?)",org,member,entry.getKey());db.update("INSERT INTO app.role_assignment VALUES(?,?,?)",org,member,entry.getValue());
  }
  keyring=temp.resolve(UUID.randomUUID()+".json");byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
  Files.writeString(keyring,json.writeValueAsString(Map.of("activeKeyId","v1","keys",Map.of("v1",Base64.getEncoder().encodeToString(key)))));
  secrets=new McpCredentials(db,json,keyring.toString());peer=new Peer();registry=build(secrets);
 }
 McpRegistry build(McpCredentials credentials){return new McpRegistry(db,new TenantDirectory(db),credentials,peer,new McpSchemas(json),json,new JdbcTransactionManager(ds),"http://peer:8091/mcp","development");}
 McpRegistry.Registration input(String name){return new McpRegistry.Registration(name,"http://peer:8091/mcp","AUTO_DUAL","credential-sentinel-"+UUID.randomUUID());}
 UUID register(){return (UUID)registry.register(admin,org,input("Native "+UUID.randomUUID())).get("id");}
 UUID capability(UUID id){return db.queryForObject("SELECT id FROM app.mcp_capability WHERE organization_id=? AND integration_id=? AND present AND version=(SELECT configuration_version FROM app.mcp_integration WHERE organization_id=? AND id=?)",UUID.class,org,id,org,id);}
 void activate(UUID id){registry.validate(admin,org,id);registry.approve(admin,org,id,capability(id),"INTEGRATION_ADMIN",true);registry.activate(admin,org,id);}
 @Test void persistenceCiphertextAndRestartRecovery(){
  var registration=input("Persistent");UUID id=(UUID)registry.register(admin,org,registration).get("id");activate(id);
  byte[] cipher=db.queryForObject("SELECT ciphertext FROM app.mcp_credential WHERE organization_id=?",byte[].class,org);
  assertThat(new String(cipher,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(registration.credential());
  assertThat(json.writeValueAsString(registry.detail(admin,org,id))).doesNotContain(registration.credential()).doesNotContain("ciphertext");
  registry.shutdown();registry=build(new McpCredentials(db,json,keyring.toString()));
  registry.invoke(admin,org,id,capability(id),UUID.randomUUID(),Map.of("value","ok"));assertThat(peer.calls.get()).isEqualTo(1);
  assertThat(db.queryForObject("SELECT count(*) FROM app.mcp_event WHERE organization_id=? AND kind='INVOKE' AND status='SUCCESS'",Integer.class,org)).isEqualTo(1);
 }
 @Test void tenantRolesGrantsAndDisablePreventSourceCalls(){
  UUID id=register();registry.validate(admin,org,id);UUID cap=capability(id);
  assertThatThrownBy(()->registry.activate(admin,org,id)).isInstanceOf(McpFailure.class);
  assertThatThrownBy(()->registry.register(user,org,input("Denied"))).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->registry.detail(admin,other,id)).isInstanceOf(AccessDeniedException.class);
  registry.approve(admin,org,id,cap,"INTEGRATION_ADMIN",true);registry.activate(admin,org,id);
  assertThatThrownBy(()->registry.invoke(user,org,id,cap,UUID.randomUUID(),Map.of("value","no"))).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->registry.invoke(auditor,org,id,cap,UUID.randomUUID(),Map.of("value","no"))).isInstanceOf(AccessDeniedException.class);
  registry.approve(admin,org,id,cap,"USER",true);registry.invoke(user,org,id,cap,UUID.randomUUID(),Map.of("value","yes"));
  registry.approve(admin,org,id,cap,"USER",false);
  assertThatThrownBy(()->registry.invoke(user,org,id,cap,UUID.randomUUID(),Map.of("value","no"))).isInstanceOf(AccessDeniedException.class);
  registry.disable(admin,org,id,false);
  assertThatThrownBy(()->registry.invoke(admin,org,id,cap,UUID.randomUUID(),Map.of("value","no"))).isInstanceOf(McpFailure.class);
  assertThat(peer.calls.get()).isEqualTo(1);assertThat(peer.closed.get()).isPositive();
 }
 @Test void editsInvalidateApprovalsAndSchemasRequireReview(){
  UUID id=register();activate(id);UUID cap=capability(id);
  registry.edit(admin,org,id,input("Changed"));assertThat(registry.detail(admin,org,id).get("configuration_version")).isEqualTo(2L);
  assertThatThrownBy(()->registry.invoke(admin,org,id,cap,UUID.randomUUID(),Map.of("value","no"))).isInstanceOf(McpFailure.class);
  activate(id);UUID current=capability(id);peer.changed=true;
  assertThatThrownBy(()->registry.invoke(admin,org,id,current,UUID.randomUUID(),Map.of("value","no"))).isInstanceOf(McpFailure.class);
  assertThat(peer.calls.get()).isZero();
  assertThat(db.queryForObject("SELECT count(*) FROM app.mcp_approval WHERE organization_id=? AND status='APPROVED'",Integer.class,org)).isZero();
 }
 @Test void staleValidationCannotActivateEditedRevision()throws Exception{
  UUID id=register();peer.waiting=new CountDownLatch(1);peer.release=new CountDownLatch(1);
  try(var executor=Executors.newSingleThreadExecutor()){
   Future<?> old=executor.submit(()->registry.validate(admin,org,id));assertThat(peer.waiting.await(5,TimeUnit.SECONDS)).isTrue();
   registry.edit(admin,org,id,input("Revision two"));peer.release.countDown();assertThatThrownBy(()->old.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(McpFailure.class);
  }
  assertThat(registry.detail(admin,org,id).get("state")).isEqualTo("DRAFT");assertThat(registry.detail(admin,org,id).get("configuration_version")).isEqualTo(2L);
 }
 @Test void duplicatesInvalidDestinationsAndBadArgumentsNeverCall(){
  registry.register(admin,org,input("Duplicate"));assertThatThrownBy(()->registry.register(admin,org,input("duplicate"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->registry.register(admin,org,new McpRegistry.Registration("Bad","http://169.254.169.254/latest","AUTO_DUAL","sentinel-valid-credential"))).isInstanceOf(McpFailure.class);
  UUID id=register();activate(id);
  assertThatThrownBy(()->registry.invoke(admin,org,id,capability(id),UUID.randomUUID(),Map.of("value",4))).isInstanceOf(McpFailure.class);assertThat(peer.calls.get()).isZero();
 }
 @Test void credentialTamperingAndWrongTenantCannotDecrypt(){
  UUID id=secrets.store(org,"sentinel-credential-1234567890");assertThat(secrets.resolve(org,id)).isEqualTo("sentinel-credential-1234567890");
  assertThatThrownBy(()->secrets.resolve(other,id)).isInstanceOf(IllegalStateException.class);
  db.update("UPDATE app.mcp_credential SET ciphertext=decode(repeat('00',48),'hex') WHERE organization_id=? AND id=?",org,id);
  assertThatThrownBy(()->secrets.resolve(org,id)).isInstanceOf(IllegalStateException.class);
 }
 @Test void keyRotationRetainsOldDecryptKeysAndUsesNewActiveKey()throws Exception{
  UUID old=secrets.store(org,"old-sentinel-credential-123456789");
  var oldTree=json.readTree(Files.readString(keyring));String first=oldTree.path("keys").path("v1").asString();
  byte[] second=new byte[32];new java.security.SecureRandom().nextBytes(second);
  Files.writeString(keyring,json.writeValueAsString(Map.of("activeKeyId","v2","keys",Map.of("v1",first,"v2",Base64.getEncoder().encodeToString(second)))));
  var rotated=new McpCredentials(db,json,keyring.toString());assertThat(rotated.resolve(org,old)).isEqualTo("old-sentinel-credential-123456789");
  UUID fresh=rotated.store(org,"new-sentinel-credential-123456789");assertThat(db.queryForObject("SELECT key_id FROM app.mcp_credential WHERE organization_id=? AND id=?",String.class,org,fresh)).isEqualTo("v2");
 }
 @Test void sameRemoteNamesDoNotShareCapabilitiesOrApprovals(){
  UUID first=register(),second=register();activate(first);activate(second);UUID firstCap=capability(first),secondCap=capability(second);
  assertThat(firstCap).isNotEqualTo(secondCap);
  assertThatThrownBy(()->registry.invoke(admin,org,second,firstCap,UUID.randomUUID(),Map.of("value","no"))).isInstanceOf(AccessDeniedException.class);
  assertThat(peer.calls.get()).isZero();
 }
 static class Peer implements NativeMcpClient {
  AtomicInteger calls=new AtomicInteger(),closed=new AtomicInteger();boolean changed;CountDownLatch waiting,release;Connect input;
  Tool tool(){return new Tool("same_remote_name","Untrusted description",Map.of("type","object","properties",Map.of("value",Map.of("type","string","maxLength",40)),"required",List.of("value"),"additionalProperties",false),null,null,null,(changed?"b":"a").repeat(64),true,null);}
  Snapshot snapshot(){return new Snapshot(input.organizationId()+"/"+input.integrationId()+"/"+input.configurationVersion(),UUID.randomUUID().toString(),"2026-07-28","modern",Map.of("name","Controlled registry peer","version","1"),Map.of("tools",Map.of()),List.of(tool()));}
  public Snapshot connect(Connect c){input=c;if(waiting!=null){waiting.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}return snapshot();}
  public Snapshot discover(Handle h){return snapshot();}
  public Outcome invoke(Handle h,String n,String fp,Map<String,Object>a,UUID id){if(!fp.equals(tool().fingerprint()))throw new McpFailure("MCP_SCHEMA_CHANGED");calls.incrementAndGet();return new Outcome("2026-07-28",fp,Map.of("structuredContent",a));}
  public boolean cancel(Handle h,UUID id){return true;}public void close(Handle h,boolean revoke){closed.incrementAndGet();}
 }
}
