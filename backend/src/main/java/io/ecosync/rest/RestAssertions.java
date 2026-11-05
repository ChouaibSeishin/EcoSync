package io.ecosync.rest;
import io.ecosync.restcontract.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component @ConditionalOnProperty(name="ecosync.rest.enabled",havingValue="true")
public class RestAssertions {
 private final JsonMapper json;private final PrivateKey privateKey;private final PublicKey publicKey;
 public RestAssertions(JsonMapper json,@Value("${ecosync.rest.private-key:}")String privatePath,@Value("${ecosync.rest.public-key:}")String publicPath){
  this.json=json;try{var f=KeyFactory.getInstance("Ed25519");privateKey=f.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(privatePath))));publicKey=f.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(publicPath))));}catch(Exception e){throw new IllegalStateException("Rest delegation keys unavailable");}
 }
 public String issue(UUID actor,UUID org,UUID connector,long revision,String action,UUID execution,UUID operation,String hash){
  long now=Instant.now().getEpochSecond();var c=new TreeMap<String,Object>();c.put("iss","ecosync-platform");c.put("aud","ecosync-rest-gateway");c.put("sub",actor.toString());c.put("org",org.toString());c.put("connector",connector.toString());c.put("revision",revision);c.put("action",action);c.put("execution",execution.toString());c.put("operation",operation==null?"":operation.toString());c.put("hash",hash);c.put("iat",now);c.put("exp",now+30);c.put("jti",UUID.randomUUID().toString());
  try{String p=Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(Map.of("alg","EdDSA","typ","JWT")))+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(c));var s=Signature.getInstance("Ed25519");s.initSign(privateKey);s.update(p.getBytes(StandardCharsets.US_ASCII));return p+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());}catch(Exception e){throw new RestFailure("REST_UNAVAILABLE");}
 }
 public Map<String,Object> verify(String value){try{
  if(value==null||value.length()>4096)throw new Exception();String[] p=value.split("\\.",-1);if(p.length!=3)throw new Exception();var d=Base64.getUrlDecoder();var h=json.readTree(d.decode(p[0]));if(!h.path("alg").asString().equals("EdDSA")||!h.path("typ").asString().equals("JWT"))throw new Exception();
  var s=Signature.getInstance("Ed25519");s.initVerify(publicKey);s.update((p[0]+"."+p[1]).getBytes(StandardCharsets.US_ASCII));if(!s.verify(d.decode(p[2])))throw new Exception();var c=json.readTree(d.decode(p[1]));long now=Instant.now().getEpochSecond();
  if(!c.path("iss").asString().equals("ecosync-platform")||!c.path("aud").asString().equals("ecosync-rest-gateway")||c.path("exp").asLong()<=now||c.path("iat").asLong()>now+2||c.path("exp").asLong()<=c.path("iat").asLong()||c.path("exp").asLong()-c.path("iat").asLong()>30||c.path("revision").asLong()<1)throw new Exception();
  for(String field:List.of("sub","org","connector","execution","jti"))UUID.fromString(c.path(field).asString());
  if(!Set.of("TEST","LIST","INVOKE","DESCRIBE").contains(c.path("action").asString()))throw new Exception();return json.convertValue(c,Map.class);
 }catch(Exception e){throw new RestFailure("REST_ACCESS_DENIED");}}
 public String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(value))));}catch(Exception e){throw new IllegalArgumentException();}}
 private Object canonical(Object value){if(value instanceof Map<?,?> m){var result=new TreeMap<String,Object>();m.forEach((k,v)->result.put(k.toString(),canonical(v)));return result;}if(value instanceof List<?> l)return l.stream().map(this::canonical).toList();return value;}
}
