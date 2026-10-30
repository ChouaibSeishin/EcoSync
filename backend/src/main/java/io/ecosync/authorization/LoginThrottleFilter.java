package io.ecosync.authorization;
import io.ecosync.web.HttpErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;
final class LoginThrottleFilter extends OncePerRequestFilter {
    private final LoginThrottle throttle;
    private final HttpErrors errors;
    LoginThrottleFilter(LoginThrottle throttle, HttpErrors errors) { this.throttle = throttle; this.errors = errors; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getMethod().equals("POST") && req.getRequestURI().equals(req.getContextPath() + "/api/auth/login")) {
            String login = OptionalValue.normalized(req.getParameter("username"));
            String password = req.getParameter("password");
            if (login.isEmpty() || login.length() > 64 || password == null || password.length() > 128) {
                errors.write(req, res, 400, "INVALID_REQUEST", "Enter a valid username and password."); return;
            }
            if (!throttle.allow(req.getRemoteAddr(), login)) {
                res.setHeader("Retry-After", "60");
                errors.write(req, res, 429, "LOGIN_THROTTLED", "Too many sign-in attempts. Try again in a minute."); return;
            }
        }
        chain.doFilter(req, res);
    }
    private static class OptionalValue {
        static String normalized(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT); }
    }
}
