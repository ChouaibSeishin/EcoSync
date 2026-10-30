package io.ecosync.identity;
import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.security.core.userdetails.*;
@Configuration
class AccountConfiguration {
    @Bean org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() { return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean UserDetailsService accountDetails(UserAccountRepository accounts) {
        return login -> accounts.findByLogin(login.strip().toLowerCase(Locale.ROOT))
            .map(u -> new AccountPrincipal(u.id(), u.login(), u.displayName(), u.passwordHash(), u.enabled(), u.platformAdmin()))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
