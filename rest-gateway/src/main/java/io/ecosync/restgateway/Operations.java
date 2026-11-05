package io.ecosync.restgateway;
import io.ecosync.restcontract.RestFailure;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
/** No retained source connection pool. Bounded admission and cancellable per-execution sockets. */
public final class Operations implements AutoCloseable {
 private final Semaphore global=new Semaphore(8);private final ConcurrentHashMap<UUID,Operation> active=new ConcurrentHashMap<>();
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"rest-deadlines");t.setDaemon(true);return t;});
 public final class Operation implements AutoCloseable {
  final UUID id;final String connector;final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);private volatile Socket socket;private volatile boolean cancelled;private ScheduledFuture<?> expiry;
  Operation(UUID id,String connector){this.id=id;this.connector=connector;}
  public void check(){if(System.nanoTime()>deadline)throw new RestFailure("REST_TIMEOUT");if(cancelled||Thread.currentThread().isInterrupted())throw new RestFailure("REST_CANCELLED");}
  public synchronized void socket(Socket value){check();socket=value;}
  public synchronized void cancel(){cancelled=true;if(socket!=null)try{socket.close();}catch(Exception ignored){}}
  public void close(){cancel();if(expiry!=null)expiry.cancel(false);if(active.remove(id,this))global.release();}
 }
 public synchronized Operation begin(UUID id,String connector){if(active.containsKey(id)||active.values().stream().filter(o->o.connector.equals(connector)).count()>=2||!global.tryAcquire())throw new RestFailure("REST_BUSY");var op=new Operation(id,connector);active.put(id,op);op.expiry=timer.schedule(op::cancel,10,TimeUnit.SECONDS);return op;}
 public void cancel(UUID id){var op=active.get(id);if(op!=null)op.cancel();}
 public void retire(String connector){active.values().stream().filter(o->o.connector.equals(connector)).forEach(Operation::cancel);}
 public int active(){return active.size();}
 public void close(){active.values().forEach(Operation::cancel);timer.shutdownNow();}
}
