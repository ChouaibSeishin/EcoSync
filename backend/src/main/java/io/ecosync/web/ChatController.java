package io.ecosync.web;
import io.ecosync.chat.*;
import io.ecosync.identity.AccountPrincipal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/organizations/{org}/chat")
public class ChatController {
 private final ChatService chat;private final ChatCapabilities capabilities;
 public ChatController(ChatService chat,ChatCapabilities capabilities){this.chat=chat;this.capabilities=capabilities;}
 @GetMapping("/conversations") public Map<String,Object> list(@PathVariable UUID org,@AuthenticationPrincipal AccountPrincipal actor){return chat.list(actor.id(),org);}
 @GetMapping("/capabilities") public Map<String,Object> tools(@PathVariable UUID org,@AuthenticationPrincipal AccountPrincipal actor){return Map.of("items",capabilities.catalog(actor.id(),org).stream().map(ChatCapabilities.Capability::definition).toList());}
 @PostMapping("/conversations") public Map<String,Object> create(@PathVariable UUID org,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Map<String,Object> body){ChatData.keys(body,Set.of("title"));return chat.create(actor.id(),org,(String)body.get("title"));}
 @GetMapping("/conversations/{id}") public Map<String,Object> detail(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return chat.detail(actor.id(),org,id);}
 @PostMapping("/conversations/{id}/messages") @ResponseStatus(HttpStatus.ACCEPTED) public Map<String,Object> send(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Map<String,Object> body){ChatData.keys(body,Set.of("requestId","message"));return chat.send(actor.id(),org,id,UUID.fromString(Objects.toString(body.get("requestId"),"")),(String)body.get("message"));}
 @PostMapping("/conversations/{id}/archive") public Map<String,Object> archive(@PathVariable UUID org,@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return chat.archive(actor.id(),org,id);}
 @PostMapping("/conversations/{id}/turns/{turn}/cancel") public Map<String,Object> cancel(@PathVariable UUID org,@PathVariable UUID id,@PathVariable UUID turn,@AuthenticationPrincipal AccountPrincipal actor){return chat.cancel(actor.id(),org,id,turn);}
}
