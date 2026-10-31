package io.ecosync.verification;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class ProofApplication {
    public static void main(String[] args) {
        if (args.length>0 && args[0].equals("--probe")) { probe(args[1]); return; }
        SpringApplication.run(ProofApplication.class,args);
    }
    private static String token() {
        String value=System.getenv("POC_TOKEN");
        if(value==null||value.length()<32)throw new IllegalStateException("Proof credential required");
        return value;
    }
    @GetMapping("/health") Map<String,Object> health(){return Map.of("ready",true);}
    @Bean HttpServletStreamableServerTransportProvider transport(){
        return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp")
            .maxRequestSize(16384).keepAliveInterval(Duration.ofSeconds(10)).build();
    }
    @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> servlet(HttpServletStreamableServerTransportProvider transport){
        var bean=new ServletRegistrationBean<>(transport,"/mcp");bean.setAsyncSupported(true);return bean;
    }
    @Bean(destroyMethod="close") McpSyncServer server(HttpServletStreamableServerTransportProvider transport){
        Map<String,Object> schema=Map.of("type","object","properties",Map.of("value",Map.of("type","string","maxLength",40)),"required",List.of("value"),"additionalProperties",false);
        var tool=McpSchema.Tool.builder("proof_echo",schema).description("Disposable wire proof; no source data.")
            .outputSchema(Map.of("type","object","properties",Map.of("value",Map.of("type","string"),"amount",Map.of("type","string")),"required",List.of("value","amount"),"additionalProperties",false)).build();
        var delay=McpSchema.Tool.builder("proof_delay",schema).description("Disposable timeout proof.").build();
        return McpServer.sync(transport).serverInfo("ecosync-java-legacy-proof","1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .toolCall(tool,(exchange,request)->McpSchema.CallToolResult.builder()
                .addTextContent("Wire proof").structuredContent(Map.of("value",request.arguments().get("value"),"amount","12.34")).isError(false).build())
            .toolCall(delay,(exchange,request)->{try{Thread.sleep(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                return McpSchema.CallToolResult.builder().addTextContent("Delay completed").isError(false).build();}).build();
    }
    @Bean FilterRegistrationBean<Filter> auth(){
        final byte[] expected=("Bearer "+token()).getBytes(StandardCharsets.UTF_8);
        Filter filter=(request,response,chain)->{
            var req=(HttpServletRequest)request;var res=(HttpServletResponse)response;
            String origin=req.getHeader("Origin"),auth=req.getHeader("Authorization");
            if(origin!=null&&!Set.of("http://localhost","http://127.0.0.1").contains(origin)){res.setStatus(403);return;}
            if(!Set.of("localhost","127.0.0.1").contains(req.getServerName())){res.setStatus(403);return;}
            if(auth==null||!MessageDigest.isEqual(expected,auth.getBytes(StandardCharsets.UTF_8))){res.setHeader("WWW-Authenticate","Bearer");res.setStatus(401);return;}
            chain.doFilter(request,response);
        };
        var bean=new FilterRegistrationBean<>(filter);bean.setUrlPatterns(List.of("/mcp"));bean.setAsyncSupported(true);bean.setOrder(-100);return bean;
    }
    static void probe(String base){
        var transport=HttpClientStreamableHttpTransport.builder(base).endpoint("/mcp")
            .customizeClient(b->b.followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)))
            .addHttpRequestCustomizer((builder,method,uri,body,context)->builder.header("Authorization","Bearer "+token())).build();
        String stage="initialize";
        try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(2)).build()){
            var init=client.initialize();stage="tools/list";
            var tools=client.listTools();stage="tools/call";
            var result=client.callTool(new McpSchema.CallToolRequest("proof_echo",Map.of("value","wire")));
            System.out.println("PROBE_JSON="+new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("supported",true,"protocol",init.protocolVersion(),"server",init.serverInfo().name(),"tools",tools.tools().stream().map(McpSchema.Tool::name).toList(),"structuredContent",result.structuredContent())));
        }catch(Exception e){System.out.println("PROBE_JSON={\"supported\":false,\"stage\":\""+stage+"\",\"errorType\":\""+e.getClass().getSimpleName()+"\"}");}
    }
}
