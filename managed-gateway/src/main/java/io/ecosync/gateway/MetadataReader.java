package io.ecosync.gateway;
import java.sql.*;
import java.util.*;
public final class MetadataReader {
 public static boolean supported(int type,String name){return Set.of(Types.CHAR,Types.VARCHAR,Types.LONGVARCHAR,Types.INTEGER,Types.BIGINT,Types.SMALLINT,Types.TINYINT,Types.NUMERIC,Types.DECIMAL,Types.BOOLEAN,Types.BIT,Types.DATE,Types.TIMESTAMP,Types.TIMESTAMP_WITH_TIMEZONE).contains(type)||type==Types.OTHER&&name.equalsIgnoreCase("uuid");}
 private static String identifier(String s){if(s==null||s.isEmpty()||s.length()>128||s.chars().anyMatch(Character::isISOControl))throw new GatewayFailure("DB_LIMIT");return s;}
 public List<Map<String,Object>> inspect(Connection connection,Map<String,Object> config)throws SQLException{
  var result=new ArrayList<Map<String,Object>>();var metadata=connection.getMetaData();boolean mysql=config.get("databaseType").equals("MYSQL");String database=(String)config.get("databaseName");int columns=0;
  for(Object schema:(List<?>)config.get("schemas")){
   try(var tables=metadata.getTables(database,mysql?null:schema.toString(),"%",new String[]{"TABLE"})){
    while(tables.next()){if(result.size()>=64)throw new GatewayFailure("DB_LIMIT");String actualSchema=mysql?tables.getString("TABLE_CAT"):tables.getString("TABLE_SCHEM");if(!schema.equals(actualSchema))continue;
     String name=identifier(tables.getString("TABLE_NAME"));if(Set.of("schema_history","flyway_schema_history").contains(name.toLowerCase(Locale.ROOT)))continue;
     var table=table(connection,config,schema.toString(),name);columns+=((List<?>)table.get("columns")).size();if(columns>512)throw new GatewayFailure("DB_LIMIT");result.add(table);
    }
   }
  }result.sort(Comparator.comparing(t->t.get("schema")+"."+t.get("name")));return result;
 }
 public Map<String,Object> table(Connection connection,Map<String,Object> config,String schema,String name)throws SQLException{
  if(!((List<?>)config.get("schemas")).contains(schema))throw new GatewayFailure("DB_ACCESS_DENIED");var db=connection.getMetaData();boolean mysql=config.get("databaseType").equals("MYSQL");String catalog=(String)config.get("databaseName");var columns=new ArrayList<Map<String,Object>>();
  // Escape JDBC metadata patterns; '%' and '_' in names must remain literal.
  String escape=db.getSearchStringEscape();String tablePattern=name.replace(escape,escape+escape).replace("_",escape+"_").replace("%",escape+"%");
  try(var rows=db.getColumns(catalog,mysql?null:schema,tablePattern,"%")){
   while(rows.next()){if(columns.size()>=64)throw new GatewayFailure("DB_LIMIT");if(!name.equals(rows.getString("TABLE_NAME")))continue;String type=Objects.toString(rows.getString("TYPE_NAME"),"");if(type.length()>128)throw new GatewayFailure("DB_LIMIT");int jdbc=rows.getInt("DATA_TYPE");var c=new LinkedHashMap<String,Object>();c.put("name",identifier(rows.getString("COLUMN_NAME")));c.put("jdbcType",jdbc);c.put("typeName",type);c.put("nullable",rows.getInt("NULLABLE")!=DatabaseMetaData.columnNoNulls);c.put("ordinal",rows.getInt("ORDINAL_POSITION"));c.put("precision",rows.getInt("COLUMN_SIZE"));c.put("scale",rows.getInt("DECIMAL_DIGITS"));c.put("supported",supported(jdbc,type));columns.add(c);}
  }
  if(columns.isEmpty())throw new GatewayFailure("DB_SCHEMA_CHANGED");columns.sort(Comparator.comparingInt(c->((Number)c.get("ordinal")).intValue()));
  var primary=new TreeMap<Integer,String>();try(var rows=db.getPrimaryKeys(catalog,mysql?null:schema,name)){while(rows.next()){if(primary.size()>=8)throw new GatewayFailure("DB_LIMIT");primary.put(rows.getInt("KEY_SEQ"),identifier(rows.getString("COLUMN_NAME")));}}
  var foreign=new ArrayList<Map<String,Object>>();try(var rows=db.getImportedKeys(catalog,mysql?null:schema,name)){while(rows.next()){if(foreign.size()>=64)throw new GatewayFailure("DB_LIMIT");String targetSchema=mysql?rows.getString("PKTABLE_CAT"):rows.getString("PKTABLE_SCHEM");if(!((List<?>)config.get("schemas")).contains(targetSchema))continue;foreign.add(Map.of("column",identifier(rows.getString("FKCOLUMN_NAME")),"targetSchema",targetSchema,"targetTable",identifier(rows.getString("PKTABLE_NAME")),"targetColumn",identifier(rows.getString("PKCOLUMN_NAME")),"sequence",rows.getInt("KEY_SEQ")));}}
  foreign.sort(Comparator.comparing(f->f.get("column")+"/"+f.get("targetSchema")+"/"+f.get("targetTable")+"/"+f.get("targetColumn")+"/"+f.get("sequence")));var result=new LinkedHashMap<String,Object>(Map.of("schema",schema,"name",name,"columns",columns,"primaryKey",new ArrayList<>(primary.values()),"foreignKeys",foreign));if(new tools.jackson.databind.json.JsonMapper().writeValueAsBytes(result).length>32768)throw new GatewayFailure("DB_LIMIT");return result;
 }
}
