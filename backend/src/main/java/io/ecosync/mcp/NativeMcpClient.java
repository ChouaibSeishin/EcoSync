package io.ecosync.mcp;
import java.util.*;
public interface NativeMcpClient {
 record Connect(UUID organizationId,UUID integrationId,long configurationVersion,String configurationFingerprint,
                String ownerId,String endpoint,String credential,String protocolPolicy){
  @Override public String toString(){return "Connect[org="+organizationId+", integration="+integrationId+", revision="+configurationVersion+", credential=REDACTED]";}
 }
 record Handle(String key,String leaseId){}
 record Tool(String name,String description,Map<String,Object> inputSchema,Map<String,Object> outputSchema,
             Map<String,Object> annotations,Map<String,Object> metadata,String fingerprint,boolean supported,String unsupportedReason){}
 record Snapshot(String key,String leaseId,String protocol,String era,Map<String,Object> serverInfo,
                 Map<String,Object> serverCapabilities,List<Tool> tools){
  public Handle handle(){return new Handle(key,leaseId);}
 }
 record Outcome(String protocol,String toolFingerprint,Map<String,Object> result){}
 Snapshot connect(Connect configuration);
 Snapshot discover(Handle handle);
 Outcome invoke(Handle handle,String originalName,String expectedFingerprint,Map<String,Object> arguments,UUID operationId);
 boolean cancel(Handle handle,UUID operationId);
 void close(Handle handle,boolean revoke);
}
