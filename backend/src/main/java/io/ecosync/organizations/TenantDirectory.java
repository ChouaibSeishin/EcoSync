package io.ecosync.organizations;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
@Service
public class TenantDirectory {
    public record Workspace(UUID id, String name, List<String> roles, String createdAt) { }
    private final JdbcTemplate jdbc;
    public TenantDirectory(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Workspace> memberships(UUID user) {
        return jdbc.query("""
            SELECT o.id, o.display_name, o.created_at, m.id AS membership_id
            FROM app.organization o JOIN app.membership m ON m.organization_id=o.id
            JOIN app.app_user u ON u.id=m.user_id
            WHERE m.user_id=? AND o.enabled AND u.enabled
              AND EXISTS (SELECT 1 FROM app.role_assignment r WHERE r.organization_id=o.id AND r.membership_id=m.id)
            ORDER BY o.display_name, o.id
            """, (rs,n) -> new Workspace(rs.getObject("id", UUID.class), rs.getString("display_name"),
                jdbc.queryForList("SELECT role FROM app.role_assignment WHERE organization_id=? AND membership_id=? ORDER BY role",
                    String.class, rs.getObject("id", UUID.class), rs.getObject("membership_id", UUID.class)),
                rs.getTimestamp("created_at").toInstant().toString()), user);
    }
    public Workspace requireMembership(UUID user, UUID organization) {
        return memberships(user).stream().filter(m -> m.id.equals(organization)).findFirst()
            .orElseThrow(() -> new AccessDeniedException("Workspace access denied"));
    }
}
