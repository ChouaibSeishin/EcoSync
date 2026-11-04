package io.ecosync.gateway;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
/** Real independent engines/drivers. The live demo journey separately tests pools and network pins. */
class DialectIT {
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
 static PostgreSQLContainer pg=new PostgreSQLContainer(DockerImageName.parse("postgres:17.11-bookworm@sha256:2cd2c0f809604aaeff6f0ec8f40aecbd025613107561015c69369a1a7f1d3416").asCompatibleSubstituteFor("postgres"));
 static MySQLContainer mysql=new MySQLContainer(DockerImageName.parse("mysql:8.4.13@sha256:5cf1e1293ea42e077e6a22114e6773b9aae3bb8f9933a2acd5a9721518ff8f5b").asCompatibleSubstituteFor("mysql")).withDatabaseName("inventory").withUrlParam("connectTimeout","3000").withUrlParam("socketTimeout","5000").withStartupTimeout(java.time.Duration.ofMinutes(3));
 static String password=UUID.randomUUID().toString();
 @BeforeAll static void start()throws Exception{pg.start();mysql.start();for(boolean my:List.of(false,true)){try(var c=owner(my);var s=c.createStatement()){if(!my)s.execute("CREATE SCHEMA sales");String name=my?"inventory":"sales";s.execute("CREATE TABLE "+name+".items(id INTEGER PRIMARY KEY,label VARCHAR(128),amount DECIMAL(12,2),active BOOLEAN,created_at TIMESTAMP,secret VARCHAR(100))");s.execute("INSERT INTO "+name+".items VALUES(1,'alpha',12.34,true,'2026-10-01 00:00:00','hidden'),(2,'beta',0.01,false,'2026-10-02 00:00:00','hidden')");if(my){s.execute("CREATE USER 'connector_reader'@'%' IDENTIFIED BY '"+password+"'");s.execute("GRANT SELECT(id,label,amount,active,created_at) ON inventory.items TO 'connector_reader'@'%'");}else{s.execute("CREATE ROLE connector_reader LOGIN PASSWORD '"+password+"'");s.execute("GRANT USAGE ON SCHEMA sales TO connector_reader");s.execute("GRANT SELECT(id,label,amount,active,created_at) ON sales.items TO connector_reader");}}}}
 static Connection owner(boolean my)throws Exception{return DriverManager.getConnection(my?mysql.getJdbcUrl():pg.getJdbcUrl(),my?"root":pg.getUsername(),my?mysql.getPassword():pg.getPassword());}
 static Connection reader(boolean my)throws Exception{var config=Map.<String,Object>of("databaseType",my?"MYSQL":"POSTGRESQL","host",my?mysql.getHost():pg.getHost(),"port",my?mysql.getMappedPort(3306):pg.getMappedPort(5432),"databaseName",my?mysql.getDatabaseName():pg.getDatabaseName(),"tls",false,"username","connector_reader","password",password);var properties=Connections.settings(config).getDataSourceProperties();properties.remove("socketFactory");properties.remove("socketFactoryArg");properties.setProperty("user","connector_reader");properties.setProperty("password",password);var c=DriverManager.getConnection(my?mysql.getJdbcUrl():pg.getJdbcUrl(),properties);c.setReadOnly(true);c.setAutoCommit(false);return c;}
 @AfterAll static void stop(){mysql.stop();pg.stop();}
 void dialect(boolean my)throws Exception{try(var c=reader(my)){String schema=my?"inventory":"sales";var config=Map.<String,Object>of("databaseType",my?"MYSQL":"POSTGRESQL","databaseName",my?"inventory":pg.getDatabaseName(),"schemas",List.of(schema));var tables=new MetadataReader().inspect(c,config);assertThat(tables).hasSize(1);var table=tables.getFirst();assertThat(table.get("primaryKey")).isEqualTo(List.of("id"));var plan=new ReadQuery().build(my,table,List.of("id","label","amount"),Map.of("columns",List.of("id","label","amount"),"filters",List.of(Map.of("column","label","operator","EQ","value","' OR 1=1 --"))));try(var s=c.prepareStatement(plan.sql())){int i=1;for(var v:plan.parameters())s.setObject(i++,v);try(var r=s.executeQuery()){assertThat(r.next()).isFalse();}}var exact=new ReadQuery().build(my,table,List.of("id","amount"),Map.of("columns",List.of("id","amount"),"limit",1));try(var s=c.prepareStatement(exact.sql())){int i=1;for(var v:exact.parameters())s.setObject(i++,v);try(var r=s.executeQuery()){assertThat(r.next()).isTrue();assertThat(r.getBigDecimal("amount").toPlainString()).isEqualTo("12.34");assertThat(r.next()).isTrue();assertThat(r.next()).isFalse();}}}}
 @Test void postgresqlMetadataPreparedReadsAndLimitProbe()throws Exception{dialect(false);}
 @Test void mysqlMetadataPreparedReadsAndLimitProbe()throws Exception{dialect(true);}
 void grants(boolean my)throws Exception{String table=(my?"inventory":"sales")+".items";for(String sql:List.of("UPDATE "+table+" SET label='bad' WHERE id=1","DELETE FROM "+table+" WHERE id=-1","CREATE TABLE "+(my?"inventory":"sales")+".forbidden(id int)","SELECT secret FROM "+table,"SELECT * FROM "+(my?"mysql.user":"pg_catalog.pg_authid"))){try(var c=reader(my);var s=c.createStatement()){c.setAutoCommit(true);c.setReadOnly(false);assertThatThrownBy(()->s.execute(sql)).isInstanceOf(SQLException.class);}}}
 @Test void postgresqlRealReaderDeniesWritesDdlUnapprovedColumnAndAdminCatalog()throws Exception{grants(false);}
 @Test void mysqlRealReaderDeniesWritesDdlUnapprovedColumnAndAdminCatalog()throws Exception{grants(true);}
 void bounded(boolean my)throws Exception{String table=(my?"inventory":"sales")+".bounds";try(var owner=owner(my);var statement=owner.createStatement()){
  statement.execute("CREATE TABLE "+table+"(id INTEGER PRIMARY KEY,a VARCHAR(4000),b VARCHAR(4000),c VARCHAR(4000))");try{
   try(var insert=owner.prepareStatement("INSERT INTO "+table+" VALUES(?,?,?,?)")){for(int i=0;i<100;i++){insert.setInt(1,i);for(int j=2;j<=4;j++)insert.setString(j,"x".repeat(3000));insert.addBatch();}insert.executeBatch();}
   statement.execute(my?"GRANT SELECT ON "+table+" TO 'connector_reader'@'%'":"GRANT SELECT ON "+table+" TO connector_reader");
   try(var connection=reader(my);var store=new Connections(new DestinationPolicy("","",false));var op=store.begin(UUID.randomUUID(),"bounds")){
    var reader=new ReadResults(new tools.jackson.databind.json.JsonMapper(),store);var result=reader.read(connection,new ReadQuery.Plan("SELECT id,a,b,c FROM "+table+" ORDER BY id LIMIT ?",List.of(101),List.of("id","a","b","c"),100,0),op);
    assertThat(result.get("truncated")).isEqualTo(true);assertThat(result.get("cellTruncated")).isEqualTo(true);assertThat(result.get("partialFailure")).isEqualTo("RESULT_BYTES_LIMIT");assertThat(((List<?>)result.get("rows")).size()).isBetween(1,99);assertThat(new tools.jackson.databind.json.JsonMapper().writeValueAsBytes(result).length).isLessThan(270336);
   }
   try(var connection=reader(my);var store=new Connections(new DestinationPolicy("","",false));var op=store.begin(UUID.randomUUID(),"rows")){
    var result=new ReadResults(new tools.jackson.databind.json.JsonMapper(),store).read(connection,new ReadQuery.Plan("SELECT id FROM "+table+" ORDER BY id LIMIT ?",List.of(3),List.of("id"),2,0),op);assertThat((List<?>)result.get("rows")).hasSize(2);assertThat(result.get("truncated")).isEqualTo(true);assertThat(result.get("nextOffset")).isEqualTo(2);
   }
  }finally{statement.execute("DROP TABLE "+table);}
 }}
 @Test void postgresqlBoundedRowsCellsBytesAndExplicitPartialResults()throws Exception{bounded(false);}
 @Test void mysqlBoundedRowsCellsBytesAndExplicitPartialResults()throws Exception{bounded(true);}
 void schemaChange(boolean my)throws Exception{String schema=my?"inventory":"sales";var config=Map.<String,Object>of("databaseType",my?"MYSQL":"POSTGRESQL","databaseName",my?"inventory":pg.getDatabaseName(),"schemas",List.of(schema));var keys=java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var file=temp.resolve("public.der");java.nio.file.Files.write(file,keys.getPublic().getEncoded());var fingerprint=new Delegation(new tools.jackson.databind.json.JsonMapper(),file.toString());String before;try(var c=reader(my)){before=fingerprint.hash(new MetadataReader().table(c,config,schema,"items"));}try(var owner=owner(my);var s=owner.createStatement()){s.execute(my?"ALTER TABLE inventory.items MODIFY COLUMN label VARCHAR(129)":"ALTER TABLE sales.items ALTER COLUMN label TYPE VARCHAR(129)");try{try(var c=reader(my)){var changed=new MetadataReader().table(c,config,schema,"items");assertThat(fingerprint.hash(changed)).isNotEqualTo(before);assertThat(changed.get("primaryKey")).isEqualTo(List.of("id"));}}finally{s.execute(my?"ALTER TABLE inventory.items MODIFY COLUMN label VARCHAR(128)":"ALTER TABLE sales.items ALTER COLUMN label TYPE VARCHAR(128)");}}}
 @Test void postgresqlActualSchemaTypeChangeChangesTheReviewedFingerprint()throws Exception{schemaChange(false);}
 @Test void mysqlActualSchemaTypeChangeChangesTheReviewedFingerprint()throws Exception{schemaChange(true);}
 void deadline(boolean my)throws Exception{try(var connection=reader(my);var store=new Connections(new DestinationPolicy("","",false));var op=store.begin(UUID.randomUUID(),"deadline")){
  long start=System.nanoTime();var plan=new ReadQuery.Plan(my?"SELECT SLEEP(6) AS delayed":"SELECT pg_sleep(6) AS delayed",List.of(),List.of("delayed"),1,0);
  // Fault SQL is constructed only by this disposable-engine test; the production builder rejects functions/raw SQL.
  assertThatThrownBy(()->new ReadResults(new tools.jackson.databind.json.JsonMapper(),store).read(connection,plan,op)).isInstanceOf(SQLException.class);assertThat(java.time.Duration.ofNanos(System.nanoTime()-start)).isLessThan(java.time.Duration.ofSeconds(8));connection.rollback();try(var s=connection.createStatement();var r=s.executeQuery("SELECT 1")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(1);}
 }}
 @Test void postgresqlActualQueryDeadlineCancelsAndConnectionRecovers()throws Exception{deadline(false);}
 @Test void mysqlActualQueryDeadlineCancelsAndConnectionRecovers()throws Exception{deadline(true);}
}
