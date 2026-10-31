package io.ecosync.mcp;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class McpSchemasTest {
 private final McpSchemas policy=new McpSchemas(JsonMapper.builder().build());
 @Test void externalReferencesPatternsAndUnboundedArraysAreDenied(){
  for(var schema:List.of(Map.<String,Object>of("$ref","http://169.254.169.254/secret"),Map.<String,Object>of("type","string","pattern","(a+)+"),Map.<String,Object>of("type","array","items",Map.of("type","string"))))assertThat(policy.supported(schema)).isFalse();
 }
 @Test void maintainedValidatorRejectsWrongTypesAndExtraProperties(){
  Map<String,Object> schema=Map.of("type","object","properties",Map.of("n",Map.of("type","integer","minimum",1,"maximum",4)),"required",List.of("n"),"additionalProperties",false);
  policy.arguments(schema,Map.of("n",2));
  assertThatThrownBy(()->policy.arguments(schema,Map.of("n","2"))).isInstanceOf(McpFailure.class);
  assertThatThrownBy(()->policy.arguments(schema,Map.of("n",2,"extra",true))).isInstanceOf(McpFailure.class);
  assertThatThrownBy(()->policy.arguments(schema,Map.of("n",5))).isInstanceOf(McpFailure.class);
 }
}
