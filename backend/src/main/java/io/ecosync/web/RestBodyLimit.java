package io.ecosync.web;
import io.ecosync.restcontract.RestJson;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Bound before MVC deserialization; duplicate argument keys must never be silently collapsed. */
@Component
public class RestBodyLimit extends OncePerRequestFilter {
 @Override protected boolean shouldNotFilter(HttpServletRequest r){return !(r.getRequestURI().contains("/rest-connectors")||r.getRequestURI().startsWith("/internal/rest-gateway"))||!java.util.Set.of("POST","PUT").contains(r.getMethod());}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
  byte[] body=request.getInputStream().readNBytes(1048577);if(body.length>1048576){response.setStatus(413);return;}if(body.length>0)try{RestJson.read(body,1048576);}catch(Exception e){response.setStatus(400);response.setContentType("application/json");response.getWriter().write("{\"code\":\"REST_INVALID_JSON\"}");return;}
  var input=new ByteArrayInputStream(body);var wrapped=new HttpServletRequestWrapper(request){public ServletInputStream getInputStream(){return new ServletInputStream(){public int read(){return input.read();}public boolean isReady(){return true;}public boolean isFinished(){return input.available()==0;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}};chain.doFilter(wrapped,response);
 }
}
