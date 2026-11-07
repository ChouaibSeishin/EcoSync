package io.ecosync.chat;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
public record ChatSettings(boolean enabled,String provider,String model,String ollamaUrl,int seconds,int calls,int rounds,int contextBytes,int evidenceBytes,String bindings,String modelDigest) {
 public ChatSettings(boolean enabled,String provider,String model,String ollamaUrl,int seconds,int calls,int rounds,int contextBytes,int evidenceBytes,String bindings){this(enabled,provider,model,ollamaUrl,seconds,calls,rounds,contextBytes,evidenceBytes,bindings,"");}
 @org.springframework.beans.factory.annotation.Autowired public ChatSettings(Environment env){this(Boolean.parseBoolean(env.getProperty("AI_ENABLED","false")),env.getProperty("AI_PROVIDER","ollama"),env.getProperty("AI_MODEL",""),env.getProperty("AI_OLLAMA_URL","http://ollama:11434"),number(env,"AI_QUERY_SECONDS",30,1,30),number(env,"AI_TOOL_CALLS",8,1,8),number(env,"AI_ROUNDS",6,1,9),number(env,"AI_CONTEXT_BYTES",65536,4096,65536),number(env,"AI_EVIDENCE_BYTES",16384,1024,16384),env.getProperty("AI_SOURCE_BINDINGS",""),env.getProperty("AI_MODEL_DIGEST",""));}
 private static int number(Environment e,String key,int fallback,int min,int max){int n=Integer.parseInt(e.getProperty(key,Integer.toString(fallback)));if(n<min||n>max)throw new IllegalArgumentException("Invalid operator chat limit");return n;}
}
