package io.ecosync.restcontract;
import java.net.URI;
import java.util.*;
/** Exact operator configuration, shared by authority and runtime; no DNS in the backend. */
public final class RestDestination {
 public record Target(String scheme,String host,int port,String basePath,String canonical){}
 private RestDestination(){}
 public static Target parse(String value){try{
  if(value==null||value.length()>512||!value.matches("[\\x21-\\x7e]+")||value.contains("%")||value.contains("\\"))throw new Exception();var u=URI.create(value);
  String host=u.getHost(),path=u.getRawPath();if(!Set.of("https","http").contains(u.getScheme())||host==null||!host.matches("[a-z][a-z0-9-]*(?:\\.[a-z0-9][a-z0-9-]*)*")||host.length()>253||host.endsWith(".")||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null)throw new Exception();
  int port=u.getPort()==-1?(u.getScheme().equals("https")?443:80):u.getPort();if(port<1||port>65535)throw new Exception();if(path==null||path.equals("/"))path="";if(!path.isEmpty()&&(!path.matches("(?:/[A-Za-z0-9_~-]+)+")||path.contains("//")))throw new Exception();String canonical=u.getScheme()+"://"+host+":"+port+path;return new Target(u.getScheme(),host,port,path,canonical);
 }catch(Exception e){throw new RestFailure("REST_DESTINATION_DENIED");}}
 public static Target check(String value,String entries,boolean development){var t=parse(value);var allowed=Set.copyOf(Arrays.stream(entries.split(",")).filter(s->!s.isBlank()).toList());if(!allowed.contains(t.canonical+"|"+(t.scheme.equals("https")?"tls":"dev"))||t.scheme.equals("http")&&!development)throw new RestFailure("REST_DESTINATION_DENIED");return t;}
}
