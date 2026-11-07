package io.ecosync.chat;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.*;
public interface ModelPort {
 AssistantMessage propose(List<Message> messages,List<ChatCapabilities.Capability> tools,Duration remaining);
 String provider();String model();
 default boolean testDouble(){return false;}
}
