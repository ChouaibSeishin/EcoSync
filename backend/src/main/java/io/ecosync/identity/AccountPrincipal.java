package io.ecosync.identity;
import java.io.Serializable;
import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
public record AccountPrincipal(UUID id, String login, String displayName, String passwordHash,
        boolean active, boolean administrator) implements UserDetails, Serializable {
    @Override public String getUsername() { return login; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return active; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return administrator ? List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"),
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
            : List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));
    }
}
