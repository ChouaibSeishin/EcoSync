package io.ecosync.gateway;
import java.sql.*;
import java.io.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Bounds rows, streamed text cells and encoded result bytes independently of MCP. */
public final class ReadResults {
 private final JsonMapper json;private final Connections connections;
 public ReadResults(JsonMapper json,Connections connections){this.json=json;this.connections=connections;}
 public Map<String,Object> read(Connection connection,ReadQuery.Plan plan,Connections.Operation op)throws SQLException{
  var rows=new ArrayList<Map<String,Object>>();boolean truncated=false,cellTruncated=false;String partial="";int bytes=128;
  try(var statement=connection.prepareStatement(plan.sql())){op.statement=statement;statement.setQueryTimeout(4);statement.setFetchSize(32);statement.setMaxRows(plan.limit()+1);int index=1;for(var value:plan.parameters()){if(value instanceof Timestamp t)statement.setTimestamp(index++,t,Calendar.getInstance(TimeZone.getTimeZone("UTC")));else statement.setObject(index++,value);}op.check();connections.sqlAdmissions.incrementAndGet();
   try(var result=statement.executeQuery()){var meta=result.getMetaData();while(result.next()){op.check();if(rows.size()==plan.limit()){truncated=true;break;}var row=new LinkedHashMap<String,Object>();
     for(int i=1;i<=plan.columns().size();i++){Object value;int type=meta.getColumnType(i);if(Set.of(Types.CHAR,Types.VARCHAR,Types.LONGVARCHAR).contains(type)){try(var reader=result.getCharacterStream(i)){if(reader==null)value=null;else{char[] buffer=new char[2049];int count=0,n;while(count<buffer.length&&(n=reader.read(buffer,count,buffer.length-count))!=-1)count+=n;if(count>2048)cellTruncated=true;value=new String(buffer,0,Math.min(count,2048));}}catch(IOException e){throw new GatewayFailure("DB_UNAVAILABLE");}
      }else if(type==Types.TIMESTAMP||type==Types.TIMESTAMP_WITH_TIMEZONE){var t=result.getTimestamp(i,Calendar.getInstance(TimeZone.getTimeZone("UTC")));value=t==null?null:t.toInstant().toString();}else if(type==Types.BOOLEAN||type==Types.BIT){boolean b=result.getBoolean(i);value=result.wasNull()?null:b;}else{Object raw=result.getObject(i);value=raw==null?null:raw.toString();}row.put(plan.columns().get(i-1),value);}
     int count=json.writeValueAsBytes(row).length+1;if(bytes+count>262144){truncated=true;partial="RESULT_BYTES_LIMIT";break;}rows.add(row);bytes+=count;
    }}finally{op.statement=null;}
  }var result=new LinkedHashMap<String,Object>();result.put("rows",rows);result.put("truncated",truncated);result.put("cellTruncated",cellTruncated);result.put("partialFailure",partial);result.put("nextOffset",truncated&&plan.offset()+rows.size()<=10000&&rows.size()>0?plan.offset()+rows.size():null);result.put("returnedRows",rows.size());result.put("retrievedAt",Instant.now().toString());return result;
 }
}
