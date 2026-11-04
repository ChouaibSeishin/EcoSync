package io.ecosync.gateway;
import com.zaxxer.hikari.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded pools contain connection material only in this data plane; no durable credentials. */
public final class Connections implements AutoCloseable {
 private record Pool(String scope,long revision,String credential,String epoch,HikariDataSource source,AtomicLong touched){}
 public static final class Operation implements AutoCloseable {
  public final UUID id;public final String scope;public final long generation;private final Connections owner;private final Semaphore local;
  volatile Statement statement;volatile Connection connection;volatile boolean cancelled;volatile Future<?> deadline;
  private Operation(UUID id,String scope,long generation,Connections owner,Semaphore local){this.id=id;this.scope=scope;this.generation=generation;this.owner=owner;this.local=local;}
  public void check(){if(cancelled||owner.generation(scope)!=generation||Thread.currentThread().isInterrupted())throw new GatewayFailure("DB_CANCELLED");}
  @Override public void close(){if(deadline!=null)deadline.cancel(false);owner.operations.remove(id,this);local.release();owner.global.release();}
 }
 private final DestinationPolicy policy;private final Map<String,Pool> pools=new HashMap<>();private final Map<String,AtomicLong> generations=new ConcurrentHashMap<>();
 private final Map<String,Semaphore> limits=new ConcurrentHashMap<>();private final Map<UUID,Operation> operations=new ConcurrentHashMap<>();private final Semaphore global=new Semaphore(8);
 private final ScheduledThreadPoolExecutor cleanup=new ScheduledThreadPoolExecutor(1);
 private final ExecutorService aborts=Executors.newVirtualThreadPerTaskExecutor();
 public final AtomicLong sqlAdmissions=new AtomicLong();
 public Connections(DestinationPolicy policy){this.policy=policy;PinnedSockets.install(policy);cleanup.setRemoveOnCancelPolicy(true);cleanup.scheduleWithFixedDelay(this::idle,15,15,TimeUnit.SECONDS);}
 public long generation(String scope){return generations.computeIfAbsent(scope,k->new AtomicLong()).get();}
 public Operation begin(UUID id,String scope){if(generations.size()>=128&&!generations.containsKey(scope))throw new GatewayFailure("DB_LIMIT");if(!global.tryAcquire())throw new GatewayFailure("DB_BUSY");var local=limits.computeIfAbsent(scope,k->new Semaphore(2));if(!local.tryAcquire()){global.release();throw new GatewayFailure("DB_BUSY");}var op=new Operation(id,scope,generation(scope),this,local);if(operations.putIfAbsent(id,op)!=null){local.release();global.release();throw new GatewayFailure("DB_ACCESS_DENIED");}op.deadline=cleanup.schedule(()->cancel(id),12,TimeUnit.SECONDS);return op;}
 public Connection acquire(Map<String,Object> config,Operation operation)throws SQLException{
  operation.check();String scope=config.get("organizationId")+"/"+config.get("connectorId"),key=scope+"/"+config.get("revision")+"/"+config.get("authorizationEpoch");try{policy.check(config);}catch(GatewayFailure e){retire(scope);throw e;}Pool pool;
  synchronized(pools){pool=pools.get(key);if(pool==null){if(pools.size()>=16)throw new GatewayFailure("DB_LIMIT");var hikari=settings(config);
    operation.check();pool=new Pool(scope,((Number)config.get("revision")).longValue(),config.get("credentialReference").toString(),config.get("authorizationEpoch").toString(),new HikariDataSource(hikari),new AtomicLong(System.nanoTime()));pools.put(key,pool);
   }pool.touched.set(System.nanoTime());}
  try{operation.check();var c=pool.source.getConnection();operation.connection=c;operation.check();c.setReadOnly(true);return c;}catch(GatewayFailure|SQLException e){retire(scope);throw e;}
 }
 public static HikariConfig settings(Map<String,Object> config){
  var hikari=new HikariConfig();boolean mysql=config.get("databaseType").equals("MYSQL"),tls=Boolean.TRUE.equals(config.get("tls"));String host=(String)config.get("host");int port=((Number)config.get("port")).intValue();
    hikari.setJdbcUrl((mysql?"jdbc:mysql://":"jdbc:postgresql://")+host+":"+port+"/"+config.get("databaseName"));hikari.setUsername((String)config.get("username"));hikari.setPassword((String)config.get("password"));hikari.setDriverClassName(mysql?"com.mysql.cj.jdbc.Driver":"org.postgresql.Driver");hikari.setMaximumPoolSize(2);hikari.setMinimumIdle(0);hikari.setConnectionTimeout(4000);hikari.setValidationTimeout(1000);hikari.setIdleTimeout(60000);hikari.setMaxLifetime(120000);hikari.setInitializationFailTimeout(-1);hikari.setReadOnly(true);hikari.setAutoCommit(false);hikari.setRegisterMbeans(false);
    if(mysql){hikari.addDataSourceProperty("sslMode",tls?"VERIFY_IDENTITY":"DISABLED");hikari.addDataSourceProperty("allowPublicKeyRetrieval",!tls);hikari.addDataSourceProperty("allowMultiQueries",false);hikari.addDataSourceProperty("allowLoadLocalInfile",false);hikari.addDataSourceProperty("allowUrlInLocalInfile",false);hikari.addDataSourceProperty("allowLoadLocalInfileInPath","");hikari.addDataSourceProperty("autoDeserialize",false);hikari.addDataSourceProperty("paranoid",true);hikari.addDataSourceProperty("connectTimeout",3000);hikari.addDataSourceProperty("socketTimeout",5000);hikari.addDataSourceProperty("maxAllowedPacket",1048576);hikari.addDataSourceProperty("connectionTimeZone","UTC");hikari.addDataSourceProperty("forceConnectionTimeZoneToSession",true);hikari.addDataSourceProperty("useServerPrepStmts",true);hikari.addDataSourceProperty("useCursorFetch",true);hikari.addDataSourceProperty("socketFactory",MySqlPinnedSocketFactory.class.getName());
    }else{hikari.addDataSourceProperty("sslmode",tls?"verify-full":"disable");hikari.addDataSourceProperty("sslfactory","org.postgresql.ssl.DefaultJavaSSLFactory");hikari.addDataSourceProperty("connectTimeout",3);hikari.addDataSourceProperty("socketTimeout",5);hikari.addDataSourceProperty("cancelSignalTimeout",2);hikari.addDataSourceProperty("maxResultBuffer",1048576);hikari.addDataSourceProperty("readOnlyMode","always");hikari.addDataSourceProperty("options","-c statement_timeout=4000 -c lock_timeout=1000 -c default_transaction_read_only=on -c timezone=UTC");hikari.addDataSourceProperty("socketFactory",PostgresPinnedSocketFactory.class.getName());hikari.addDataSourceProperty("socketFactoryArg",host+":"+port);}

  // JDBC drivers consume Properties.getProperty; Hikari 7 preserves object values verbatim.
  hikari.getDataSourceProperties().replaceAll((key,value)->value.toString());
  return hikari;
 }
 public void retire(String scope){var generation=generations.get(scope);if(generation!=null)generation.incrementAndGet();for(var operation:operations.values())if(operation.scope.equals(scope))cancel(operation.id);synchronized(pools){var iterator=pools.values().iterator();while(iterator.hasNext()){var p=iterator.next();if(p.scope.equals(scope)){iterator.remove();aborts.submit(p.source::close);}}}}
 public void cancel(UUID id){var op=operations.get(id);if(op==null)return;synchronized(op){if(op.cancelled)return;op.cancelled=true;}aborts.submit(()->{try{if(op.statement!=null)op.statement.cancel();}catch(Exception ignored){}try{if(op.connection!=null)op.connection.abort(aborts);}catch(Exception ignored){}});}
 public void retain(Map<String,String> registrations){List<String> stale;synchronized(pools){stale=pools.values().stream().filter(p->!Objects.equals(registrations.get(p.scope),p.revision+"/"+p.epoch)).map(Pool::scope).distinct().toList();}stale.forEach(this::retire);}
 private void idle(){try{synchronized(pools){var it=pools.values().iterator();while(it.hasNext()){var p=it.next();if(System.nanoTime()-p.touched.get()>Duration.ofMinutes(1).toNanos()&&operations.values().stream().noneMatch(o->o.scope.equals(p.scope))){it.remove();aborts.submit(p.source::close);}}generations.forEach((scope,g)->{if(pools.values().stream().noneMatch(p->p.scope.equals(scope))&&operations.values().stream().noneMatch(o->o.scope.equals(scope))&&generations.remove(scope,g))limits.remove(scope);});}}catch(Exception ignored){}}
 public Map<String,Object> counts(){synchronized(pools){return Map.of("pools",pools.size(),"running",operations.size(),"sourceSqlAdmissions",sqlAdmissions.get());}}
 @Override public void close(){cleanup.shutdownNow();operations.keySet().forEach(this::cancel);synchronized(pools){pools.values().forEach(p->aborts.submit(p.source::close));pools.clear();}aborts.shutdown();try{if(!aborts.awaitTermination(2,TimeUnit.SECONDS))aborts.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();}policy.close();}
}
