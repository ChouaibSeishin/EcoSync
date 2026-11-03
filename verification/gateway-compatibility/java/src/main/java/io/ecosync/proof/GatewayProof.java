package io.ecosync.proof;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.*;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.*;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Disposable wire gate; authority is a separate HTTP peer backed by a retained file. */
@SpringBootApplication @RestController
public class GatewayProof {
 private final JsonMapper json=new JsonMapper();
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
 private McpSyncServer server;
 public static void main(String[] a){SpringApplication.run(GatewayProof.class,a);}
 private Map<String,Object> claims(String value){
  try{
   if(value==null||value.length()>4096)throw new Exception();String[] p=value.split("\\.");if(p.length!=3)throw new Exception();
   var decoder=Base64.getUrlDecoder();var head=json.readTree(decoder.decode(p[0]));
   if(!head.path("alg").asString().equals("EdDSA")||!head.path("typ").asString().equals("JWT"))throw new Exception();
   var key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(System.getenv("PROOF_PUBLIC_KEY"))));
   var signature=Signature.getInstance("Ed25519");signature.initVerify(key);signature.update((p[0]+"."+p[1]).getBytes(StandardCharsets.US_ASCII));if(!signature.verify(decoder.decode(p[2])))throw new Exception();
   var c=json.readTree(decoder.decode(p[1]));long now=Instant.now().getEpochSecond();
   if(!c.path("iss").asString().equals("ecosync-platform")||!c.path("aud").asString().equals("ecosync-db-gateway")||c.path("exp").asLong()<=now||c.path("iat").asLong()>now+2||c.path("exp").asLong()-c.path("iat").asLong()>30)throw new Exception();
   UUID.fromString(c.path("org").asString());UUID.fromString(c.path("sub").asString());
   return json.convertValue(c,Map.class);
  }catch(Exception e){throw new SecurityException("DELEGATION_DENIED");}
 }
 private Map<String,Object> authority(String assertion,String table){
  try{
   var req=HttpRequest.newBuilder(URI.create(System.getenv("PROOF_AUTHORITY")+"/authorize")).timeout(Duration.ofSeconds(2)).header("Authorization","Bearer "+System.getenv("PROOF_TOKEN")).header("X-Ecosync-Delegation",assertion).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("table",table)))).build();
   var r=http.send(req,HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200||r.body().length()>4096)throw new Exception();return json.readValue(r.body(),Map.class);
  }catch(Exception e){throw new SecurityException("AUTHORITY_UNAVAILABLE_OR_DENIED");}
 }
 @GetMapping("/health") Map<String,Boolean> health(){return Map.of("ready",true);}
 @PostMapping("/proof/fail-add") void failAdd(){server.addTool(null);}
 @PostMapping("/proof/fail-remove") void failRemove(){server.removeTool("not_registered");}
 @PostMapping("/proof/reconcile") synchronized Map<String,Boolean> reconcile(@RequestBody Map<String,Object> input){
  boolean active=Boolean.TRUE.equals(input.get("active"));String name="database_read_rows";
  if(server.listTools().stream().anyMatch(t->t.name().equals(name)))server.removeTool(name);
  if(active){
   var schema=Map.<String,Object>of("type","object","properties",Map.of("tableId",Map.of("type","string","maxLength",36)),"required",List.of("tableId"),"additionalProperties",false);
   server.addTool(McpServerFeatures.SyncToolSpecification.builder().tool(McpSchema.Tool.builder(name,schema).description("Read approved table by opaque identity.").build()).callHandler((exchange,request)->{
    try{String a=(String)exchange.transportContext().get("assertion");claims(a);var allowed=authority(a,(String)request.arguments().get("tableId"));return McpSchema.CallToolResult.builder().structuredContent(Map.of("value",allowed.get("value"))).addTextContent("Untrusted proof data").isError(false).build();}
    catch(Exception e){return McpSchema.CallToolResult.builder().addTextContent("ACCESS_DENIED").isError(true).build();}
   }).build());
  }
  return Map.of("active",active);
 }
 @Bean HttpServletStreamableServerTransportProvider transport(){return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp").maxRequestSize(16384).contextExtractor(r->McpTransportContext.create(Map.of("assertion",r.getHeader("X-Ecosync-Delegation")))).build();}
 @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> servlet(HttpServletStreamableServerTransportProvider t){var b=new ServletRegistrationBean<>(t,"/mcp");b.setAsyncSupported(true);return b;}
 @Bean(destroyMethod="close") McpSyncServer mcp(HttpServletStreamableServerTransportProvider t){
  server=McpServer.sync(t).serverInfo("ecosync-managed-gate-proof","0.4.0").capabilities(McpSchema.ServerCapabilities.builder().tools(true).build()).addToolFilter((context,tool)->{
   try{return Boolean.TRUE.equals(authority((String)context.get("assertion"),"").get("visible"));}catch(Exception e){return false;}
  }).build();return server;
 }
 @Bean FilterRegistrationBean<Filter> security(){
  Filter f=(request,response,chain)->{var r=(HttpServletRequest)request;var s=(HttpServletResponse)response;
   if(r.getHeader("Origin")!=null||!Set.of("localhost","127.0.0.1").contains(r.getServerName())){s.setStatus(403);return;}
   if(!MessageDigest.isEqual(("Bearer "+System.getenv("PROOF_TOKEN")).getBytes(StandardCharsets.UTF_8),Objects.toString(r.getHeader("Authorization"),"").getBytes(StandardCharsets.UTF_8))){s.setStatus(401);return;}
   if(r.getRequestURI().equals("/mcp"))try{claims(r.getHeader("X-Ecosync-Delegation"));}catch(Exception e){s.setStatus(403);return;}
   chain.doFilter(request,response);
  };var b=new FilterRegistrationBean<>(f);b.setUrlPatterns(List.of("/mcp","/proof/*"));b.setAsyncSupported(true);return b;
 }
}
