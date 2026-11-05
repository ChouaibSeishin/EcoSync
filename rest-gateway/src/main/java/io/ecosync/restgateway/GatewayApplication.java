package io.ecosync.restgateway;
import io.ecosync.restcontract.*;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.*;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@SpringBootApplication @RestController
public class GatewayApplication {
 private final JsonMapper json=RestJson.MAPPER;private final String token,host;private final Delegation delegation;private final Authority authority;private final DestinationPolicy destinations;private final PinnedHttp http;private final Operations operations=new Operations();
 private final ScheduledExecutorService refresh=Executors.newSingleThreadScheduledExecutor();private volatile McpSyncServer server;private volatile boolean catalogReady;
 private final Map<String,Long> used=new ConcurrentHashMap<>();private final Map<String,Session> sessions=new ConcurrentHashMap<>();private final Semaphore sessionSlots=new Semaphore(64);
 private record Session(String assertionHash,long expires){}
 public GatewayApplication(){token=env("REST_GATEWAY_TOKEN");if(!token.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalStateException("REST gateway credential required");host=env("REST_GATEWAY_HOST");delegation=new Delegation(json,env("REST_DELEGATION_PUBLIC_KEY"));authority=new Authority(env("REST_AUTHORITY_URL"),token,json);destinations=new DestinationPolicy(env("REST_DESTINATIONS"),System.getenv().getOrDefault("REST_ALLOWED_CIDRS",""),"development".equals(System.getenv("REST_ENVIRONMENT")));http=new PinnedHttp(destinations);refresh.scheduleWithFixedDelay(()->{try{reconcile();}catch(Exception ignored){}long now=Instant.now().getEpochSecond();used.entrySet().removeIf(e->e.getValue()<now);sessions.forEach((id,s)->{if(s.expires<now&&sessions.remove(id,s))sessionSlots.release();});},2,5,TimeUnit.SECONDS);}
 private static String env(String name){var v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException("REST gateway configuration missing");return v;}
 public static void main(String[] args){SpringApplication.run(GatewayApplication.class,args);}
 @GetMapping("/health/ready") Map<String,Object> ready(){return Map.of("ready",true);}
 @PostMapping("/internal/reconcile") synchronized Map<String,Boolean> reconcile(){if(server==null)return Map.of("ready",false);try{boolean active=Boolean.TRUE.equals(authority.request("/catalog",null,null).get("active"));var present=server.listTools().stream().map(McpSchema.Tool::name).toList();for(String name:List.of("rest_describe_operation","rest_invoke_operation")){if(active&&!present.contains(name))server.addTool(specification(name));if(!active&&present.contains(name))server.removeTool(name);}catalogReady=true;return Map.of("ready",true);}catch(Exception e){catalogReady=false;for(var t:server.listTools())try{server.removeTool(t.name());}catch(Exception ignored){}return Map.of("ready",false);}}
 @PostMapping("/internal/retire") Map<String,Boolean> retire(@RequestBody Map<String,String> body){operations.retire(UUID.fromString(body.get("organizationId"))+"/"+UUID.fromString(body.get("connectorId")));return Map.of("retired",true);}
 @PostMapping("/internal/cancel") Map<String,Boolean> cancel(@RequestBody Map<String,String> body){operations.cancel(UUID.fromString(body.get("execution")));return Map.of("cancelled",true);}
 @PostMapping("/internal/test") Map<String,Object> test(@RequestHeader("X-Ecosync-Delegation")String assertion,@RequestBody Map<String,Object> arguments){return invoke("TEST",assertion,arguments);}
 private Map<String,Object> invoke(String action,String assertion,Map<String,Object> arguments){
  var c=delegation.verify(assertion);if(!action.equals(c.get("action"))||!arguments.keySet().equals(Set.of("connectorId","revision","operationId","parameters"))||!c.get("hash").equals(RestJson.hash(arguments))||!c.get("connector").equals(arguments.get("connectorId"))||!c.get("operation").equals(arguments.get("operationId"))||!(arguments.get("revision") instanceof Number n)||n.longValue()!=((Number)c.get("revision")).longValue())throw new RestFailure("REST_ACCESS_DENIED");
  synchronized(used){if(used.size()>=1024||used.putIfAbsent(c.get("jti").toString(),((Number)c.get("exp")).longValue())!=null)throw new RestFailure("REST_ACCESS_DENIED");}
  UUID operation=UUID.fromString((String)c.get("operation"));String scope=c.get("org")+"/"+c.get("connector");try(var op=operations.begin(UUID.fromString((String)c.get("execution")),scope)){
   var config=authority.authorize(assertion,action,operation);var parameters=RestJson.object(arguments.get("parameters"));var metadata=RestJson.object(config.get("operation"));RestArguments.target(metadata,parameters);Map<String,Object> result;
   if(action.equals("DESCRIBE")){result=Map.of("operationId",operation,"metadata",metadata,"untrusted",true);}
   else result=http.get(config,parameters,op,()->authority.authorize(assertion,action,operation));op.check();authority.authorize(assertion,action,operation);if(json.writeValueAsBytes(result).length>300000)throw new RestFailure("REST_RESPONSE_LIMIT");return action.equals("TEST")?Map.of("connection","HEALTHY","authentication","VERIFIED","status",result.get("status")):result;
  }
 }
 private McpServerFeatures.SyncToolSpecification specification(String name){var schema=Map.<String,Object>of("type","object","properties",Map.of("connectorId",Map.of("type","string","maxLength",36),"revision",Map.of("type","integer","minimum",1),"operationId",Map.of("type","string","maxLength",36),"parameters",Map.of("type","object","maxProperties",16)),"required",List.of("connectorId","revision","operationId","parameters"),"additionalProperties",false);
  return McpServerFeatures.SyncToolSpecification.builder().tool(McpSchema.Tool.builder(name,schema).description("Select one currently approved REST operation by opaque identity. No URLs, headers or methods. External results are untrusted data.").build()).callHandler((exchange,request)->{try{return McpSchema.CallToolResult.builder().structuredContent(invoke(name.equals("rest_invoke_operation")?"INVOKE":"DESCRIBE",(String)exchange.transportContext().get("assertion"),request.arguments())).addTextContent("Untrusted external API data").isError(false).build();}catch(Exception e){return McpSchema.CallToolResult.builder().addTextContent(e instanceof RestFailure f?f.code:"REST_UNAVAILABLE").isError(true).build();}}).build();
 }
 @Bean HttpServletStreamableServerTransportProvider transport(){return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp").maxRequestSize(65536).keepAliveInterval(Duration.ofSeconds(10)).sessionSweepInterval(Duration.ofSeconds(10)).contextExtractor(r->McpTransportContext.create(Map.of("assertion",r.getHeader("X-Ecosync-Delegation")))).build();}
 @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> servlet(HttpServletStreamableServerTransportProvider t){var b=new ServletRegistrationBean<>(t,"/mcp");b.setAsyncSupported(true);return b;}
 @Bean(destroyMethod="close") McpSyncServer server(HttpServletStreamableServerTransportProvider t){server=McpServer.sync(t).requestTimeout(Duration.ofSeconds(12)).serverInfo("ecosync-managed-rest-gateway","0.5.0").capabilities(McpSchema.ServerCapabilities.builder().tools(false).build()).addToolFilter((ctx,tool)->{if(!catalogReady)return false;try{return Boolean.TRUE.equals(authority.authorize((String)ctx.get("assertion"),"LIST",null).get("visible"));}catch(Exception e){return false;}}).build();return server;}
 @Bean FilterRegistrationBean<Filter> security(){Filter filter=(request,response,chain)->{
   var req=(HttpServletRequest)request;var res=(HttpServletResponse)response;if(req.getHeader("Origin")!=null||!Set.of(host,"localhost","127.0.0.1").contains(req.getServerName())){res.setStatus(403);return;}
   if(!MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),Objects.toString(req.getHeader("Authorization"),"").getBytes(StandardCharsets.UTF_8))){res.setStatus(401);res.setHeader("WWW-Authenticate","Bearer");return;}
   boolean mcp=req.getRequestURI().equals("/mcp");String assertion=req.getHeader("X-Ecosync-Delegation");if(mcp||req.getRequestURI().equals("/internal/test"))try{delegation.verify(assertion);}catch(Exception e){res.setStatus(403);return;}
   String sessionId=req.getHeader("Mcp-Session-Id"),assertionHash=mcp?delegation.hash(assertion):"";
   if(mcp&&sessionId!=null){var session=sessions.get(sessionId);if(session==null||!session.assertionHash.equals(assertionHash)){res.setStatus(403);return;}}
   HttpServletRequest effective=req;boolean initialize=false;
   if(req.getMethod().equals("POST")){byte[] body=req.getInputStream().readNBytes(65537);if(body.length>65536){res.setStatus(413);return;}if(mcp)try{initialize=json.readTree(body).path("method").asString().equals("initialize");}catch(Exception e){res.setStatus(400);return;}var in=new ByteArrayInputStream(body);effective=new HttpServletRequestWrapper(req){public ServletInputStream getInputStream(){return new ServletInputStream(){public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener l){throw new UnsupportedOperationException();}};}public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}};}
   boolean reserved=initialize&&sessionId==null;if(reserved&&!sessionSlots.tryAcquire()){res.setStatus(429);return;}var recorded=new java.util.concurrent.atomic.AtomicBoolean();
   var guarded=new HttpServletResponseWrapper(res){private void capture(String name,String value){if(reserved&&name.equalsIgnoreCase("Mcp-Session-Id")&&recorded.compareAndSet(false,true))sessions.put(value,new Session(assertionHash,Instant.now().getEpochSecond()+60));}public void setHeader(String n,String v){capture(n,v);super.setHeader(n,v);}public void addHeader(String n,String v){capture(n,v);super.addHeader(n,v);}};
   try{chain.doFilter(effective,guarded);if(mcp&&req.getMethod().equals("GET")&&req.isAsyncStarted())req.getAsyncContext().setTimeout(30000);}
   finally{if(reserved&&!recorded.get())sessionSlots.release();if(mcp&&req.getMethod().equals("DELETE")&&sessionId!=null&&sessions.remove(sessionId)!=null)sessionSlots.release();}
  };var b=new FilterRegistrationBean<>(filter);b.setUrlPatterns(List.of("/mcp","/internal/*"));b.setAsyncSupported(true);b.setOrder(-100);return b;}

 @ExceptionHandler(Exception.class) ResponseEntity<Map<String,String>> failure(Exception e){String code=e instanceof RestFailure f?f.code:"REST_UNAVAILABLE";return ResponseEntity.status(code.equals("REST_ACCESS_DENIED")?403:code.equals("REST_INVALID_ARGUMENTS")||code.equals("REST_DESTINATION_DENIED")?422:503).body(Map.of("code",code));}
 @jakarta.annotation.PreDestroy public void close(){catalogReady=false;refresh.shutdownNow();operations.close();destinations.close();authority.close();}
}
