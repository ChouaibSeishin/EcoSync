package io.ecosync.chat;
import java.util.*;
import java.time.Duration;
import org.springframework.ai.chat.messages.*;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** Explicit verification-only scripted proposals. Never used or described as model reasoning. */
@Component @Profile("chat-verification") @ConditionalOnProperty(name="AI_ENABLED",havingValue="true")
public class VerificationModelPort implements ModelPort {
 public VerificationModelPort(ChatSettings settings){if(!settings.provider().equals("verification"))throw new IllegalArgumentException("Verification adapter requires explicit verification provider");}
 public String provider(){return "verification";}public String model(){return "deterministic-test-double";}public boolean testDouble(){return true;}
 @SuppressWarnings("unchecked") public AssistantMessage propose(List<Message> messages,List<ChatCapabilities.Capability> catalog,Duration remaining){int last=-1;for(int n=0;n<messages.size();n++)if(messages.get(n) instanceof UserMessage)last=n;if(last<0)throw new ChatFailure("CHAT_INVALID_MODEL_RESPONSE");String question=messages.get(last).getText();int rounds=0;for(int n=last+1;n<messages.size();n++)if(messages.get(n) instanceof ToolResponseMessage)rounds++;
  if(question.startsWith("VERIFY ")){var plan=ChatData.object(question.substring(7),8192);ChatData.keys(plan,Set.of("rounds","answer","delayMs","repeat"));if(plan.get("delayMs") instanceof Number delay)try{Thread.sleep(Math.max(0,Math.min(35000,delay.longValue())));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ChatFailure("CHAT_CANCELLED");}if(!(plan.get("rounds") instanceof List<?> scripted)||scripted.size()>9)throw new ChatFailure("CHAT_INVALID_MODEL_RESPONSE");int index=Boolean.TRUE.equals(plan.get("repeat"))?0:rounds;if(index<scripted.size()){if(!(scripted.get(index) instanceof List<?> items)||items.size()>9)throw new ChatFailure("CHAT_INVALID_MODEL_RESPONSE");var calls=new ArrayList<AssistantMessage.ToolCall>();for(Object item:items){if(!(item instanceof Map<?,?> c))throw new ChatFailure("CHAT_INVALID_MODEL_RESPONSE");calls.add(new AssistantMessage.ToolCall(UUID.randomUUID().toString(),"function",Objects.toString(c.get("tool")),ChatData.json(c.get("arguments"))));}return AssistantMessage.builder().content("").toolCalls(calls).build();}return new AssistantMessage(ChatData.json(plan.getOrDefault("answer",Map.of("status","CLARIFICATION_REQUIRED","references",List.of(),"question","Which identifier and period do you mean?"))));}
  // A single named UI fixture plan, with no expected business answer embedded.
  if(question.toLowerCase(Locale.ROOT).contains("partner")){if(rounds==0){var cap=catalog.stream().filter(c->c.kind().equals("REST")&&c.operation().startsWith("directorySearch ")).findFirst().orElseThrow(()->new ChatFailure("CHAT_UNKNOWN_TOOL"));return AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall(UUID.randomUUID().toString(),"function",cap.name(),"{\"limit\":1}"))).build();}return new AssistantMessage("{\"status\":\"COMPLETE\",\"references\":[{\"evidence\":\"e1\",\"pointer\":\"/records/0/name\"}]}");}
  return new AssistantMessage("{\"status\":\"CLARIFICATION_REQUIRED\",\"references\":[],\"question\":\"This is a verification adapter, not an LLM. Which explicit verification scenario should run?\"}");
 }
}
