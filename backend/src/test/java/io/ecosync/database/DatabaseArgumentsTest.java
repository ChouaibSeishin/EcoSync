package io.ecosync.database;
import java.sql.Types;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DatabaseArgumentsTest {
 final Map<String,Object> table=Map.of("columns",List.of(Map.of("name","id","jdbcType",Types.INTEGER,"typeName","int4"),Map.of("name","label","jdbcType",Types.VARCHAR,"typeName","varchar")));
 @Test void queryLimitsAndOperatorsAreRejectedBeforeProtocolDispatch(){for(Map<String,Object> q:List.<Map<String,Object>>of(Map.of("columns",List.of("id"),"limit",101),Map.of("columns",List.of("id"),"offset",10001),Map.of("columns",List.of("id"),"filters",List.of(Map.of("column","id","operator","IN","value","1"))),Map.of("columns",List.of("id"),"order",List.of(Map.of("column","id","direction","ASC; DELETE")))))assertThatThrownBy(()->DatabaseArguments.check(q,table,List.of("id"))).isInstanceOf(DatabaseFailure.class);}
 @Test void identifiersAreApprovedAndNumericValuesAreTypedIndependentlyOfGateway(){assertThatThrownBy(()->DatabaseArguments.check(Map.of("columns",List.of("secret")),table,List.of("id"))).isInstanceOf(DatabaseFailure.class);assertThatThrownBy(()->DatabaseArguments.check(Map.of("columns",List.of("id"),"filters",List.of(Map.of("column","id","operator","EQ","value","1 OR 1=1"))),table,List.of("id"))).isInstanceOf(DatabaseFailure.class);DatabaseArguments.check(Map.of("columns",List.of("label"),"filters",List.of(Map.of("column","label","operator","EQ","value","' OR 1=1 --"))),table,List.of("id","label"));}
}
