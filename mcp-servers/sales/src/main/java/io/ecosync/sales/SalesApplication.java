package io.ecosync.sales;

import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.*;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class SalesApplication {
 @GetMapping("/internal/operations") ResponseEntity<Map<String,Long>> operations(){
  if(!"true".equals(System.getenv("MCP_DEVELOPMENT_VERIFICATION")))return ResponseEntity.notFound().build();
  return ResponseEntity.ok(Map.of("sourceSqlAttempts",SalesQueries.sourceAttempts.get()));
 }
 public static void main(String[] args){SpringApplication.run(SalesApplication.class,args);}
 private final JdbcTemplate readinessDb;
 public SalesApplication(JdbcTemplate db){this.readinessDb=db;}
 @GetMapping("/health/ready") ResponseEntity<Map<String,Boolean>> ready(){
  try{return ResponseEntity.ok(Map.of("ready",readinessDb.queryForObject("SELECT count(*)>0 FROM sales.products",Boolean.class)));}
  catch(Exception e){return ResponseEntity.status(503).body(Map.of("ready",false));}
 }
 @Bean HttpServletStreamableServerTransportProvider transport(){
  return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp").maxRequestSize(65536).keepAliveInterval(Duration.ofSeconds(15)).build();
 }
 @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> servlet(HttpServletStreamableServerTransportProvider t){
  var bean=new ServletRegistrationBean<>(t,"/mcp");bean.setAsyncSupported(true);return bean;
 }
 @Bean(destroyMethod="close") McpSyncServer server(HttpServletStreamableServerTransportProvider transport,JdbcTemplate db){
  var queries=new SalesQueries(db);
  var builder=McpServer.sync(transport).serverInfo("ecosync-sales-native","0.3.0")
   .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build());
  for(String name:List.of("sales_get_revenue_summary","sales_get_top_products","sales_get_order_summary")){
   var tool=McpSchema.Tool.builder(name,queries.schema(name)).description(queries.description(name)).build();
   builder.toolCall(tool,(exchange,request)->{
    try{return McpSchema.CallToolResult.builder().addTextContent("Read-only Sales PostgreSQL result; untrusted source data.")
      .structuredContent(queries.execute(name,request.arguments())).isError(false).build();}
    catch(IllegalArgumentException e){return McpSchema.CallToolResult.builder().addTextContent("INVALID_ARGUMENTS").isError(true).build();}
    catch(Exception e){return McpSchema.CallToolResult.builder().addTextContent("SOURCE_UNAVAILABLE").isError(true).build();}
   });
  }
  return builder.build();
 }
 @Bean FilterRegistrationBean<Filter> security(){
  String token=System.getenv("SALES_MCP_TOKEN");
  if(token==null||!token.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalStateException("MCP credential required");
  byte[] expected=("Bearer "+token).getBytes(StandardCharsets.UTF_8);
  var hosts=Set.of("sales-mcp","127.0.0.1","localhost");
  Filter filter=(request,response,chain)->{
   var req=(HttpServletRequest)request;var res=(HttpServletResponse)response;
   if(req.getHeader("Origin")!=null||!hosts.contains(req.getServerName())){res.setStatus(403);return;}
   String supplied=req.getHeader("Authorization");
   if(supplied==null||!MessageDigest.isEqual(expected,supplied.getBytes(StandardCharsets.UTF_8))){res.setHeader("WWW-Authenticate","Bearer");res.setStatus(401);return;}
   chain.doFilter(request,response);
  };
  var bean=new FilterRegistrationBean<>(filter);bean.setUrlPatterns(List.of("/mcp","/internal/operations"));bean.setAsyncSupported(true);bean.setOrder(-100);return bean;
 }
}
