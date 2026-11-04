package io.ecosync.gateway;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ConnectionsTest {
 @Test void perConnectorAndGlobalConcurrencyAreFailFastAndCleanupReleasesSlots(){try(var c=new Connections(new DestinationPolicy("","",false))){var first=c.begin(UUID.randomUUID(),"a");var second=c.begin(UUID.randomUUID(),"a");assertThatThrownBy(()->c.begin(UUID.randomUUID(),"a")).isInstanceOf(GatewayFailure.class);var others=new ArrayList<Connections.Operation>();for(int i=0;i<6;i++)others.add(c.begin(UUID.randomUUID(),"x"+i));assertThatThrownBy(()->c.begin(UUID.randomUUID(),"another")).isInstanceOf(GatewayFailure.class);first.close();second.close();others.forEach(Connections.Operation::close);assertThat(c.counts().get("running")).isEqualTo(0);try(var fresh=c.begin(UUID.randomUUID(),"a")){fresh.check();}}}
 @Test void retireAndCancellationFenceLateOperations(){try(var c=new Connections(new DestinationPolicy("","",false));var op=c.begin(UUID.randomUUID(),"a")){c.retire("a");assertThatThrownBy(op::check).isInstanceOf(GatewayFailure.class);try(var next=c.begin(UUID.randomUUID(),"a")){next.check();c.cancel(next.id);assertThatThrownBy(next::check).isInstanceOf(GatewayFailure.class);}}}
 @Test void streamingBodyLimitCancelsBeforeOversizedBufferIsCopied(){var body=new BoundedBody(8);var cancelled=new java.util.concurrent.atomic.AtomicBoolean();body.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long n){}public void cancel(){cancelled.set(true);}});body.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[9])));assertThat(cancelled).isTrue();assertThatThrownBy(()->body.getBody().toCompletableFuture().join()).hasCauseInstanceOf(GatewayFailure.class);}
}
