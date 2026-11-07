package io.ecosync.chat;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
/** Monotonic whole-turn deadline. Neither model nor caller can increase it. */
public final class QueryBudget {
 private final long end;private final int maximum;private volatile int calls;private final AtomicBoolean cancelled=new AtomicBoolean();
 public QueryBudget(Duration duration,int maximum){if(duration.isNegative()||duration.isZero()||duration.compareTo(Duration.ofSeconds(30))>0||maximum<1||maximum>8)throw new IllegalArgumentException();end=System.nanoTime()+duration.toNanos();this.maximum=maximum;}
 public void cancel(){cancelled.set(true);}
 public Duration remaining(){if(cancelled.get()||Thread.currentThread().isInterrupted())throw new ChatFailure("CHAT_CANCELLED");long n=end-System.nanoTime();if(n<=0)throw new ChatFailure("CHAT_TIMEOUT");return Duration.ofNanos(n);}
 public int consume(){remaining();if(calls>=maximum)throw new ChatFailure("CHAT_CALL_BUDGET");return ++calls;}
 public int calls(){return calls;}
}
