package io.ecosync.execution;
import java.time.Duration;
import java.util.concurrent.Callable;
/** Scoped monotonic caller deadline. Absent scope preserves established manual-invocation behavior. */
public final class CallDeadline {
 private static final ThreadLocal<Long> END=new ThreadLocal<>();
 private CallDeadline(){}
 public static final class Expired extends RuntimeException {}
 public static <T>T within(Duration remaining,Callable<T> action)throws Exception{Long previous=END.get();long end=System.nanoTime()+remaining.toNanos();END.set(previous==null?end:Math.min(previous,end));try{limit(remaining);return action.call();}finally{if(previous==null)END.remove();else END.set(previous);}}
 public static Duration limit(Duration maximum){Long end=END.get();if(end==null)return maximum;long remaining=end-System.nanoTime();if(remaining<=0||Thread.currentThread().isInterrupted())throw new Expired();return Duration.ofNanos(Math.min(remaining,maximum.toNanos()));}
 public static void check(){limit(Duration.ofSeconds(30));}
}
