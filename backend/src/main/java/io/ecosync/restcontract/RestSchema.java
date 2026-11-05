package io.ecosync.restcontract;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static io.ecosync.restcontract.RestJson.*;

/** Explicit finite JSON Schema subset. Unknown assertions are rejected, never ignored. */
public final class RestSchema {
 private static final Set<String> KEYS=Set.of("type","properties","required","additionalProperties","items","enum","minimum","maximum","minLength","maxLength","minItems","maxItems","nullable","pattern","format","description","title","default","example","examples","readOnly","writeOnly","deprecated");
 private RestSchema(){}
 public static Map<String,Object> normalize(Object input,boolean version31){return normalize(input,version31,0,new int[]{0});}
 private static Map<String,Object> normalize(Object input,boolean v31,int depth,int[] nodes){
  var s=object(input);if(depth>8||++nodes[0]>256||!KEYS.containsAll(s.keySet()))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");
  var out=new LinkedHashMap<String,Object>();Object type=s.get("type");boolean nullable=Boolean.TRUE.equals(s.get("nullable"));
  if(s.containsKey("nullable")&&(v31||!(s.get("nullable") instanceof Boolean)))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");
  if(type instanceof List<?> types){if(!v31||types.size()!=2||!types.contains("null")||types.stream().distinct().count()!=2)throw new RestFailure("REST_SCHEMA_UNSUPPORTED");type=types.stream().filter(t->!t.equals("null")).findFirst().orElseThrow();nullable=true;}
  if(!(type instanceof String t)||!Set.of("object","array","string","integer","number","boolean").contains(t))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");out.put("type",type);out.put("nullable",nullable);
  for(String key:List.of("description","title"))if(s.containsKey(key)){if(!(s.get(key) instanceof String text)||text.length()>1000)throw new RestFailure("REST_SCHEMA_UNSUPPORTED");out.put(key,text);}
  if(!type.equals("string")&&s.keySet().stream().anyMatch(Set.of("pattern","minLength","maxLength")::contains)||!Set.of("integer","number").contains(type)&&s.keySet().stream().anyMatch(Set.of("minimum","maximum")::contains)||!type.equals("array")&&s.keySet().stream().anyMatch(Set.of("items","minItems","maxItems")::contains)||nullable&&s.containsKey("enum"))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");
  if(s.containsKey("enum")){var values=array(s.get("enum"));if(values.isEmpty()||values.size()>64||values.stream().anyMatch(v->v instanceof Map||v instanceof List)||new HashSet<>(values).size()!=values.size())throw new RestFailure("REST_SCHEMA_UNSUPPORTED");out.put("enum",List.copyOf(values));}
  for(String key:List.of("minimum","maximum","minLength","maxLength","minItems","maxItems"))if(s.containsKey(key)){if(!(s.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||Set.of("minLength","maxLength","minItems","maxItems").contains(key)&&(n.doubleValue()!=n.intValue()||n.intValue()<0))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");out.put(key,s.get(key));}
  for(String[] pair:List.of(new String[]{"minimum","maximum"},new String[]{"minLength","maxLength"},new String[]{"minItems","maxItems"}))if(out.containsKey(pair[0])&&out.containsKey(pair[1])&&decimal(out.get(pair[0])).compareTo(decimal(out.get(pair[1])))>0)throw new RestFailure("REST_SCHEMA_UNSUPPORTED");
  if(s.containsKey("pattern")){String p=Objects.toString(s.get("pattern"),"");if(!safePattern(p))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");Pattern.compile(p);out.put("pattern",p);}
  if(s.containsKey("format")){if(!Set.of("date-time","date","uuid","int32","int64","double","float").contains(s.get("format")))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");out.put("format",s.get("format"));}
  if(type.equals("object")){
   var props=object(s.getOrDefault("properties",Map.of()));if(props.size()>64)throw new RestFailure("REST_SCHEMA_UNSUPPORTED");var normalized=new LinkedHashMap<String,Object>();
   for(var e:props.entrySet()){if(!e.getKey().matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}")||Set.of("__proto__","constructor","prototype").contains(e.getKey()))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");normalized.put(e.getKey(),normalize(e.getValue(),v31,depth+1,nodes));}
   var required=array(s.getOrDefault("required",List.of()));if(new HashSet<>(required).size()!=required.size()||!props.keySet().containsAll(required))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");
   if(s.containsKey("additionalProperties")&&!(s.get("additionalProperties") instanceof Boolean))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");out.put("properties",normalized);out.put("required",required);out.put("additionalProperties",s.getOrDefault("additionalProperties",true));
  }else if(type.equals("array")){out.put("items",normalize(s.get("items"),v31,depth+1,nodes));}
  else if(s.containsKey("properties")||s.containsKey("items")||s.containsKey("additionalProperties")||s.containsKey("required"))throw new RestFailure("REST_SCHEMA_UNSUPPORTED");
  if(s.containsKey("default")){validate(out,s.get("default"),"REST_SCHEMA_UNSUPPORTED");out.put("default",s.get("default"));}
  return out;
 }
 // No user-supplied unbounded repetition, lookaround, backreference or nested grouping.
 static boolean safePattern(String p){if(p.equals("^(0|[1-9][0-9]{0,8})[.][0-9]{2}$"))return true;if(p.length()>160||!p.startsWith("^")||!p.endsWith("$"))return false;String body=p.substring(1,p.length()-1);return body.matches("(?:[A-Za-z0-9 _:-]|\\[(?:[A-Za-z0-9 ._:-]|-)+\\](?:\\{[0-9]{1,3}\\})?)+");}
 public static void validate(Map<String,Object> s,Object value,String code){try{check(s,value,0,new int[]{0});}catch(Exception e){throw new RestFailure(code);}}
 private static void untyped(Object value,int depth,int[] count){if(depth>16||++count[0]>10000)throw new IllegalArgumentException();if(value instanceof String text&&text.length()>8192)throw new IllegalArgumentException();if(value instanceof Map<?,?> map){if(map.size()>64)throw new IllegalArgumentException();map.values().forEach(v->untyped(v,depth+1,count));}else if(value instanceof List<?> list){if(list.size()>1000)throw new IllegalArgumentException();list.forEach(v->untyped(v,depth+1,count));}}
 private static BigDecimal decimal(Object v){return new BigDecimal(v.toString());}
 private static void check(Map<String,Object> s,Object v,int depth,int[] count){if(depth>16||++count[0]>10000)throw new IllegalArgumentException();if(v==null){if(!Boolean.TRUE.equals(s.get("nullable")))throw new IllegalArgumentException();return;}
  String type=(String)s.get("type");switch(type){
   case "object" -> {var m=object(v);var props=object(s.get("properties"));if(m.size()>64||!m.keySet().containsAll(array(s.get("required"))))throw new IllegalArgumentException();for(var e:m.entrySet()){if(props.containsKey(e.getKey()))check(object(props.get(e.getKey())),e.getValue(),depth+1,count);else if(Boolean.FALSE.equals(s.get("additionalProperties")))throw new IllegalArgumentException();else untyped(e.getValue(),depth+1,count);}}
   case "array" -> {var l=array(v);if(l.size()>1000||l.size()<((Number)s.getOrDefault("minItems",0)).intValue()||l.size()>((Number)s.getOrDefault("maxItems",1000)).intValue())throw new IllegalArgumentException();for(Object item:l)check(object(s.get("items")),item,depth+1,count);}
   case "string" -> {if(!(v instanceof String text)||text.length()>8192||text.length()<((Number)s.getOrDefault("minLength",0)).intValue()||text.length()>((Number)s.getOrDefault("maxLength",8192)).intValue())throw new IllegalArgumentException();if(s.containsKey("pattern")&&!Pattern.matches((String)s.get("pattern"),text))throw new IllegalArgumentException();switch(Objects.toString(s.get("format"),"")){case "date-time" -> OffsetDateTime.parse(text);case "date" -> LocalDate.parse(text);case "uuid" -> UUID.fromString(text);default -> {}}}
   case "integer","number" -> {if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue())||type.equals("integer")&&decimal(v).stripTrailingZeros().scale()>0||s.containsKey("minimum")&&decimal(v).compareTo(decimal(s.get("minimum")))<0||s.containsKey("maximum")&&decimal(v).compareTo(decimal(s.get("maximum")))>0)throw new IllegalArgumentException();}
   case "boolean" -> {if(!(v instanceof Boolean))throw new IllegalArgumentException();}
   default -> throw new IllegalArgumentException();
  }
  if(s.get("enum") instanceof List<?> allowed&&allowed.stream().noneMatch(x->x instanceof Number&&v instanceof Number?decimal(x).compareTo(decimal(v))==0:Objects.equals(x,v)))throw new IllegalArgumentException();
 }
}
