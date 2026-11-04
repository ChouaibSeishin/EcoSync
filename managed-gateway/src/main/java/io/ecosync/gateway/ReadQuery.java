package io.ecosync.gateway;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** All identifiers are selected from reviewed metadata. All values (including limits) are bound. */
public final class ReadQuery {
 public record Plan(String sql,List<Object> parameters,List<String> columns,int limit,int offset){}
 private static void invalid(){throw new GatewayFailure("DB_INVALID_QUERY");}
 private static String quote(String name,boolean mysql){String q=mysql?"`":"\"";return q+name.replace(q,q+q)+q;}
 public Plan build(boolean mysql,Map<String,Object> table,Collection<String> approved,Map<String,Object> request){
  for(String k:request.keySet())if(!Set.of("connectorId","revision","tableId","columns","filters","order","limit","offset").contains(k))invalid();
  if(!(request.get("columns") instanceof List<?> selected)||selected.isEmpty()||selected.size()>20||new HashSet<>(selected).size()!=selected.size())throw new GatewayFailure("DB_INVALID_QUERY");
  var metadata=new LinkedHashMap<String,Map<String,Object>>();for(var c:(List<Map<String,Object>>)table.get("columns"))metadata.put((String)c.get("name"),c);
  var names=new ArrayList<String>();for(var v:selected){if(!(v instanceof String name)||!approved.contains(name)||!metadata.containsKey(name)||!Boolean.TRUE.equals(metadata.get(name).get("supported")))throw new GatewayFailure("DB_ACCESS_DENIED");names.add(name);}
  var pk=(List<String>)table.get("primaryKey");if(pk==null||pk.isEmpty()||pk.size()>4||!approved.containsAll(pk))throw new GatewayFailure("DB_ACCESS_DENIED");
  int limit=number(request.getOrDefault("limit",50),1,100),offset=number(request.getOrDefault("offset",0),0,10000);var values=new ArrayList<Object>();var predicates=new ArrayList<String>();
  Object filtersValue=request.getOrDefault("filters",List.of());if(!(filtersValue instanceof List<?> filters)||filters.size()>8)throw new GatewayFailure("DB_INVALID_QUERY");
  for(Object raw:filters){if(!(raw instanceof Map<?,?> f)||!Set.of("column","operator","value").containsAll(f.keySet()))throw new GatewayFailure("DB_INVALID_QUERY");String column=Objects.toString(f.get("column"),""),op=Objects.toString(f.get("operator"),"");if(!approved.contains(column)||!metadata.containsKey(column))throw new GatewayFailure("DB_ACCESS_DENIED");
   if(Set.of("IS_NULL","IS_NOT_NULL").contains(op)){if(f.containsKey("value"))invalid();predicates.add(quote(column,mysql)+(op.equals("IS_NULL")?" IS NULL":" IS NOT NULL"));continue;}
   String sql=switch(op){case "EQ"->"=";case "NE"->"<>";case "LT"->"<";case "LTE"->"<=";case "GT"->">";case "GTE"->">=";default->throw new GatewayFailure("DB_INVALID_QUERY");};values.add(value(metadata.get(column),f.get("value")));predicates.add(quote(column,mysql)+" "+sql+" ?");
  }
  Object orderValue=request.getOrDefault("order",List.of());if(!(orderValue instanceof List<?> order)||order.size()>3)throw new GatewayFailure("DB_INVALID_QUERY");var orders=new LinkedHashMap<String,String>();
  for(Object raw:order){if(!(raw instanceof Map<?,?> o)||!o.keySet().equals(Set.of("column","direction")))throw new GatewayFailure("DB_INVALID_QUERY");String column=Objects.toString(o.get("column"),""),direction=Objects.toString(o.get("direction"),"");if(!approved.contains(column)||!metadata.containsKey(column)||!Set.of("ASC","DESC").contains(direction)||orders.putIfAbsent(column,direction)!=null)throw new GatewayFailure("DB_INVALID_QUERY");}
  pk.forEach(c->orders.putIfAbsent(c,"ASC"));String sql="SELECT "+(mysql?"/*+ MAX_EXECUTION_TIME(4000) */ ":"")+String.join(",",names.stream().map(n->quote(n,mysql)).toList())+" FROM "+quote((String)table.get("schema"),mysql)+"."+quote((String)table.get("name"),mysql)+(predicates.isEmpty()?"":" WHERE "+String.join(" AND ",predicates))+" ORDER BY "+String.join(",",orders.entrySet().stream().map(e->quote(e.getKey(),mysql)+" "+e.getValue()).toList())+" LIMIT ? OFFSET ?";values.add(limit+1);values.add(offset);return new Plan(sql,values,names,limit,offset);
 }
 static int number(Object value,int min,int max){if(!(value instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw new GatewayFailure("DB_INVALID_QUERY");return n.intValue();}
 static Object value(Map<String,Object> meta,Object raw){
  int type=((Number)meta.get("jdbcType")).intValue();try{
   if(raw==null)throw new Exception();if(type==Types.BOOLEAN||type==Types.BIT){if(!(raw instanceof Boolean))throw new Exception();return raw;}
   if(!(raw instanceof String s)||s.length()>2048||s.indexOf(0)>=0)throw new Exception();
   if(type==Types.OTHER&&meta.get("typeName").toString().equalsIgnoreCase("uuid"))return UUID.fromString(s);
   if(Set.of(Types.INTEGER,Types.BIGINT,Types.SMALLINT,Types.TINYINT).contains(type)){if(!s.matches("-?(0|[1-9][0-9]{0,19})"))throw new Exception();var value=new BigInteger(s);boolean unsigned=meta.get("typeName").toString().toUpperCase(Locale.ROOT).contains("UNSIGNED");int bits=type==Types.BIGINT?64:type==Types.INTEGER?32:type==Types.SMALLINT?16:8;var min=unsigned?BigInteger.ZERO:BigInteger.ONE.shiftLeft(bits-1).negate();var max=BigInteger.ONE.shiftLeft(unsigned?bits:bits-1).subtract(BigInteger.ONE);if(value.compareTo(min)<0||value.compareTo(max)>0)throw new Exception();return new BigDecimal(value);}
   if(type==Types.NUMERIC||type==Types.DECIMAL){if(!s.matches("-?(0|[1-9][0-9]{0,37})(\\.[0-9]{1,18})?"))throw new Exception();var decimal=new BigDecimal(s);int precision=((Number)meta.get("precision")).intValue(),scale=((Number)meta.get("scale")).intValue();if(precision>0&&(decimal.precision()>precision||decimal.scale()>scale))throw new Exception();return decimal;}
   if(type==Types.DATE)return java.sql.Date.valueOf(LocalDate.parse(s));
   if(type==Types.TIMESTAMP||type==Types.TIMESTAMP_WITH_TIMEZONE){if(!s.endsWith("Z"))throw new Exception();return Timestamp.from(Instant.parse(s));}
   if(Set.of(Types.CHAR,Types.VARCHAR,Types.LONGVARCHAR).contains(type)){int max=((Number)meta.get("precision")).intValue();if(max>0&&s.length()>max)throw new Exception();return s;}
  }catch(Exception e){throw new GatewayFailure("DB_INVALID_QUERY");}throw new GatewayFailure("DB_INVALID_QUERY");
 }
}
