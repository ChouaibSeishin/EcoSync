package io.ecosync.observability;
import java.lang.management.ManagementFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/system")
public class SystemStatusController {
    private final DataSource dataSource;
    private final boolean mcp,database,rest;
    @org.springframework.beans.factory.annotation.Value("${AI_ENABLED:false}") private boolean aiEnabled;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<io.ecosync.chat.ModelPort> models;
    public SystemStatusController(DataSource dataSource) { this(dataSource,false,false); }
    public SystemStatusController(DataSource dataSource,boolean mcp,boolean database) { this(dataSource,mcp,database,false); }
    @org.springframework.beans.factory.annotation.Autowired
    public SystemStatusController(DataSource dataSource,@org.springframework.beans.factory.annotation.Value("${ecosync.mcp.enabled:false}")boolean mcp,@org.springframework.beans.factory.annotation.Value("${ecosync.database.enabled:false}")boolean database,@org.springframework.beans.factory.annotation.Value("${ecosync.rest.enabled:false}")boolean rest) { this.rest=rest;this.dataSource = dataSource;this.mcp=mcp;this.database=database; }
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean available;
        try (var connection = dataSource.getConnection()) { available = connection.isValid(1); }
        catch (Exception error) { available = false; }
        return Map.of("application", "UP", "database", available ? "UP" : "DOWN",
                "status", available ? "UP" : "DEGRADED", "phase", aiEnabled?6:rest?5:database?4:mcp?3:1,
                "uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                "aiAvailable", aiEnabled&&models!=null&&models.getIfAvailable()!=null, "integrationManagementAvailable", mcp,"databaseManagementAvailable",database,"restManagementAvailable",rest);
    }
}
