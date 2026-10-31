package io.ecosync.mcp;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name="ecosync.mcp.enabled",havingValue="true")
public class HttpNativeMcpClient implements NativeMcpClient {
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final URI base;
 private final String credential;
 private final JsonMapper json;
 public HttpNativeMcpClient(JsonMapper json,@Value("${ecosync.mcp.client-url:http://mcp-client:8090}")String url,
   @Value("${ecosync.mcp.internal-token:}")String credential,@Value("${ecosync.mcp.environment:production}")String environment){
  this.json=json;this.base=URI.create(url);
  if(base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||base.getHost()==null
    ||!base.getPath().isEmpty()&&!base.getPath().equals("/")||!base.getScheme().equals("https")&&!(environment.equals("development")&&base.getScheme().equals("http"))
    ||!credential.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalArgumentException("Invalid internal MCP client service configuration");
  this.credential=credential;
 }
 @Override public Snapshot connect(Connect c){return post("/v1/connect",c,Snapshot.class);}
 @Override public Snapshot discover(Handle h){return post("/v1/discover",h,Snapshot.class);}
 @Override public Outcome invoke(Handle h,String name,String fingerprint,Map<String,Object> arguments,UUID operationId){
  return post("/v1/invoke",Map.of("key",h.key(),"leaseId",h.leaseId(),"name",name,"expectedFingerprint",fingerprint,"arguments",arguments,"operationId",operationId),Outcome.class);
 }
 @Override public boolean cancel(Handle h,UUID operationId){
  var result=post("/v1/cancel",Map.of("key",h.key(),"leaseId",h.leaseId(),"operationId",operationId),Map.class);
  return Boolean.TRUE.equals(result.get("cancelled"));
 }
 @Override public void close(Handle h,boolean revoke){post("/v1/close",Map.of("key",h.key(),"leaseId",h.leaseId(),"revoke",revoke),Map.class);}
 @jakarta.annotation.PreDestroy public void shutdown(){http.shutdownNow();}
 private <T>T post(String path,Object body,Class<T> type){
  byte[] data=json.writeValueAsBytes(body);if(data.length>65536)throw new McpFailure("MCP_ARGUMENTS_TOO_LARGE");
  var request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+credential)
    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(data)).build();
  var future=http.sendAsync(request,info->new BoundedBody(2097152));
  try{
   var response=future.get(30,TimeUnit.SECONDS);
   if(response.statusCode()!=200){
    String code="MCP_UNAVAILABLE";
    try{var node=json.readTree(response.body()).path("error").path("code");if(node.isString())code=node.asString();}catch(Exception ignored){}
    throw new McpFailure(code);
   }
   return json.readValue(response.body(),type);
  }catch(InterruptedException error){future.cancel(true);Thread.currentThread().interrupt();throw new McpFailure("MCP_CANCELLED");}
  catch(TimeoutException error){future.cancel(true);throw new McpFailure("MCP_TIMEOUT");}
  catch(ExecutionException error){future.cancel(true);if(error.getCause() instanceof McpFailure failure)throw failure;throw new McpFailure("MCP_UNAVAILABLE");}
  catch(McpFailure error){throw error;}
  catch(Exception error){future.cancel(true);throw new McpFailure("MCP_INVALID_RESPONSE");}
 }
 private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final CompletableFuture<byte[]> result=new CompletableFuture<>();
  private final ByteArrayOutputStream output=new ByteArrayOutputStream();
  private final int maximum;private Flow.Subscription subscription;private int size;
  BoundedBody(int maximum){this.maximum=maximum;}
  @Override public CompletionStage<byte[]> getBody(){return result;}
  @Override public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
  @Override public void onNext(List<ByteBuffer> buffers){
   for(var buffer:buffers){int count=buffer.remaining();if(count>maximum-size){subscription.cancel();result.completeExceptionally(new McpFailure("MCP_RESPONSE_TOO_LARGE"));return;}
    size+=count;byte[] bytes=new byte[count];buffer.get(bytes);output.writeBytes(bytes);
   }subscription.request(1);
  }
  @Override public void onError(Throwable error){result.completeExceptionally(error);}
  @Override public void onComplete(){result.complete(output.toByteArray());}
 }
}
