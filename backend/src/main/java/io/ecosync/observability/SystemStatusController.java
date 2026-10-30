package io.ecosync.observability;
import java.lang.management.ManagementFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/system")
public class SystemStatusController {
    private final DataSource dataSource;
    public SystemStatusController(DataSource dataSource) { this.dataSource = dataSource; }
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean available;
        try (var connection = dataSource.getConnection()) { available = connection.isValid(1); }
        catch (Exception error) { available = false; }
        return Map.of("application", "UP", "database", available ? "UP" : "DOWN",
                "status", available ? "UP" : "DEGRADED", "phase", 1,
                "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "aiAvailable", false, "integrationManagementAvailable", false);
    }
}
