package io.ecosync.web;
import io.ecosync.database.DatabaseRegistry;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/database-gateway")
@ConditionalOnProperty(name="ecosync.database.enabled",havingValue="true")
public class DatabaseInternalController {
 private final DatabaseRegistry registry;public DatabaseInternalController(DatabaseRegistry registry){this.registry=registry;}
 public record Authorization(String mode,UUID tableId){}
 @GetMapping("/catalog") public Map<String,Object> catalog(){return registry.catalog();}
 @PostMapping("/authorize") public Map<String,Object> authorize(@RequestHeader("X-Ecosync-Delegation")String assertion,@RequestBody Authorization input){return registry.authorize(assertion,input.mode(),input.tableId());}
 @PostMapping("/drift") public Map<String,Object> drift(@RequestHeader("X-Ecosync-Delegation")String assertion,@RequestBody Authorization input){registry.drift(assertion,input.tableId());return Map.of("stale",true);}
}
