package io.ecosync.gateway;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
public final class Delegation {
 private final JsonMapper json;private final PublicKey publicKey;
 public Delegation(JsonMapper json,String publicPath){this.json=json;try{publicKey=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(publicPath))));}catch(Exception e){throw new IllegalStateException("Gateway public key unavailable");}}
 public Map<String,Object> verify(String value){try{
  if(value==null||value.length()>4096)throw new Exception();String[] p=value.split("\\.");if(p.length!=3)throw new Exception();var d=Base64.getUrlDecoder();var h=json.readTree(d.decode(p[0]));if(!h.path("alg").asString().equals("EdDSA")||!h.path("typ").asString().equals("JWT"))throw new Exception();var s=Signature.getInstance("Ed25519");s.initVerify(publicKey);s.update((p[0]+"."+p[1]).getBytes(StandardCharsets.US_ASCII));if(!s.verify(d.decode(p[2])))throw new Exception();var c=json.readTree(d.decode(p[1]));long now=Instant.now().getEpochSecond();
  if(!c.path("iss").asString().equals("ecosync-platform")||!c.path("aud").asString().equals("ecosync-db-gateway")||c.path("exp").asLong()<=now||c.path("iat").asLong()>now+2||c.path("exp").asLong()-c.path("iat").asLong()>30||c.path("revision").asLong()<1)throw new Exception();for(String field:List.of("sub","org","connector","execution","jti"))UUID.fromString(c.path(field).asString());if(!Set.of("INSPECT","LIST","QUERY","DESCRIBE").contains(c.path("action").asString()))throw new Exception();return json.convertValue(c,Map.class);
 }catch(Exception e){throw new GatewayFailure("DB_ACCESS_DENIED");}}
 public String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(value))));}catch(Exception e){throw new GatewayFailure("DB_INVALID_QUERY");}}
 private Object canonical(Object value){if(value instanceof Map<?,?> m){var r=new TreeMap<String,Object>();m.forEach((k,v)->r.put(k.toString(),canonical(v)));return r;}if(value instanceof List<?> l)return l.stream().map(this::canonical).toList();return value;}
}
