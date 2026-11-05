package io.ecosync.rest;
import io.ecosync.restcontract.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Private official-SDK wire path. Each operation has isolated short-lived identity/session. */
@Service @ConditionalOnProperty(name="ecosync.rest.enabled",havingValue="true")
public class HttpRestGateway implements RestGateway {
 private final String base,token;private final JsonMapper json;private final HttpClient http;private final Semaphore calls=new Semaphore(8);
 public HttpRestGateway(JsonMapper json,@Value("${ecosync.rest.gateway-url:}")String base,@Value("${ecosync.rest.service-token:}")String token){
  this.json=json;this.base=base;this.token=token;if(!token.matches("[A-Za-z0-9_-]{32,128}")||!base.matches("https?://[a-zA-Z0-9.-]+:[0-9]+"))throw new IllegalStateException("Rest gateway configuration invalid");http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(3)).build();
 }
 private Map<String,Object> post(String path,String assertion,Object body){
  if(!calls.tryAcquire())throw new RestFailure("REST_BUSY");
  try{var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json");if(assertion!=null)b.header("X-Ecosync-Delegation",assertion);
   var future=http.sendAsync(b.POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build(),info->new RestBody(524288));
   try{var r=future.get(15,java.util.concurrent.TimeUnit.SECONDS);var value=json.readValue(r.body(),Map.class);if(r.statusCode()!=200)throw new RestFailure(Objects.toString(value.get("code"),"REST_UNAVAILABLE"));return value;}catch(Exception e){future.cancel(true);if(e instanceof RestFailure d)throw d;if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new RestFailure("REST_UNAVAILABLE");}
  }catch(RestFailure e){throw e;}catch(Exception e){throw new RestFailure("REST_UNAVAILABLE");}finally{calls.release();}
 }
 @Override public Map<String,Object> inspect(String assertion,Map<String,Object> arguments){return post("/internal/test",assertion,arguments);}
 private <T>T wire(String assertion,java.util.function.Function<io.modelcontextprotocol.client.McpSyncClient,T> operation){
  if(!calls.tryAcquire())throw new RestFailure("REST_BUSY");
  var transport=HttpClientStreamableHttpTransport.builder(base).endpoint("/mcp").maxResponseSize(524288).openConnectionOnStartup(false).resumableStreams(false).supportedProtocolVersions(List.of("2025-11-25")).customizeClient(b->b.connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER)).addHttpRequestCustomizer((b,m,u,data,c)->b.header("Authorization","Bearer "+token).header("X-Ecosync-Delegation",assertion)).build();
  try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(12)).build()){var initialized=client.initialize();if(!initialized.protocolVersion().equals("2025-11-25"))throw new RestFailure("REST_UNAVAILABLE");return operation.apply(client);
  }catch(RestFailure e){throw e;}catch(io.modelcontextprotocol.spec.McpError e){throw new RestFailure("REST_GATEWAY_UNAVAILABLE");}catch(Exception e){throw new RestFailure("REST_GATEWAY_UNAVAILABLE");}finally{calls.release();}
 }
 @Override public Map<String,Object> call(String assertion,String tool,Map<String,Object> arguments){return wire(assertion,c->{var r=c.callTool(new McpSchema.CallToolRequest(tool,arguments));if(Boolean.TRUE.equals(r.isError())){String code=r.content().isEmpty()?"REST_UNAVAILABLE":r.content().getFirst() instanceof McpSchema.TextContent t?t.text():"REST_UNAVAILABLE";throw new RestFailure(code);}return json.convertValue(r.structuredContent(),Map.class);});}
 @Override public List<Map<String,Object>> tools(String assertion){return wire(assertion,c->c.listTools().tools().stream().map(t->(Map<String,Object>)json.convertValue(t,Map.class)).toList());}
 @Override public void retire(UUID org,UUID connector){try{post("/internal/retire",null,Map.of("organizationId",org,"connectorId",connector));}catch(RestFailure ignored){/* Persisted authority still denies; periodic reconciliation retires resources. */}}
 @Override public void cancel(UUID execution){try{post("/internal/cancel",null,Map.of("execution",execution));}catch(RestFailure ignored){}}
 @Override public void reconcile(){try{post("/internal/reconcile",null,Map.of());}catch(RestFailure ignored){}}
 @jakarta.annotation.PreDestroy public void shutdown(){http.shutdownNow();}
}
