package io.ecosync.restcontract;

import java.security.MessageDigest;
import java.util.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/** Shared by authority and isolated gateway; no network/reference resolution. */
public final class RestJson {
 public static final JsonMapper MAPPER=JsonMapper.builder(JsonFactory.builder()
  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(262144).maxNumberLength(64).build()).build()).build();
 private RestJson(){}
 public static Object read(byte[] bytes,int maximum){try{
  if(bytes.length>maximum)throw new RestFailure("REST_LIMIT");
  try(var parser=MAPPER.createParser(bytes)){Object value=MAPPER.readValue(parser,Object.class);if(parser.nextToken()!=null)throw new IllegalArgumentException();bounds(value,0,new int[]{0});return value;}
 }catch(RestFailure e){throw e;}catch(Exception e){throw new RestFailure("REST_INVALID_JSON");}}
 public static void bounds(Object value,int depth,int[] nodes){if(depth>24||++nodes[0]>20000)throw new RestFailure("REST_LIMIT");if(value instanceof Map<?,?> m){if(m.size()>256)throw new RestFailure("REST_LIMIT");m.values().forEach(v->bounds(v,depth+1,nodes));}else if(value instanceof List<?> l){if(l.size()>1000)throw new RestFailure("REST_LIMIT");l.forEach(v->bounds(v,depth+1,nodes));}}
 public static Map<String,Object> object(Object value){if(!(value instanceof Map<?,?>))throw new RestFailure("REST_CONTRACT_INVALID");return (Map<String,Object>)value;}
 public static List<Object> array(Object value){if(!(value instanceof List<?>))throw new RestFailure("REST_CONTRACT_INVALID");return (List<Object>)value;}
 public static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(canonical(value))));}catch(Exception e){throw new RestFailure("REST_CONTRACT_INVALID");}}
 private static Object canonical(Object value){if(value instanceof Map<?,?> m){var out=new TreeMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),canonical(v)));return out;}if(value instanceof List<?> l)return l.stream().map(RestJson::canonical).toList();return value;}
}
