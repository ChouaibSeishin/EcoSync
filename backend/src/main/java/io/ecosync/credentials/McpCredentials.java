package io.ecosync.credentials;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** External versioned KEKs; ciphertext and tenant-bound AAD only in PostgreSQL. */
@Service
@ConditionalOnProperty(name="ecosync.mcp.enabled",havingValue="true")
public class McpCredentials {
 private final JdbcTemplate db;
 private final String active;
 private final Map<String,byte[]> keys=new HashMap<>();
 private final SecureRandom random=new SecureRandom();
 public McpCredentials(JdbcTemplate db,JsonMapper json,@Value("${ecosync.mcp.keyring:}")String path){
  this.db=db;
  try{
   byte[] data=Files.readAllBytes(Path.of(path));if(data.length>16384)throw new IllegalArgumentException();
   var tree=json.readTree(data);active=tree.path("activeKeyId").asString();
   for(var entry:tree.path("keys").properties()){
    if(!entry.getKey().matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException();
    byte[] key=Base64.getDecoder().decode(entry.getValue().asString());if(key.length!=32)throw new IllegalArgumentException();keys.put(entry.getKey(),key);
   }
   if(!keys.containsKey(active)||keys.size()>16)throw new IllegalArgumentException();
  }catch(Exception e){throw new IllegalStateException("External MCP keyring unavailable or invalid");}
 }
 public UUID store(UUID org,String token){
  if(token==null||token.length()<16||token.length()>2048||token.chars().anyMatch(c->c<33||c>126))throw new IllegalArgumentException("Invalid MCP credential");
  UUID id=UUID.randomUUID();byte[] nonce=new byte[12];random.nextBytes(nonce);
  byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,org,id,active,nonce,token.getBytes(StandardCharsets.UTF_8));
  db.update("INSERT INTO app.mcp_credential(organization_id,id,key_id,nonce,ciphertext) VALUES(?,?,?,?,?)",org,id,active,nonce,encrypted);return id;
 }
 public String resolve(UUID org,UUID id){
  var records=db.query("SELECT key_id,nonce,ciphertext FROM app.mcp_credential WHERE organization_id=? AND id=? AND NOT revoked",
   (rs,n)->new String(crypt(Cipher.DECRYPT_MODE,org,id,rs.getString(1),rs.getBytes(2),rs.getBytes(3)),StandardCharsets.UTF_8),org,id);
  if(records.size()!=1)throw new IllegalStateException("MCP credential unavailable");return records.getFirst();
 }
 public void revoke(UUID org,UUID id){db.update("UPDATE app.mcp_credential SET revoked=true WHERE organization_id=? AND id=?",org,id);}
 private byte[] crypt(int mode,UUID org,UUID id,String kid,byte[] nonce,byte[] data){
  try{
   if(!keys.containsKey(kid))throw new IllegalArgumentException();
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(keys.get(kid),"AES"),new GCMParameterSpec(128,nonce));
   cipher.updateAAD(("ecosync-mcp/"+org+"/"+id+"/"+kid).getBytes(StandardCharsets.UTF_8));return cipher.doFinal(data);
  }catch(Exception e){throw new IllegalStateException("MCP credential unavailable");}
 }
}
