package io.ecosync.gateway;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;
/** Resolve every admission/socket, validate all answers, retain first address set until explicit restart. */
public class DestinationPolicy implements AutoCloseable {
 public interface Resolver {InetAddress[] lookup(String host)throws UnknownHostException;}
 private final Set<String> tuples;private final List<String> networks;private final boolean development;private final Resolver resolver;
 private final Map<String,Set<String>> pins=new ConcurrentHashMap<>();
 private final ThreadPoolExecutor dns=new ThreadPoolExecutor(4,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{var t=new Thread(r,"database-dns");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public DestinationPolicy(String policy,String networks,boolean development){this(policy,networks,development,InetAddress::getAllByName);}
 public DestinationPolicy(String policy,String networks,boolean development,Resolver resolver){this.tuples=Set.copyOf(Arrays.stream(policy.split(",")).filter(s->!s.isBlank()).toList());this.networks=Arrays.stream(networks.split(",")).filter(s->!s.isBlank()).toList();this.development=development;this.resolver=resolver;}
 public void check(Map<String,Object> c){
  String type=Objects.toString(c.get("databaseType"),""),host=Objects.toString(c.get("host"),""),database=Objects.toString(c.get("databaseName"),"");boolean tls=Boolean.TRUE.equals(c.get("tls"));
  if(!Set.of("POSTGRESQL","MYSQL").contains(type)||!host.matches("[a-z0-9][a-z0-9.-]{0,252}")||host.endsWith(".")||!database.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")||!(c.get("port") instanceof Number port)||port.intValue()<1||port.intValue()>65535||!(c.get("schemas") instanceof List<?> schemas)||schemas.isEmpty()||schemas.size()>4||!tls&&!development)throw new GatewayFailure("DB_DESTINATION_DENIED");
  for(Object s:schemas){String schema=Objects.toString(s,"");if(!schema.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")||schema.toLowerCase(Locale.ROOT).startsWith("pg_")||Set.of("information_schema","mysql","performance_schema","sys","app").contains(schema.toLowerCase(Locale.ROOT))||type.equals("MYSQL")&&!schema.equals(database)||!tuples.contains(type+"|"+host+"|"+port.intValue()+"|"+database+"|"+schema+"|"+(tls?"tls":"dev")))throw new GatewayFailure("DB_DESTINATION_DENIED");}
  resolve(host,port.intValue());
 }
 public InetAddress resolve(String host,int port){try{
  // A socket factory cannot use an unregistered host/port even if instantiated elsewhere.
  if(tuples.stream().noneMatch(t->{String[] p=t.split("\\|");return p.length==6&&p[1].equals(host)&&p[2].equals(Integer.toString(port));}))throw new Exception();
  var lookup=dns.submit(()->resolver.lookup(host));InetAddress[] addresses;try{addresses=lookup.get(2,TimeUnit.SECONDS);}catch(Exception e){lookup.cancel(true);throw e;}if(addresses.length<1||addresses.length>8)throw new Exception();var values=new TreeSet<String>();
  for(var address:addresses){byte[] b=address.getAddress();if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isMulticastAddress()||List.of("0.0.0.0/8","127.0.0.0/8","169.254.0.0/16","100.64.0.0/10","192.0.0.0/24","192.0.2.0/24","198.18.0.0/15","198.51.100.0/24","203.0.113.0/24","224.0.0.0/4","240.0.0.0/4","2001:db8::/32").stream().anyMatch(n->contains(n,address))||b.length==16&&(b[0]&254)==252&&networks.stream().noneMatch(n->contains(n,address)))throw new Exception();
   if(address.isSiteLocalAddress()&&networks.stream().noneMatch(n->contains(n,address)))throw new Exception();values.add(address.getHostAddress());}
  Set<String> prior=pins.putIfAbsent(host+":"+port,Set.copyOf(values));if(prior!=null&&!prior.equals(values))throw new Exception();return addresses[0];
 }catch(Exception e){throw new GatewayFailure("DB_DESTINATION_DENIED");}}
 static boolean contains(String cidr,InetAddress value){try{String[] parts=cidr.split("/");byte[] network=InetAddress.getByName(parts[0]).getAddress(),address=value.getAddress();int bits=Integer.parseInt(parts[1]);if(network.length!=address.length||bits<0||bits>network.length*8)return false;for(int i=0;i<bits;i++)if((network[i/8]&(1<<(7-i%8)))!=(address[i/8]&(1<<(7-i%8))))return false;return true;}catch(Exception e){return false;}}
 @Override public void close(){dns.shutdownNow();}
}
