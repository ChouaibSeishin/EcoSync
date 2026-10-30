package io.ecosync;
import io.ecosync.identity.BootstrapService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest @Testcontainers @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(SecurityIT.FailureFixture.class)
class SecurityIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
        "postgres:17.11-bookworm@sha256:2cd2c0f809604aaeff6f0ec8f40aecbd025613107561015c69369a1a7f1d3416")
        .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        postgres.start(); // PER_CLASS Spring injection can resolve properties before the extension beforeAll.
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        p.add("spring.flyway.enabled", () -> true);
        p.add("server.servlet.session.cookie.secure", () -> false);
    }
    @Autowired WebApplicationContext context;
    @Autowired BootstrapService bootstrap;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    UUID firstOrg, secondOrg;
    final String password = UUID.randomUUID() + "!valid-test";
    @BeforeAll void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        assertThat(bootstrap.provision("testadmin", password, "First workspace")).isTrue();
        firstOrg = jdbc.queryForObject("SELECT id FROM app.organization WHERE slug='workspace'", UUID.class);
        secondOrg = UUID.randomUUID();
        jdbc.update("INSERT INTO app.organization(id,slug,display_name) VALUES (?,?,?)", secondOrg, "second", "Second workspace");
        addUser("reader", firstOrg); addUser("other", secondOrg);
    }
    void addUser(String login, UUID org) {
        UUID user = UUID.randomUUID(), membership = UUID.randomUUID();
        jdbc.update("INSERT INTO app.app_user(id,login,display_name,password_hash) VALUES (?,?,?,?)",
            user, login, login, encoder.encode(password));
        jdbc.update("INSERT INTO app.membership VALUES (?,?,?)", org, membership, user);
        jdbc.update("INSERT INTO app.role_assignment VALUES (?,?,?)", org, membership, "USER");
    }
    MockHttpSession login(String user) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf())
            .param("username", user).param("password", password)).andExpect(status().isNoContent())
            .andReturn().getRequest().getSession(false);
    }
    @Test void anonymousAccessDeniedAndCsrfRequiredEvenForLogin() throws Exception {
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/auth/login").param("username","testadmin").param("password",password)).andExpect(status().isForbidden());
    }
    @Test void invalidCredentialsAreGenericAndOverlongInputRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","unknown").param("password","invalid"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","x".repeat(65)).param("password",password))
            .andExpect(status().isBadRequest());
    }
    @Test void loginRotatesSessionAndReturnsNoPassword() throws Exception {
        var before = new MockHttpSession(); String original = before.getId();
        var after = (MockHttpSession)mvc.perform(post("/api/auth/login").session(before).with(r -> { r.setRequestedSessionId(original); r.setRequestedSessionIdValid(true); return r; }).with(csrf())
            .param("username","testadmin").param("password",password)).andExpect(status().isNoContent())
            .andReturn().getRequest().getSession(false);
        assertThat(after.getId()).isNotEqualTo(original);
        mvc.perform(get("/api/session").session(after)).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("testadmin")).andExpect(jsonPath("$.workspaces.length()").value(1))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
    }
    @Test void rolesAndCrossOrganizationAccessAreEnforced() throws Exception {
        var reader = login("reader");
        mvc.perform(get("/api/admin/summary").session(reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/organizations/"+firstOrg+"/overview").session(reader)).andExpect(status().isOk());
        mvc.perform(get("/api/organizations/"+secondOrg+"/overview").session(reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/organizations/"+secondOrg+"/integrations").session(login("testadmin"))).andExpect(status().isForbidden());
    }
    @Test void logoutRequiresCsrfAndInvalidatesSession() throws Exception {
        var session = login("reader");
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
    }
    @Test void bootstrapIsOneTimeAndNeverResetsExistingPassword() {
        String before = jdbc.queryForObject("SELECT password_hash FROM app.app_user WHERE login='testadmin'",String.class);
        assertThat(bootstrap.provision("replacement", UUID.randomUUID().toString(), "Replacement")).isFalse();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app.app_user WHERE login='testadmin'",String.class)).isEqualTo(before);
        assertThat(encoder.matches(password, before)).isTrue();
        assertThat(before).startsWith("{bcrypt}").isNotEqualTo(password);
    }
    @Test void healthAndErrorsDoNotExposeInternalDetails() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").session(login("testadmin"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/test/failure").session(login("reader"))).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sentinel-secret"))));
    }
    @Test void throttleAppliesToActualLoginRequests() throws Exception {
        for (int i=0;i<5;i++) mvc.perform(post("/api/auth/login").with(csrf()).param("username","throttle-target").param("password","incorrect")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username","throttle-target").param("password","incorrect")).andExpect(status().isTooManyRequests());
    }
    @Test void malformedIdsAndMissingRoutesAreSafe() throws Exception {
        var session = login("reader");
        mvc.perform(get("/api/organizations/not-a-uuid/overview").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/unknown").session(session)).andExpect(status().isNotFound());
    }
    @RestController static class FailureFixture {
        @GetMapping("/api/test/failure") void failure() { throw new IllegalStateException("password=sentinel-secret; internal-host"); }
    }
}
