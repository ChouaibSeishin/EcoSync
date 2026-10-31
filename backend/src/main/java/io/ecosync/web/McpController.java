package io.ecosync.web;
import io.ecosync.identity.AccountPrincipal;
import io.ecosync.integrations.McpRegistry;
import io.ecosync.mcp.McpFailure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/organizations/{org}/integrations")
public class McpController {
 private final ObjectProvider<McpRegistry> registry;
 public McpController(ObjectProvider<McpRegistry> registry){this.registry=registry;}
 private McpRegistry service(){var value=registry.getIfAvailable();if(value==null)throw new McpFailure("MCP_DISABLED");return value;}
 public record Approval(@NotNull String role,boolean approved){}
 public record Invocation(@NotNull UUID invocationId,@NotNull Map<String,Object> arguments){}
 @PostMapping public Map<String,Object> create(@PathVariable UUID org,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody McpRegistry.Registration input){return service().register(actor.id(),org,input);}
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().detail(actor.id(),org,id);}
 @PutMapping("/{id}") public Map<String,Object> edit(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody McpRegistry.Registration input){return service().edit(actor.id(),org,id,input);}
 @PostMapping("/{id}/validate") public Map<String,Object> validate(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().validate(actor.id(),org,id);}
 @PostMapping("/{id}/activate") public Map<String,Object> activate(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().activate(actor.id(),org,id);}
 @PostMapping("/{id}/disable") public Map<String,Object> disable(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().disable(actor.id(),org,id,false);}
 @DeleteMapping("/{id}") public Map<String,Object> remove(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().disable(actor.id(),org,id,true);}
 @PostMapping("/{id}/capabilities/{cap}/approval") public Map<String,Object> approval(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID cap,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Approval input){return service().approve(actor.id(),org,id,cap,input.role(),input.approved());}
 @PostMapping("/{id}/capabilities/{cap}/invocations") public Map<String,Object> invoke(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID cap,@AuthenticationPrincipal AccountPrincipal actor,@Valid @RequestBody Invocation input){return service().invoke(actor.id(),org,id,cap,input.invocationId(),input.arguments());}
 @PostMapping("/{id}/invocations/{invocation}/cancel") public Map<String,Object> cancel(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID invocation,@AuthenticationPrincipal AccountPrincipal actor){return service().cancel(actor.id(),org,id,invocation);}
}
