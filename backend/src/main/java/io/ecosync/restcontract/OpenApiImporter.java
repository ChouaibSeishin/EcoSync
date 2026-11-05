package io.ecosync.restcontract;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static io.ecosync.restcontract.RestJson.*;

/** JSON-only importer. All limits are independent of source descriptions and servers. */
public final class OpenApiImporter {
 public record Limits(int bytes,int paths,int operations,int referenceNodes){public Limits{if(bytes<128||bytes>1048576||paths<1||paths>128||operations<1||operations>256||referenceNodes<1||referenceNodes>50000)throw new IllegalArgumentException();}public static Limits defaults(){return new Limits(262144,64,128,20000);}}
 public record Operation(String operationId,String method,String path,boolean supported,String reason,List<Map<String,Object>> parameters,Map<String,Object> responses,List<String> authentication,String fingerprint){}
 public record Contract(String title,String version,String openapi,String fingerprint,List<Operation> operations){}
 private final Limits limits;
 public OpenApiImporter(){this(Limits.defaults());}public OpenApiImporter(Limits limits){this.limits=limits;}
 public Contract parse(String document){try{
  var raw=object(read(document.getBytes(StandardCharsets.UTF_8),limits.bytes));
  String version=Objects.toString(raw.get("openapi"),"");if(!version.matches("3\\.(0|1)\\.[0-9]{1,3}"))throw new RestFailure("REST_CONTRACT_INVALID");boolean v31=version.startsWith("3.1.");
  if(raw.containsKey("webhooks")||raw.containsKey("jsonSchemaDialect"))throw new RestFailure("REST_CONTRACT_UNSUPPORTED");
  var expanded=object(expand(raw,raw,new HashSet<>(),0,new int[]{0}));var info=object(expanded.get("info"));String title=label(info.get("title"),120),release=label(info.get("version"),100);
  var paths=object(expanded.get("paths"));if(paths.isEmpty()||paths.size()>limits.paths)throw new RestFailure("REST_LIMIT");var result=new ArrayList<Operation>();var ids=new HashSet<String>();var templates=new HashSet<String>();
  var securitySchemes=object(object(expanded.getOrDefault("components",Map.of())).getOrDefault("securitySchemes",Map.of()));
  for(String path:new TreeSet<>(paths.keySet())){
   if(!path.matches("/(?:[A-Za-z0-9._~-]+|\\{[A-Za-z_][A-Za-z0-9_]{0,63}\\})(?:/(?:[A-Za-z0-9._~-]+|\\{[A-Za-z_][A-Za-z0-9_]{0,63}\\}))*")||Arrays.stream(path.split("/")).anyMatch(s->s.equals(".")||s.equals("..")))throw new RestFailure("REST_CONTRACT_INVALID");
   if(!templates.add(path.replaceAll("\\{[^}]+}","{}")))throw new RestFailure("REST_CONTRACT_INVALID");var item=object(paths.get(path));
   for(String method:new TreeSet<>(item.keySet())){
    if(Set.of("parameters","summary","description","servers").contains(method)||method.startsWith("x-"))continue;
    if(!Set.of("get","post","put","patch","delete","head","options","trace").contains(method))throw new RestFailure("REST_CONTRACT_INVALID");
    if(result.size()>=limits.operations)throw new RestFailure("REST_LIMIT");var op=object(item.get(method));String id=op.containsKey("operationId")?label(op.get("operationId"),100):"get_"+hash(List.of(method,path)).substring(0,24);
    if(!id.matches("[A-Za-z_][A-Za-z0-9_.-]{0,99}")||!ids.add(id))throw new RestFailure("REST_OPERATION_ID_INVALID");
    var params=new ArrayList<Map<String,Object>>();var responses=new TreeMap<String,Object>();var auth=new ArrayList<String>();String reason="";
    try{
     if(!method.equals("get"))throw new RestFailure("REST_GET_ONLY");if(op.containsKey("requestBody"))throw new RestFailure("REST_REQUEST_BODY_UNSUPPORTED");
     if(op.containsKey("callbacks")||op.containsKey("servers")||item.containsKey("servers"))throw new RestFailure("REST_OPERATION_OVERRIDE_UNSUPPORTED");
     // Path-level parameters can be inherited; duplicate/override parameters are rejected as ambiguous in this subset.
     var inherited=new ArrayList<>(array(item.getOrDefault("parameters",List.of())));inherited.addAll(array(op.getOrDefault("parameters",List.of())));var names=new HashSet<String>();var pathNames=new HashSet<String>();
     if(inherited.size()>16)throw new RestFailure("REST_LIMIT");
     for(Object input:inherited){var p=object(input);String name=label(p.get("name"),64),in=Objects.toString(p.get("in"),"");
      if(!name.matches("[A-Za-z_][A-Za-z0-9_-]{0,63}")||Set.of("__proto__","constructor","prototype").contains(name)||!names.add(name))throw new RestFailure("REST_PARAMETER_INVALID");
      if(!Set.of("path","query").contains(in)||p.containsKey("content")||Boolean.TRUE.equals(p.get("allowReserved"))||Boolean.TRUE.equals(p.get("allowEmptyValue")))throw new RestFailure("REST_SERIALIZATION_UNSUPPORTED");
      if(p.containsKey("required")&&!(p.get("required") instanceof Boolean)||p.containsKey("explode")&&!(p.get("explode") instanceof Boolean))throw new RestFailure("REST_PARAMETER_INVALID");
      if(!Objects.equals(p.getOrDefault("style",in.equals("path")?"simple":"form"),in.equals("path")?"simple":"form"))throw new RestFailure("REST_SERIALIZATION_UNSUPPORTED");
      var schema=RestSchema.normalize(p.get("schema"),v31);if(!Set.of("string","integer","number","boolean").contains(schema.get("type"))||Boolean.TRUE.equals(schema.get("nullable")))throw new RestFailure("REST_PARAMETER_INVALID");
      if(in.equals("path")){if(!Boolean.TRUE.equals(p.get("required"))||!path.contains("{"+name+"}"))throw new RestFailure("REST_PARAMETER_INVALID");pathNames.add(name);}
      params.add(Map.of("name",name,"in",in,"required",Boolean.TRUE.equals(p.get("required")),"schema",schema));
     }
     var matcher=java.util.regex.Pattern.compile("\\{([^}]+)}").matcher(path);var placeholders=new HashSet<String>();while(matcher.find())if(!placeholders.add(matcher.group(1)))throw new RestFailure("REST_PARAMETER_INVALID");if(!pathNames.equals(placeholders))throw new RestFailure("REST_PARAMETER_INVALID");
     for(var response:object(op.get("responses")).entrySet())if(response.getKey().matches("2[0-9]{2}")){
      var content=object(object(response.getValue()).get("content"));if(content.size()!=1||!content.containsKey("application/json"))throw new RestFailure("REST_RESPONSE_FORMAT_UNSUPPORTED");responses.put(response.getKey(),RestSchema.normalize(object(content.get("application/json")).get("schema"),v31));
     }
     if(responses.isEmpty()||responses.size()>8)throw new RestFailure("REST_RESPONSE_FORMAT_UNSUPPORTED");
     var requirements=array(op.getOrDefault("security",expanded.getOrDefault("security",List.of())));
     if(requirements.isEmpty())auth.add("NONE");else for(Object requirement:requirements){var r=object(requirement);if(r.isEmpty()){auth.add("NONE");continue;}if(r.size()!=1)throw new RestFailure("REST_AUTH_UNSUPPORTED");String key=r.keySet().iterator().next();if(!array(r.get(key)).isEmpty())throw new RestFailure("REST_AUTH_UNSUPPORTED");var scheme=object(securitySchemes.get(key));
      if("http".equals(scheme.get("type"))&&"bearer".equals(scheme.get("scheme")))auth.add("BEARER");else if("apiKey".equals(scheme.get("type"))&&"header".equals(scheme.get("in"))){String header=label(scheme.get("name"),64);RestAuth.header(header);auth.add("HEADER_API_KEY:"+header.toLowerCase(Locale.ROOT));}else throw new RestFailure("REST_AUTH_UNSUPPORTED");
     }
    }catch(RestFailure e){reason=e.code;}catch(Exception e){reason="REST_CONTRACT_UNSUPPORTED";}
    boolean supported=reason.isEmpty();String fingerprint=hash(List.of(id,method,path,supported,reason,params,responses,auth));result.add(new Operation(id,method.toUpperCase(Locale.ROOT),path,supported,reason,List.copyOf(params),Map.copyOf(responses),List.copyOf(auth),fingerprint));
   }
  }
  if(result.isEmpty())throw new RestFailure("REST_CONTRACT_INVALID");return new Contract(title,release,version,hash(raw),List.copyOf(result));
 }catch(RestFailure e){throw e;}catch(Exception e){throw new RestFailure("REST_CONTRACT_INVALID");}}
 private Object expand(Object value,Map<String,Object> root,Set<String> stack,int depth,int[] nodes){if(depth>24||++nodes[0]>limits.referenceNodes)throw new RestFailure("REST_LIMIT");
  if(value instanceof Map<?,?> raw){var m=object(raw);if(m.containsKey("$ref")){String ref=Objects.toString(m.get("$ref"),"");if(!ref.startsWith("#/")||ref.contains("%")||m.size()!=1)throw new RestFailure("REST_REFERENCE_DENIED");if(!stack.add(ref))throw new RestFailure("REST_REFERENCE_CIRCULAR");Object target=root;for(String part:ref.substring(2).split("/",-1)){if(part.matches(".*~(?:[^01]|$).*"))throw new RestFailure("REST_REFERENCE_INVALID");String key=part.replace("~1","/").replace("~0","~");if(!(target instanceof Map<?,?> map)||!map.containsKey(key))throw new RestFailure("REST_REFERENCE_UNRESOLVED");target=map.get(key);}Object out=expand(target,root,stack,depth+1,nodes);stack.remove(ref);return out;}
   var out=new LinkedHashMap<String,Object>();for(var e:m.entrySet())out.put(e.getKey(),expand(e.getValue(),root,stack,depth+1,nodes));return out;
  }if(value instanceof List<?> l){var out=new ArrayList<>();for(Object child:l)out.add(expand(child,root,stack,depth+1,nodes));return out;}return value;
 }
 private static String label(Object value,int max){if(!(value instanceof String s)||s.isBlank()||s.length()>max||s.chars().anyMatch(Character::isISOControl))throw new RestFailure("REST_CONTRACT_INVALID");return s;}
}
