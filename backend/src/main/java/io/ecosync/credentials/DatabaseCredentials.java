package io.ecosync.credentials;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Same external keyring as native MCP, distinct purpose/AAD and ciphertext table. Backend only. */
@Service @ConditionalOnProperty(name="ecosync.database.enabled",havingValue="true")
public class DatabaseCredentials {
 public record Secret(String username,String password){@Override public String toString(){return "DatabaseSecret[REDACTED]";}}
 private final JdbcTemplate db;private final JsonMapper json;private final String active;private final Map<String,byte[]> keys=new HashMap<>();
 public DatabaseCredentials(JdbcTemplate db,JsonMapper json,@Value("${ecosync.database.keyring:}")String path){
  this.db=db;this.json=json;
  try{byte[] bytes=Files.readAllBytes(Path.of(path));if(bytes.length>16384)throw new Exception();var tree=json.readTree(bytes);active=tree.path("activeKeyId").asString();
   for(var e:tree.path("keys").properties()){byte[] k=Base64.getDecoder().decode(e.getValue().asString());if(!e.getKey().matches("[A-Za-z0-9_-]{1,64}")||k.length!=32)throw new Exception();keys.put(e.getKey(),k);}
   if(!keys.containsKey(active)||keys.size()>16)throw new Exception();
  }catch(Exception e){throw new IllegalStateException("Database keyring unavailable");}
 }
 public UUID store(UUID org,Secret secret){
  if(secret==null||secret.username()==null||!secret.username().matches("[A-Za-z_][A-Za-z0-9_]{0,63}")||secret.password()==null||secret.password().isEmpty()||secret.password().length()>512||secret.password().indexOf(0)>=0)throw new IllegalArgumentException();
  UUID id=UUID.randomUUID();byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
  byte[] cipher=crypt(Cipher.ENCRYPT_MODE,org,id,active,nonce,json.writeValueAsBytes(secret));
  db.update("INSERT INTO app.database_credential(organization_id,id,key_id,nonce,ciphertext) VALUES(?,?,?,?,?)",org,id,active,nonce,cipher);return id;
 }
 public Secret resolve(UUID org,UUID id){
  var values=db.query("SELECT key_id,nonce,ciphertext FROM app.database_credential WHERE organization_id=? AND id=? AND NOT revoked",(r,n)->json.readValue(crypt(Cipher.DECRYPT_MODE,org,id,r.getString(1),r.getBytes(2),r.getBytes(3)),Secret.class),org,id);
  if(values.size()!=1)throw new IllegalStateException("Database credential unavailable");return values.getFirst();
 }
 public void revoke(UUID org,UUID id){db.update("UPDATE app.database_credential SET revoked=true WHERE organization_id=? AND id=?",org,id);}
 private byte[] crypt(int mode,UUID org,UUID id,String kid,byte[] nonce,byte[] data){try{
  if(!keys.containsKey(kid))throw new Exception();var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(keys.get(kid),"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(("ecosync-database/"+org+"/"+id+"/"+kid).getBytes(StandardCharsets.UTF_8));return c.doFinal(data);
 }catch(Exception e){throw new IllegalStateException("Database credential unavailable");}}
}
