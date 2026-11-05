package io.ecosync.restgateway;
import io.ecosync.restcontract.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.*;
import static io.ecosync.restcontract.RestJson.*;

/** Strict GET-only HTTP/1.1. No redirects, retries, proxy, cookies or second resolver. */
public final class PinnedHttp {
 private final DestinationPolicy destinations;private final SSLSocketFactory tlsFactory;
 public PinnedHttp(DestinationPolicy destinations){this(destinations,(SSLSocketFactory)SSLSocketFactory.getDefault());}
 PinnedHttp(DestinationPolicy destinations,SSLSocketFactory tlsFactory){this.destinations=destinations;this.tlsFactory=tlsFactory;}
 public Map<String,Object> get(Map<String,Object> config,Map<String,Object> arguments,Operations.Operation operation,Runnable reauthorize){
  var meta=object(config.get("operation"));String target=RestArguments.target(meta,arguments);String type=(String)config.get("authType"),header=(String)config.get("authHeader"),secret=(String)config.get("secret");RestAuth.configuration(type,header,secret);if(RestJson.containsSecret(arguments,secret))throw new RestFailure("REST_INVALID_ARGUMENTS");var destination=destinations.target((String)config.get("baseUrl"));
  destinations.resolve(destination);operation.check();var address=destinations.resolve(destination);reauthorize.run();operation.check();
  try(Socket raw=new Socket(Proxy.NO_PROXY)){
   operation.socket(raw);raw.connect(new InetSocketAddress(address,destination.port()),2000);raw.setSoTimeout(3000);Socket connection=raw;
   if(destination.scheme().equals("https")){var tls=(SSLSocket)tlsFactory.createSocket(raw,destination.host(),destination.port(),true);operation.socket(tls);var parameters=tls.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");parameters.setServerNames(List.of(new SNIHostName(destination.host())));parameters.setApplicationProtocols(new String[]{"http/1.1"});tls.setSSLParameters(parameters);tls.setSoTimeout(3000);tls.startHandshake();connection=tls;}
   operation.check();reauthorize.run();operation.check();String auth=type.equals("NONE")?"":type.equals("BEARER")?"Authorization: Bearer "+secret+"\r\n":header+": "+secret+"\r\n";
   String request="GET "+destination.basePath()+target+" HTTP/1.1\r\nHost: "+destination.host()+":"+destination.port()+"\r\nAccept: application/json\r\nAccept-Encoding: gzip\r\nConnection: close\r\n"+auth+"\r\n";if(request.length()>12288)throw new RestFailure("REST_LIMIT");connection.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));connection.getOutputStream().flush();
   var result=read(connection.getInputStream(),meta,operation);operation.check();reauthorize.run();operation.check();if(RestJson.containsSecret(result,secret))throw new RestFailure("REST_INVALID_RESPONSE");return result;
  }catch(RestFailure e){throw e;}catch(SSLException e){throw new RestFailure("REST_TLS_FAILED");}catch(SocketTimeoutException e){throw new RestFailure("REST_TIMEOUT");}catch(IOException e){operation.check();throw new RestFailure("REST_NETWORK_ERROR");}
 }
 static Map<String,Object> read(InputStream source,Map<String,Object> meta,Operations.Operation operation)throws IOException{
  var in=new BufferedInputStream(source,4096);var status=line(in,1024);if(!status.matches("HTTP/1\\.[01] [1-5][0-9]{2}(?: [\\x20-\\x7e]*)?"))throw new RestFailure("REST_INVALID_RESPONSE");int code=Integer.parseInt(status.substring(9,12));
  var headers=new TreeMap<String,String>();int size=0;for(int n=0;;n++){String row=line(in,8192);size+=row.length()+2;if(size>16384||n>64)throw new RestFailure("REST_LIMIT");if(row.isEmpty())break;int colon=row.indexOf(':');if(colon<1||!row.substring(0,colon).matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))throw new RestFailure("REST_INVALID_RESPONSE");String key=row.substring(0,colon).toLowerCase(Locale.ROOT),value=row.substring(colon+1).strip();if(value.chars().anyMatch(c->c<32||c>126)||headers.putIfAbsent(key,value)!=null)throw new RestFailure("REST_INVALID_RESPONSE");}
  // Reject without reading/following Location or consuming an error body.
  if(code>=300&&code<400)throw new RestFailure("REST_REDIRECT_DENIED");if(code==401)throw new RestFailure("REST_AUTHENTICATION_FAILED");if(code==403)throw new RestFailure("REST_FORBIDDEN");if(code==404)throw new RestFailure("REST_NOT_FOUND");if(code<200||code>=300)throw new RestFailure("REST_HTTP_ERROR");
  if(!headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;\\s*charset=utf-8)?"))throw new RestFailure("REST_INVALID_RESPONSE");
  if(headers.containsKey("transfer-encoding")&&headers.containsKey("content-length"))throw new RestFailure("REST_INVALID_RESPONSE");byte[] bytes;
  if(headers.containsKey("transfer-encoding")){if(!headers.get("transfer-encoding").equalsIgnoreCase("chunked"))throw new RestFailure("REST_INVALID_RESPONSE");var body=new ByteArrayOutputStream();for(int chunks=0;;chunks++){operation.check();if(chunks>2048)throw new RestFailure("REST_LIMIT");String length=line(in,32);if(!length.matches("[0-9a-fA-F]{1,8}"))throw new RestFailure("REST_INVALID_RESPONSE");long count=Long.parseLong(length,16);if(count==0){if(!line(in,2).isEmpty())throw new RestFailure("REST_INVALID_RESPONSE");break;}if(count>262144-body.size())throw new RestFailure("REST_RESPONSE_LIMIT");copy(in,body,(int)count,operation);if(!line(in,2).isEmpty())throw new RestFailure("REST_INVALID_RESPONSE");}bytes=body.toByteArray();}
  else if(headers.containsKey("content-length")){String length=headers.get("content-length");if(!length.matches("[0-9]{1,9}"))throw new RestFailure("REST_INVALID_RESPONSE");int count=Integer.parseInt(length);if(count>262144)throw new RestFailure("REST_RESPONSE_LIMIT");var body=new ByteArrayOutputStream();copy(in,body,count,operation);bytes=body.toByteArray();}
  else bytes=bounded(in,262144,operation);
  String encoding=headers.getOrDefault("content-encoding","identity");if(encoding.equalsIgnoreCase("gzip"))bytes=bounded(new GZIPInputStream(new ByteArrayInputStream(bytes)),262144,operation);else if(!encoding.equalsIgnoreCase("identity"))throw new RestFailure("REST_INVALID_RESPONSE");
  Object value=RestJson.read(bytes,262144);var responses=object(meta.get("responses"));if(!responses.containsKey(Integer.toString(code)))throw new RestFailure("REST_INVALID_RESPONSE");RestSchema.validate(object(responses.get(Integer.toString(code))),value,"REST_INVALID_RESPONSE");
  int[] items={0};Object bounded=items(value,items);var result=new LinkedHashMap<String,Object>();result.put("data",bounded);result.put("status",code);result.put("untrusted",true);result.put("truncated",items[0]>100);result.put("partial",items[0]>100);result.put("returnedItems",Math.min(items[0],100));result.put("retrievedAt",java.time.Instant.now().toString());return result;
 }
 private static Object items(Object value,int[] count){if(value instanceof List<?> list){var out=new ArrayList<>();for(Object item:list){count[0]++;if(count[0]<=100)out.add(items(item,count));}return out;}if(value instanceof Map<?,?> map){var out=new LinkedHashMap<String,Object>();for(var e:map.entrySet())out.put(e.getKey().toString(),items(e.getValue(),count));return out;}return value;}
 private static byte[] bounded(InputStream in,int maximum,Operations.Operation op)throws IOException{var out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];for(int n;(n=in.read(buffer))!=-1;){op.check();if(n>maximum-out.size())throw new RestFailure("REST_RESPONSE_LIMIT");out.write(buffer,0,n);}return out.toByteArray();}
 private static void copy(InputStream in,ByteArrayOutputStream out,int count,Operations.Operation op)throws IOException{byte[] buffer=new byte[4096];while(count>0){op.check();int n=in.read(buffer,0,Math.min(count,buffer.length));if(n<0)throw new RestFailure("REST_INVALID_RESPONSE");out.write(buffer,0,n);count-=n;}}
 private static String line(InputStream in,int maximum)throws IOException{var out=new ByteArrayOutputStream();while(out.size()<=maximum){int c=in.read();if(c<0)throw new RestFailure("REST_INVALID_RESPONSE");if(c=='\r'){if(in.read()!='\n')throw new RestFailure("REST_INVALID_RESPONSE");return out.toString(StandardCharsets.US_ASCII);}if(c<32||c>126)throw new RestFailure("REST_INVALID_RESPONSE");out.write(c);}throw new RestFailure("REST_LIMIT");}
}
