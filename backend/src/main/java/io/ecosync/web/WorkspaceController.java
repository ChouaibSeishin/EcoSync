package io.ecosync.web;
import io.ecosync.identity.AccountPrincipal;
import io.ecosync.organizations.TenantDirectory;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController
public class WorkspaceController {
    private final TenantDirectory directory;
    private final JdbcTemplate jdbc;
    public WorkspaceController(TenantDirectory directory, JdbcTemplate jdbc) { this.directory = directory; this.jdbc = jdbc; }
    @GetMapping("/api/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @GetMapping("/api/session") public Map<String,Object> session(@AuthenticationPrincipal AccountPrincipal account) {
        return Map.of("id", account.id(), "username", account.login(), "displayName", account.displayName(),
            "platformAdministrator", account.administrator(), "workspaces", directory.memberships(account.id()));
    }
    @GetMapping("/api/organizations/{id}/overview")
    public Map<String,Object> overview(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal account) {
        return Map.of("workspace", directory.requireMembership(account.id(), id),
            "registeredIntegrations", 0, "integrationManagementAvailable", false, "aiAvailable", false);
    }
    @GetMapping("/api/organizations/{id}/integrations")
    public Map<String,Object> integrations(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal account) {
        directory.requireMembership(account.id(), id);
        return Map.of("items", List.of(), "available", false);
    }
    @GetMapping("/api/admin/summary")
    public Map<String,Object> administration() {
        return Map.of("users", jdbc.queryForObject("SELECT count(*) FROM app.app_user", Long.class),
            "organizations", jdbc.queryForObject("SELECT count(*) FROM app.organization", Long.class));
    }
}
