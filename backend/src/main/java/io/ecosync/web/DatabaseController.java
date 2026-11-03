package io.ecosync.web;
import io.ecosync.database.*;
import io.ecosync.identity.AccountPrincipal;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/organizations/{org}/database-connectors")
public class DatabaseController {
 private final ObjectProvider<DatabaseRegistry> registry;
 public DatabaseController(ObjectProvider<DatabaseRegistry> registry){this.registry=registry;}
 private DatabaseRegistry service(){var r=registry.getIfAvailable();if(r==null)throw new DatabaseFailure("DB_DISABLED");return r;}
 public record Invocation(UUID invocationId,Map<String,Object> query){}
 @GetMapping public Map<String,Object> list(@PathVariable UUID org,@AuthenticationPrincipal AccountPrincipal actor){var r=registry.getIfAvailable();return r==null?Map.of("available",false,"items",List.of()):r.list(actor.id(),org);}
 @PostMapping public Map<String,Object> create(@PathVariable UUID org,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody DatabaseRegistry.Registration r){return service().register(actor.id(),org,r);}
 @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().detail(actor.id(),org,id);}
 @PutMapping("/{id}") public Map<String,Object> edit(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody DatabaseRegistry.Registration r){return service().edit(actor.id(),org,id,r);}
 @PostMapping("/{id}/inspect") public Map<String,Object> inspect(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().inspect(actor.id(),org,id);}
 @PostMapping("/{id}/tables/{table}/approval") public Map<String,Object> approve(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID table,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody DatabaseRegistry.Scope scope){return service().approve(actor.id(),org,id,table,scope);}
 @PostMapping("/{id}/activate") public Map<String,Object> activate(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().activate(actor.id(),org,id);}
 @PostMapping("/{id}/disable") public Map<String,Object> disable(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().disable(actor.id(),org,id,false);}
 @DeleteMapping("/{id}") public Map<String,Object> remove(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().disable(actor.id(),org,id,true);}
 @GetMapping("/{id}/gateway-tools") public Map<String,Object> tools(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().tools(actor.id(),org,id);}
 @PostMapping("/{id}/tables/{table}/query") public Map<String,Object> query(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID table,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Invocation i){return service().query(actor.id(),org,id,table,i.invocationId(),i.query(),false);}
 @PostMapping("/{id}/tables/{table}/describe") public Map<String,Object> describe(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID table,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Invocation i){return service().query(actor.id(),org,id,table,i.invocationId(),Map.of(),true);}
 @PostMapping("/{id}/invocations/{execution}/cancel") public Map<String,Object> cancel(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID execution,@AuthenticationPrincipal AccountPrincipal actor){return service().cancel(actor.id(),org,id,execution);}
}
