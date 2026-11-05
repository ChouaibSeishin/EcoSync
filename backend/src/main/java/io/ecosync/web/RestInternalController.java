package io.ecosync.web;
import io.ecosync.rest.RestRegistry;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@org.springframework.web.bind.annotation.RestController @RequestMapping("/internal/rest-gateway")
@ConditionalOnProperty(name="ecosync.rest.enabled",havingValue="true")
public class RestInternalController {
 private final RestRegistry registry;public RestInternalController(RestRegistry registry){this.registry=registry;}
 public record Authorization(String mode,UUID operationId){}
 @GetMapping("/catalog") public Map<String,Object> catalog(){return registry.catalog();}
 @PostMapping("/authorize") public Map<String,Object> authorize(@RequestHeader("X-Ecosync-Delegation")String assertion,@RequestBody Authorization input){return registry.authorize(assertion,input.mode,input.operationId);}
}
