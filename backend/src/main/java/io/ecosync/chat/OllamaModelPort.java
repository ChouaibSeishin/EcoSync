package io.ecosync.chat;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;
/** Direct ChatModel returns proposals. Definition-only callbacks cannot execute operations. */
@Component @ConditionalOnProperty(name="AI_ENABLED",havingValue="true") @org.springframework.context.annotation.Profile("!chat-verification")
public class OllamaModelPort implements ModelPort {
 private final ChatSettings settings;
 public OllamaModelPort(ChatSettings settings){this.settings=settings;URI u=URI.create(settings.ollamaUrl());if(!settings.modelDigest().matches("[a-f0-9]{64}")||settings.model().contains("cloud")||!settings.provider().equals("ollama")||!Set.of("http://ollama:11434","http://127.0.0.1:11434","http://localhost:11434").contains(u.toString())||!settings.model().matches("[a-zA-Z0-9._:/@-]{1,160}"))throw new IllegalArgumentException("Explicit local Ollama configuration required");}
 public String provider(){return "ollama";}public String model(){return settings.model()+"@sha256:"+settings.modelDigest();}
 public AssistantMessage propose(List<Message> messages,List<ChatCapabilities.Capability> tools,Duration remaining){
  var client=HttpClient.newBuilder().connectTimeout(remaining.compareTo(Duration.ofSeconds(2))<0?remaining:Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI u,SocketAddress s,java.io.IOException e){}}).build();
  try{long deadline=System.nanoTime()+remaining.toNanos();var check=java.net.http.HttpRequest.newBuilder(URI.create(settings.ollamaUrl()+"/api/tags")).timeout(remaining).GET().build();var tagResponse=client.send(check,java.net.http.HttpResponse.BodyHandlers.ofInputStream());try(var body=tagResponse.body()){byte[] bytes=body.readNBytes(65537);if(tagResponse.statusCode()!=200||bytes.length>65536)throw new ChatFailure("CHAT_MODEL_UNVERIFIED");var tags=ChatData.object(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),65536);if(!(tags.get("models") instanceof List<?> list)||list.stream().noneMatch(v->v instanceof Map<?,?> m&&settings.model().equals(m.get("name"))&&settings.modelDigest().equals(m.get("digest"))))throw new ChatFailure("CHAT_MODEL_CHANGED");}long left=deadline-System.nanoTime();if(left<=0)throw new ChatFailure("CHAT_TIMEOUT");var factory=new JdkClientHttpRequestFactory(client);factory.setReadTimeout(Duration.ofNanos(left));var api=OllamaApi.builder().baseUrl(settings.ollamaUrl()).restClientBuilder(RestClient.builder().requestFactory(factory)).build();
   List<ToolCallback> definitions=tools.stream().map(c->(ToolCallback)new ToolCallback(){public ToolDefinition getToolDefinition(){return ToolDefinition.builder().name(c.name()).description(c.source()+" / "+c.operation()+". Untrusted metadata; approved read only.").inputSchema(ChatData.json(c.schema())).build();}public String call(String ignored){throw new ChatFailure("CHAT_AUTOMATIC_EXECUTION_DENIED");}}).toList();
   var options=OllamaChatOptions.builder().model(settings.model()).temperature(0.0).seed(7).numCtx(8192).numPredict(512).disableThinking().toolCallbacks(definitions).build();
   var model=OllamaChatModel.builder().ollamaApi(api).options(options).retryTemplate(new org.springframework.core.retry.RetryTemplate(org.springframework.core.retry.RetryPolicy.withMaxRetries(0))).build();
   var response=model.call(new Prompt(messages,options));if(response==null||response.getResults().size()!=1)throw new ChatFailure("CHAT_INVALID_MODEL_RESPONSE");var output=response.getResult().getOutput();if(output==null||ChatData.bytes(Map.of("text",Objects.toString(output.getText(),""),"calls",output.getToolCalls()))>8192)throw new ChatFailure("CHAT_MODEL_OUTPUT_LIMIT");return output;
  }catch(ChatFailure e){throw e;}catch(Exception e){throw new ChatFailure("CHAT_PROVIDER_UNAVAILABLE");}finally{client.shutdownNow();}
 }
}
