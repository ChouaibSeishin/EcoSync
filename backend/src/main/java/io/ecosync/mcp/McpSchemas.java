package io.ecosync.mcp;

import com.networknt.schema.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Restricts schema complexity before invoking the maintained JSON Schema validator. No remote refs. */
@Component
public class McpSchemas {
 private final JsonMapper json;
 private static final Set<String> KEYWORDS=Set.of("$schema","type","properties","required","additionalProperties","items","maxItems","minItems","enum","minimum","maximum","exclusiveMinimum","exclusiveMaximum","minLength","maxLength","format","title","description");
 public McpSchemas(JsonMapper json){this.json=json;}
 public boolean supported(Map<String,Object> schema){
  try{if(schema==null||json.writeValueAsBytes(schema).length>32768)return false;guard(schema,0,new int[]{0});
   SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(json.writeValueAsString(schema),InputFormat.JSON);return true;
  }catch(Exception e){return false;}
 }
 @SuppressWarnings("unchecked") private void guard(Map<String,Object> schema,int depth,int[] count){
  if(depth>8||++count[0]>200||!KEYWORDS.containsAll(schema.keySet()))throw new IllegalArgumentException();
  if(schema.containsKey("$schema")&&!Set.of("https://json-schema.org/draft/2020-12/schema","http://json-schema.org/draft-07/schema#").contains(schema.get("$schema")))throw new IllegalArgumentException();
  if(schema.containsKey("format")&&!schema.get("format").equals("date-time"))throw new IllegalArgumentException();
  var types=schema.get("type") instanceof List<?> list?list:List.of(schema.get("type"));
  if(types.isEmpty()||types.size()>2||!Set.of("object","array","string","integer","number","boolean","null").containsAll(types)||types.size()==2&&!types.contains("null"))throw new IllegalArgumentException();
  if(types.contains("array")&&(!(schema.get("maxItems") instanceof Number n)||n.intValue()>100||n.intValue()<0))throw new IllegalArgumentException();
  if(schema.get("properties") instanceof Map<?,?> props)for(var e:props.entrySet()){
   if(Set.of("__proto__","constructor","prototype").contains(e.getKey()))throw new IllegalArgumentException();guard((Map<String,Object>)e.getValue(),depth+1,count);
  }
  if(schema.get("items") instanceof Map<?,?> child)guard((Map<String,Object>)child,depth+1,count);
  if(schema.containsKey("additionalProperties")&&!(schema.get("additionalProperties") instanceof Boolean))throw new IllegalArgumentException();
 }
 public void arguments(Map<String,Object> schema,Map<String,Object> args){validate(schema,args,16384,"MCP_INVALID_ARGUMENTS");}
 public void output(Map<String,Object> schema,Object data){validate(schema,data,1048576,"MCP_INVALID_RESPONSE");}
 private void validate(Map<String,Object> schema,Object data,int limit,String code){
  if(!supported(schema))throw new McpFailure("MCP_SCHEMA_UNSUPPORTED");
  try{
   String input=json.writeValueAsString(data);if(input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>limit)throw new McpFailure(code);
   var compiled=SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(json.writeValueAsString(schema),InputFormat.JSON);
   if(!compiled.validate(input,InputFormat.JSON,c->c.executionConfig(e->e.formatAssertionsEnabled(true))).isEmpty())throw new McpFailure(code);
  }catch(McpFailure e){throw e;}catch(Exception e){throw new McpFailure(code);}
 }
}
