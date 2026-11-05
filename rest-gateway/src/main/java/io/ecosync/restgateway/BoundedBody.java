package io.ecosync.restgateway;
import io.ecosync.restcontract.*;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
public final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
 private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final ByteArrayOutputStream output=new ByteArrayOutputStream();private final int maximum;private Flow.Subscription subscription;private int size;
 public BoundedBody(int maximum){this.maximum=maximum;}
 public CompletionStage<byte[]> getBody(){return result;}
 public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
 public void onNext(List<ByteBuffer> buffers){for(var b:buffers){int count=b.remaining();if(count>maximum-size){subscription.cancel();result.completeExceptionally(new RestFailure("REST_LIMIT"));return;}size+=count;byte[] bytes=new byte[count];b.get(bytes);output.writeBytes(bytes);}subscription.request(1);}
 public void onError(Throwable error){result.completeExceptionally(error);}public void onComplete(){result.complete(output.toByteArray());}
}
