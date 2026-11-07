package io.ecosync.chat;
import java.util.*;
/** Only cited evidence and its actual derivation inputs can authorize a relationship. */
public final class EvidenceGrounding {
 private EvidenceGrounding(){}
 public static List<ChatEvidence> selected(List<Map<String,Object>> references,List<ChatEvidence> evidence){var chosen=new LinkedHashMap<String,ChatEvidence>();for(var ref:references)collect(Objects.toString(ref.get("evidence")),evidence,chosen,new HashSet<>(),0);return List.copyOf(chosen.values());}
 private static void collect(String id,List<ChatEvidence> evidence,Map<String,ChatEvidence> chosen,Set<String> visiting,int depth){if(depth>8||!visiting.add(id))throw new ChatFailure("CHAT_INVALID_REFERENCE");var value=evidence.stream().filter(e->e.id().equals(id)&&e.status().equals("SUCCESS")).findFirst().orElseThrow(()->new ChatFailure("CHAT_INVALID_REFERENCE"));if(!value.capability().kind().equals("DETERMINISTIC"))chosen.put(id,value);else if(value.data() instanceof Map<?,?> data){if(data.get("inputEvidence") instanceof String input)collect(input,evidence,chosen,visiting,depth+1);if(data.get("inputs") instanceof List<?> inputs)for(Object raw:inputs){if(!(raw instanceof Map<?,?> ref)||!(ref.get("evidence") instanceof String input))throw new ChatFailure("CHAT_INVALID_REFERENCE");collect(input,evidence,chosen,visiting,depth+1);}}visiting.remove(id);}
 public static List<ChatEvidence> withMappings(List<ChatEvidence> selected,List<ChatEvidence> all){var result=new ArrayList<>(selected);all.stream().filter(e->e.capability().kind().equals("DETERMINISTIC")&&e.capability().name().equals("approved_mapping")).forEach(result::add);return result;}
}
