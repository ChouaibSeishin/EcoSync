package io.ecosync.observability;
import javax.sql.DataSource;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class SystemStatusTest {
    @Test void databaseFailureIsVisibleWithoutLeakingConnectionInformation() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("password=sentinel-secret; internal-host"));
        var result = new SystemStatusController(ds).status();
        assertThat(result).containsEntry("application", "UP").containsEntry("database", "DOWN")
            .containsEntry("status", "DEGRADED");
        assertThat(result.toString()).doesNotContain("sentinel", "internal-host");
    }
}
