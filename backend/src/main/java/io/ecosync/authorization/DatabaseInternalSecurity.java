package io.ecosync.authorization;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name="ecosync.database.enabled",havingValue="true")
public class DatabaseInternalSecurity {
 @Bean @Order(1) SecurityFilterChain databaseInternal(HttpSecurity http,@Value("${ecosync.database.service-token:}")String token)throws Exception{
  if(!token.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalStateException("Database service credential required");
  http.securityMatcher("/internal/database-gateway/**").csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).requestCache(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().authenticated());
  http.addFilterBefore(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws java.io.IOException,ServletException{
   if(req.getHeader("Origin")!=null||!MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),Objects.toString(req.getHeader("Authorization"),"").getBytes(StandardCharsets.UTF_8))){res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"code\":\"DB_ACCESS_DENIED\"}");return;}
   SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("database-gateway",null,List.of()));chain.doFilter(req,res);
  }},UsernamePasswordAuthenticationFilter.class);return http.build();
 }
}
