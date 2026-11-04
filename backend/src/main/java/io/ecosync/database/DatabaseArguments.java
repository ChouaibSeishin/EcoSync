package io.ecosync.database;
import java.sql.Types;
import java.math.*;
import java.time.*;
import java.util.*;
/** Independent host-side query contract check before the official MCP client's schema validation. */
public final class DatabaseArguments {
 private static void invalid(){throw new DatabaseFailure("DB_INVALID_QUERY");}
 private static void identifier(Object value,Collection<String> approved){if(!(value instanceof String s)||s.isEmpty()||s.length()>128||!approved.contains(s))throw new DatabaseFailure("DB_ACCESS_DENIED");}
 private static void number(Object value,int min,int max){if(!(value instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)invalid();}
 public static void check(Map<String,Object> query,Map<String,Object> table,Collection<String> approved){
  if(!(query.get("columns") instanceof List<?> columns)||columns.isEmpty()||columns.size()>20||new HashSet<>(columns).size()!=columns.size())throw new DatabaseFailure("DB_INVALID_QUERY");columns.forEach(c->identifier(c,approved));
  number(query.getOrDefault("limit",50),1,100);number(query.getOrDefault("offset",0),0,10000);
  Object f=query.getOrDefault("filters",List.of());if(!(f instanceof List<?> filters)||filters.size()>8)throw new DatabaseFailure("DB_INVALID_QUERY");
  for(Object raw:filters){if(!(raw instanceof Map<?,?> filter)||!Set.of("column","operator","value").containsAll(filter.keySet()))throw new DatabaseFailure("DB_INVALID_QUERY");identifier(filter.get("column"),approved);String op=Objects.toString(filter.get("operator"),"");if(!Set.of("EQ","NE","LT","LTE","GT","GTE","IS_NULL","IS_NOT_NULL").contains(op))invalid();
   if(Set.of("IS_NULL","IS_NOT_NULL").contains(op)){if(filter.containsKey("value"))invalid();continue;}Object value=filter.get("value");if(!(value instanceof String)&&!(value instanceof Boolean)||value instanceof String s&&(s.length()>2048||s.indexOf(0)>=0))invalid();
   var meta=((List<Map<String,Object>>)table.get("columns")).stream().filter(c->c.get("name").equals(filter.get("column"))).findFirst().orElseThrow(()->new DatabaseFailure("DB_ACCESS_DENIED"));typed(meta,value);
  }
  Object o=query.getOrDefault("order",List.of());if(!(o instanceof List<?> order)||order.size()>3)throw new DatabaseFailure("DB_INVALID_QUERY");var seen=new HashSet<Object>();for(Object raw:order){if(!(raw instanceof Map<?,?> item)||!item.keySet().equals(Set.of("column","direction"))||!Set.of("ASC","DESC").contains(item.get("direction"))||!seen.add(item.get("column")))throw new DatabaseFailure("DB_INVALID_QUERY");identifier(item.get("column"),approved);}
 }
 private static void typed(Map<String,Object> meta,Object value){try{int type=((Number)meta.get("jdbcType")).intValue();if(type==Types.BOOLEAN||type==Types.BIT){if(!(value instanceof Boolean))invalid();return;}if(!(value instanceof String s))throw new DatabaseFailure("DB_INVALID_QUERY");
   if(Set.of(Types.INTEGER,Types.BIGINT,Types.SMALLINT,Types.TINYINT).contains(type)){if(!s.matches("-?(0|[1-9][0-9]{0,19})"))invalid();var v=new BigInteger(s);boolean unsigned=meta.get("typeName").toString().toUpperCase(Locale.ROOT).contains("UNSIGNED");int bits=type==Types.BIGINT?64:type==Types.INTEGER?32:type==Types.SMALLINT?16:8;if(v.compareTo(unsigned?BigInteger.ZERO:BigInteger.ONE.shiftLeft(bits-1).negate())<0||v.compareTo(BigInteger.ONE.shiftLeft(unsigned?bits:bits-1).subtract(BigInteger.ONE))>0)invalid();
   }else if(type==Types.DECIMAL||type==Types.NUMERIC){if(!s.matches("-?(0|[1-9][0-9]{0,37})(\\.[0-9]{1,18})?"))invalid();var d=new BigDecimal(s);int precision=((Number)meta.get("precision")).intValue(),scale=((Number)meta.get("scale")).intValue();if(precision>0&&(d.precision()>precision||d.scale()>scale))invalid();
   }else if(type==Types.DATE)LocalDate.parse(s);else if(type==Types.TIMESTAMP||type==Types.TIMESTAMP_WITH_TIMEZONE){if(!s.endsWith("Z"))invalid();Instant.parse(s);}else if(type==Types.OTHER&&meta.get("typeName").toString().equalsIgnoreCase("uuid"))UUID.fromString(s);else if(!Set.of(Types.CHAR,Types.VARCHAR,Types.LONGVARCHAR).contains(type))invalid();
  }catch(DatabaseFailure e){throw e;}catch(Exception e){throw new DatabaseFailure("DB_INVALID_QUERY");}}
}
