package io.ecosync;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class MigrationIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse(
        "postgres:17.11-bookworm@sha256:2cd2c0f809604aaeff6f0ec8f40aecbd025613107561015c69369a1a7f1d3416").asCompatibleSubstituteFor("postgres"));
    @Test void migrationIsReproducibleAndTenantForeignKeyIsEnforced() throws Exception {
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("app").defaultSchema("app").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var s = c.createStatement()) {
            s.execute("SET search_path TO app");
            s.execute("INSERT INTO organization(id,slug,display_name) VALUES ('10000000-0000-0000-0000-000000000001','first','First'),('10000000-0000-0000-0000-000000000002','second','Second')");
            s.execute("INSERT INTO app_user(id,login,display_name,password_hash) VALUES ('20000000-0000-0000-0000-000000000001','fixture','Fixture','not-a-login-credential')");
            s.execute("INSERT INTO membership(organization_id,id,user_id) VALUES ('10000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001')");
            assertThatThrownBy(() -> s.execute("INSERT INTO role_assignment VALUES ('10000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000001','USER')"))
                .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23503");
        }
    }
}
