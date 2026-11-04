package io.ecosync.gateway;
import java.sql.*;
import java.util.*;
public final class SqlErrors {
 public static String code(SQLException error){String code="DB_UNAVAILABLE";for(Throwable t=error;t!=null;t=t.getCause()){
  String state=t instanceof SQLException sql?Objects.toString(sql.getSQLState(),""):"";int vendor=t instanceof SQLException sql?sql.getErrorCode():0;
  if(state.startsWith("28"))code="DB_AUTHENTICATION_FAILED";if(state.equals("42501")||Set.of(1044,1142,1143,1227).contains(vendor))code="DB_ACCESS_DENIED";
  if(state.startsWith("22"))code="DB_INVALID_QUERY";if(state.equals("57014")||state.equals("55P03")||t instanceof SQLTimeoutException||Set.of(1205,3024).contains(vendor))code="DB_LIMIT";
  if(t instanceof javax.net.ssl.SSLException)code="DB_TLS_FAILED";if(t instanceof GatewayFailure f)code=f.code;
 }return code;}
 public static List<String> metadata(SQLException error){var result=new ArrayList<String>();for(Throwable t=error;t!=null&&result.size()<32;t=t.getCause()){result.add(t.getClass().getSimpleName()+":"+(t instanceof SQLException sql?Objects.toString(sql.getSQLState(),""):""));}return List.copyOf(result);}
}
