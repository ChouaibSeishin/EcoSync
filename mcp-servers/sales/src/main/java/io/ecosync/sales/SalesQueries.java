package io.ecosync.sales;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixed, bounded prepared reads. No caller-provided SQL, identifiers, or ordering. */
final class SalesQueries {
 static final java.util.concurrent.atomic.AtomicLong sourceAttempts=new java.util.concurrent.atomic.AtomicLong();
 private final JdbcTemplate db;
 SalesQueries(JdbcTemplate db){this.db=db;}
 private static Map<String,Object> integer(int max){return Map.of("type","integer","minimum",1,"maximum",max);}
 Map<String,Object> schema(String name){
  Map<String,Object> props=new LinkedHashMap<>();
  props.put("startInclusive",Map.of("type","string","format","date-time","maxLength",30));
  props.put("endExclusive",Map.of("type","string","format","date-time","maxLength",30));
  props.put("currency",Map.of("type","string","enum",List.of("USD","EUR","GBP")));
  var required=new ArrayList<>(List.of("startInclusive","endExclusive"));
  if(name.equals("sales_get_revenue_summary"))props.put("productId",integer(Integer.MAX_VALUE));
  if(name.equals("sales_get_top_products")){props.put("limit",integer(50));required.add("currency");}
  if(name.equals("sales_get_order_summary")){
   props.put("orderId",integer(Integer.MAX_VALUE));props.put("limit",integer(100));
   props.put("status",Map.of("type","string","enum",List.of("FULFILLED","PENDING","CANCELLED")));
  }
  return Map.of("type","object","properties",props,"required",required,"additionalProperties",false);
 }
 String description(String name){return switch(name){
  case "sales_get_revenue_summary" -> "Realized revenue: FULFILLED orders only, exact decimal strings grouped by currency; UTC [startInclusive,endExclusive), maximum 366 days.";
  case "sales_get_top_products" -> "Top products by exact FULFILLED revenue for one explicit currency; complete aggregation before bounded ranking, stable product-ID ties. No unit conversion.";
  default -> "Order/currency summaries in a UTC half-open window; realizedRevenue is zero for non-FULFILLED orders. Bounded rows with explicit truncation, no currency conversion.";
 };}
 @SuppressWarnings("unchecked")
 Map<String,Object> execute(String name,Map<String,Object> args){
  Set<String> allowed=((Map<String,Object>)schema(name).get("properties")).keySet();
  if(!allowed.containsAll(args.keySet()))throw new IllegalArgumentException();
  Instant start=utc(args.get("startInclusive")),end=utc(args.get("endExclusive"));
  if(!end.isAfter(start)||Duration.between(start,end).compareTo(Duration.ofDays(366))>0)throw new IllegalArgumentException();
  String currency=optional(args,"currency",Set.of("USD","EUR","GBP"));
  String status=optional(args,"status",Set.of("FULFILLED","PENDING","CANCELLED"));
  Integer product=number(args,"productId",Integer.MAX_VALUE),order=number(args,"orderId",Integer.MAX_VALUE);
  int limit=Optional.ofNullable(number(args,"limit",name.equals("sales_get_top_products")?50:100)).orElse(25);
  String common=" FROM sales.orders o JOIN sales.order_lines l ON l.order_id=o.order_id ";
  String where=" WHERE o.ordered_at>=? AND o.ordered_at<? AND (?::text IS NULL OR l.currency_code=?::text) ";
  List<Object> values=new ArrayList<>(List.of(Timestamp.from(start),Timestamp.from(end)));
  values.add(currency);values.add(currency);
  String sql;
  if(name.equals("sales_get_revenue_summary")){
   sql="SELECT l.currency_code AS currency, SUM(l.quantity*l.unit_price-l.discount_amount)::text AS revenue, COUNT(DISTINCT o.order_id) AS orders"+common+where+
    "AND o.order_status='FULFILLED' AND (?::integer IS NULL OR l.product_id=?::integer) GROUP BY l.currency_code ORDER BY l.currency_code";
   values.add(product);values.add(product);
  }else if(name.equals("sales_get_top_products")){
   if(currency==null)throw new IllegalArgumentException();
   sql="SELECT l.product_id, p.product_name, p.selling_unit, l.currency_code AS currency, SUM(l.quantity) AS quantity, SUM(l.quantity*l.unit_price-l.discount_amount)::text AS revenue"+
    common+"JOIN sales.products p ON p.product_id=l.product_id"+where+
    "AND o.order_status='FULFILLED' GROUP BY l.product_id,p.product_name,p.selling_unit,l.currency_code ORDER BY SUM(l.quantity*l.unit_price-l.discount_amount) DESC,l.product_id LIMIT ?";
   values.add(limit+1);
  }else{
   sql="SELECT o.order_id,o.ordered_at,o.order_status,l.currency_code AS currency,SUM(l.quantity*l.unit_price-l.discount_amount)::text AS order_amount,"+
    "CASE WHEN o.order_status='FULFILLED' THEN SUM(l.quantity*l.unit_price-l.discount_amount) ELSE 0::numeric END::text AS realized_revenue"+
    common+where+"AND (?::text IS NULL OR o.order_status=?::text) AND (?::integer IS NULL OR o.order_id=?::integer) "+
    "GROUP BY o.order_id,o.ordered_at,o.order_status,l.currency_code ORDER BY o.order_id,l.currency_code LIMIT ?";
   values.add(status);values.add(status);values.add(order);values.add(order);values.add(limit+1);
  }
  sourceAttempts.incrementAndGet();
  var rows=db.query(sql,(rs,index)->{
   Map<String,Object> row=new LinkedHashMap<>();var md=rs.getMetaData();
   for(int c=1;c<=md.getColumnCount();c++){Object value=rs.getObject(c);if(value instanceof Timestamp t)value=t.toInstant().toString();row.put(md.getColumnLabel(c),value);}
   return row;
  },values.toArray());
  boolean truncated=rows.size()>limit&&!name.equals("sales_get_revenue_summary");
  if(truncated)rows=new ArrayList<>(rows.subList(0,limit));
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("source","sales-postgresql");result.put("startInclusive",start.toString());result.put("endExclusive",end.toString());
  result.put("filters",args);result.put("rows",rows);result.put("truncated",truncated);
  result.put("moneyRepresentation","exact decimal strings; currencies remain separate");
  return result;
 }
 private static Instant utc(Object value){
  if(!(value instanceof String s)||s.length()>30||!s.endsWith("Z"))throw new IllegalArgumentException();
  try{return Instant.parse(s);}catch(Exception e){throw new IllegalArgumentException();}
 }
 private static Integer number(Map<String,Object>a,String k,int max){
  Object v=a.get(k);if(v==null)return null;
  if(!(v instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>max)throw new IllegalArgumentException();
  return n.intValue();
 }
 private static String optional(Map<String,Object>a,String k,Set<String> options){
  Object v=a.get(k);if(v==null)return null;if(!(v instanceof String s)||!options.contains(s))throw new IllegalArgumentException();return s;
 }
}
