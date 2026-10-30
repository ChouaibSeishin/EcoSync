package io.ecosync.authorization;
import io.ecosync.web.HttpErrors;
import java.time.Clock;
import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
@Configuration @EnableMethodSecurity
public class SecurityConfiguration {
    @Bean LoginThrottle loginThrottle() { return new LoginThrottle(Clock.systemUTC()); }
    @Bean SecurityFilterChain security(HttpSecurity http, HttpErrors errors, LoginThrottle throttle) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/csrf", "/api/auth/login", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
            .requestMatchers("/actuator/**").denyAll()
            .requestMatchers("/api/admin/**").hasRole("PLATFORM_ADMIN")
            .anyRequest().authenticated());
        http.csrf(csrf -> { });
        http.sessionManagement(session -> session.sessionFixation(fix -> fix.changeSessionId()));
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(ex -> ex
            .authenticationEntryPoint((req,res,error) -> errors.write(req,res,401,"UNAUTHENTICATED","Sign in to continue."))
            .accessDeniedHandler((req,res,error) -> errors.write(req,res,403,"FORBIDDEN","You do not have access to this resource.")));
        http.formLogin(form -> form.loginProcessingUrl("/api/auth/login")
            .successHandler((req,res,auth) -> {
                throttle.succeeded(auth.getName().strip().toLowerCase(Locale.ROOT));
                res.setStatus(204);
            })
            .failureHandler((req,res,error) -> errors.write(req,res,401,"INVALID_CREDENTIALS","The username or password is incorrect.")));
        http.logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true)
            .clearAuthentication(true).deleteCookies("ECOSYNC_SESSION").logoutSuccessHandler((req,res,auth) -> res.setStatus(204)));
        http.addFilterBefore(new LoginThrottleFilter(throttle, errors), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
