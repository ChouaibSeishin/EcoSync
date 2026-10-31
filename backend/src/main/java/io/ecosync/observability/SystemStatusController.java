package io.ecosync.observability;
import java.lang.management.ManagementFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/system")
public class SystemStatusController {
    private final DataSource dataSource;
    private final boolean mcp;
    public SystemStatusController(DataSource dataSource) { this(dataSource,false); }
    @org.springframework.beans.factory.annotation.Autowired
    public SystemStatusController(DataSource dataSource,@org.springframework.beans.factory.annotation.Value("${ecosync.mcp.enabled:false}")boolean mcp) { this.dataSource = dataSource;this.mcp=mcp; }
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean available;
        try (var connection = dataSource.getConnection()) { available = connection.isValid(1); }
        catch (Exception error) { available = false; }
        return Map.of("application", "UP", "database", available ? "UP" : "DOWN",
                "status", available ? "UP" : "DEGRADED", "phase", mcp?3:1,
                "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "aiAvailable", false, "integrationManagementAvailable", mcp);
    }
}
