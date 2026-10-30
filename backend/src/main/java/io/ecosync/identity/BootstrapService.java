package io.ecosync.identity;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class BootstrapService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public BootstrapService(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }
    @Transactional
    public boolean provision(String login, String password, String organization) {
        jdbc.execute("SELECT pg_advisory_xact_lock(841237001)");
        if (jdbc.queryForObject("SELECT count(*) FROM app.bootstrap_marker", Long.class) > 0) return false;
        if (!login.matches("[a-z][a-z0-9._-]{2,63}") || password.length() < 16 || password.length() > 128
                || organization.isBlank() || organization.length() > 120) {
            throw new IllegalArgumentException("Invalid bootstrap configuration; check documented requirements.");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM app.app_user", Long.class) > 0)
            throw new IllegalStateException("Bootstrap requires an empty identity store.");
        UUID org = UUID.randomUUID(), user = UUID.randomUUID(), membership = UUID.randomUUID();
        jdbc.update("INSERT INTO app.organization(id,slug,display_name) VALUES (?,?,?)", org, "workspace", organization);
        jdbc.update("INSERT INTO app.app_user(id,login,display_name,password_hash,platform_admin) VALUES (?,?,?,?,true)",
            user, login, "Workspace administrator", encoder.encode(password));
        jdbc.update("INSERT INTO app.membership(organization_id,id,user_id) VALUES (?,?,?)", org, membership, user);
        jdbc.update("INSERT INTO app.role_assignment VALUES (?,?,?)", org, membership, "INTEGRATION_ADMIN");
        jdbc.update("INSERT INTO app.bootstrap_marker(id) VALUES (1)");
        return true;
    }
}
