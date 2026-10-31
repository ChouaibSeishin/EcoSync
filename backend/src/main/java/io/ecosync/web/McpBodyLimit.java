package io.ecosync.web;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Bounds even chunked JSON before MVC deserialization. */
@Component
public class McpBodyLimit extends OncePerRequestFilter {
 private final tools.jackson.databind.json.JsonMapper json;
 public McpBodyLimit(tools.jackson.databind.json.JsonMapper json){this.json=json;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().matches("/api/organizations/[^/]+/integrations(?:/.*)?")||java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
  byte[] body=request.getInputStream().readNBytes(65537);
  if(body.length>65536){response.setStatus(413);response.setContentType("application/json");response.getWriter().write(json.writeValueAsString(new ApiError(413,"MCP_ARGUMENTS_TOO_LARGE","Request exceeds the MCP management limit.",String.valueOf(request.getAttribute("requestId")))));return;}
  var input=new ByteArrayInputStream(body);
  chain.doFilter(new HttpServletRequestWrapper(request){
   @Override public ServletInputStream getInputStream(){return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}
   @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
  },response);
 }
}
