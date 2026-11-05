package io.ecosync.web;
import io.ecosync.rest.RestRegistry;
import io.ecosync.restcontract.RestFailure;
import io.ecosync.identity.AccountPrincipal;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@org.springframework.web.bind.annotation.RestController @RequestMapping("/api/organizations/{org}/rest-connectors")
public class RestConnectorController {
 private final ObjectProvider<RestRegistry> registry;public RestConnectorController(ObjectProvider<RestRegistry> registry){this.registry=registry;}
 private RestRegistry service(){var r=registry.getIfAvailable();if(r==null)throw new RestFailure("REST_DISABLED");return r;}
 public record Invocation(UUID invocationId,Map<String,Object> parameters){}
 @GetMapping public Map<String,Object> list(@PathVariable UUID org,@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="false")boolean includeRevoked){var r=registry.getIfAvailable();return r==null?Map.of("available",false,"items",List.of()):r.list(actor.id(),org,includeRevoked);}
 @PostMapping public Map<String,Object> create(@PathVariable UUID org,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody RestRegistry.Registration input){return service().register(actor.id(),org,input);}
 @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().detail(actor.id(),org,id);}
 @PutMapping("/{id}") public Map<String,Object> revise(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody RestRegistry.Registration input){return service().revise(actor.id(),org,id,input);}
 @PostMapping("/{id}/inspect") public Map<String,Object> inspect(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().inspect(actor.id(),org,id);}
 @PostMapping("/{id}/operations/{operation}/approval") public Map<String,Object> approve(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID operation,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody RestRegistry.Scope input){return service().approve(actor.id(),org,id,operation,input);}
 @PostMapping("/{id}/activate") public Map<String,Object> activate(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().activate(actor.id(),org,id);}
 @PostMapping("/{id}/disable") public Map<String,Object> disable(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().disable(actor.id(),org,id,false);}
 @PostMapping("/{id}/revoke") public Map<String,Object> revoke(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().disable(actor.id(),org,id,true);}
 @GetMapping("/{id}/gateway-tools") public Map<String,Object> tools(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return service().tools(actor.id(),org,id);}
 @PostMapping("/{id}/operations/{operation}/invoke") public Map<String,Object> invoke(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID operation,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Invocation input){return service().execute(actor.id(),org,id,operation,input.invocationId,input.parameters,"INVOKE");}
 @PostMapping("/{id}/operations/{operation}/test") public Map<String,Object> test(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID operation,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Invocation input){return service().execute(actor.id(),org,id,operation,input.invocationId,input.parameters,"TEST");}
 @PostMapping("/{id}/operations/{operation}/describe") public Map<String,Object> describe(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID operation,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Invocation input){return service().execute(actor.id(),org,id,operation,input.invocationId,input.parameters,"DESCRIBE");}
 @PostMapping("/{id}/invocations/{execution}/cancel") public Map<String,Object> cancel(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID execution,@AuthenticationPrincipal AccountPrincipal actor){return service().cancel(actor.id(),org,id,execution);}
}
