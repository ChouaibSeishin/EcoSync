package io.ecosync.gateway;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;
public final class Authority implements AutoCloseable {
 private final String base,token;private final JsonMapper json;private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 public Authority(String base,String token,JsonMapper json){if(!base.matches("https?://[a-zA-Z0-9.-]+:[0-9]+"))throw new IllegalStateException("Authority target invalid");this.base=base;this.token=token;this.json=json;}
 public Map<String,Object> request(String path,String assertion,Object data){
  var builder=HttpRequest.newBuilder(URI.create(base+"/internal/database-gateway"+path)).timeout(Duration.ofSeconds(3)).header("Authorization","Bearer "+token);if(assertion!=null)builder.header("X-Ecosync-Delegation",assertion);if(data==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(data)));
  var future=client.sendAsync(builder.build(),r->new BoundedBody(65536));try{var response=future.get(3,TimeUnit.SECONDS);var result=json.readValue(response.body(),Map.class);if(response.statusCode()!=200)throw new GatewayFailure(Objects.toString(result.get("code"),"DB_ACCESS_DENIED"));return result;}catch(GatewayFailure e){throw e;}catch(Exception e){future.cancel(true);if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new GatewayFailure("DB_UNAVAILABLE");}
 }
 public Map<String,Object> authorize(String assertion,String mode,UUID table){return request("/authorize",assertion,table==null?Map.of("mode",mode):Map.of("mode",mode,"tableId",table));}
 @Override public void close(){client.shutdownNow();}
}
