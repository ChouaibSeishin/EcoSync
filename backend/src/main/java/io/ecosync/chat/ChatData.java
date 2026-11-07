package io.ecosync.chat;
import java.util.*;
import java.nio.charset.StandardCharsets;
import io.ecosync.restcontract.RestJson;
/** Strict JSON, minimization and output bounds apply before any model context. */
public final class ChatData {
 private ChatData(){}
 public static Map<String,Object> object(String text,int limit){try{return RestJson.object(RestJson.read(text.getBytes(StandardCharsets.UTF_8),limit));}catch(Exception e){throw new ChatFailure("CHAT_INVALID_JSON");}}
 public static String json(Object value){return RestJson.MAPPER.writeValueAsString(value);}
 public static String hash(Object value){return RestJson.hash(value);}
 public static int bytes(Object value){return json(value).getBytes(StandardCharsets.UTF_8).length;}
 public static Object clean(Object value){return clean(value,0,new int[]{0});}
 private static Object clean(Object value,int depth,int[] count){if(depth>12||++count[0]>2000)throw new ChatFailure("CHAT_EVIDENCE_LIMIT");if(value instanceof Map<?,?> m){if(m.size()>64)throw new ChatFailure("CHAT_EVIDENCE_LIMIT");var out=new LinkedHashMap<String,Object>();for(var e:m.entrySet()){String k=e.getKey().toString();if(k.toLowerCase(Locale.ROOT).matches(".*(password|secret|token|authorization|credential|private.?key|cookie|api.?key).*"))continue;out.put(k,clean(e.getValue(),depth+1,count));}return out;}if(value instanceof List<?> l){if(l.size()>100)throw new ChatFailure("CHAT_EVIDENCE_LIMIT");return l.stream().map(v->clean(v,depth+1,count)).toList();}if(value instanceof String s){if(s.length()>4096)throw new ChatFailure("CHAT_EVIDENCE_LIMIT");if(s.matches("(?s).*(?:-----BEGIN |Bearer [A-Za-z0-9._~-]{8,}|eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+\\.).*"))return "[REDACTED]";return s;}if(value==null||value instanceof Number||value instanceof Boolean)return value;return value.toString();}
 public static Object pointer(Object value,String pointer){if(pointer==null||pointer.length()>256||!pointer.matches("(?:/(?:[^~/]|~[01])*){0,12}"))throw new ChatFailure("CHAT_INVALID_REFERENCE");Object current=value;for(String part:pointer.isEmpty()?new String[0]:pointer.substring(1).split("/",-1)){String key=part.replace("~1","/").replace("~0","~");if(current instanceof Map<?,?> m&&m.containsKey(key))current=m.get(key);else if(current instanceof List<?> l&&key.matches("0|[1-9][0-9]{0,2}")&&Integer.parseInt(key)<l.size())current=l.get(Integer.parseInt(key));else throw new ChatFailure("CHAT_INVALID_REFERENCE");}return current;}
 public static void keys(Map<String,Object> map,Set<String> allowed){if(!allowed.containsAll(map.keySet()))throw new ChatFailure("CHAT_INVALID_ARGUMENTS");}
}
