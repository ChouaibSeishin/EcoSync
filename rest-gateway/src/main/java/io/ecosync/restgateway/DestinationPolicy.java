package io.ecosync.restgateway;
import io.ecosync.restcontract.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Every socket gets an already validated InetAddress, with the full DNS set pinned. */
public final class DestinationPolicy implements AutoCloseable {
 public interface Resolver {InetAddress[] lookup(String host)throws Exception;}
 private final String entries;private final boolean development;private final List<String> cidrs;private final Resolver resolver;private final Map<String,Set<String>> pins=new ConcurrentHashMap<>();
 private final ThreadPoolExecutor dns=new ThreadPoolExecutor(4,4,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{var t=new Thread(r,"rest-dns");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 public DestinationPolicy(String entries,String cidrs,boolean development){this(entries,cidrs,development,InetAddress::getAllByName);}
 public DestinationPolicy(String entries,String cidrs,boolean development,Resolver resolver){this.entries=entries;this.development=development;this.cidrs=Arrays.stream(cidrs.split(",")).filter(s->!s.isBlank()).toList();this.resolver=resolver;for(String c:this.cidrs){if(!c.matches("[0-9a-fA-F:./]+")||!c.contains("/"))throw new IllegalArgumentException();}}
 public RestDestination.Target target(String value){return RestDestination.check(value,entries,development);}
 public InetAddress resolve(RestDestination.Target target){try{
  target(target.canonical());var lookup=dns.submit(()->resolver.lookup(target.host()));InetAddress[] answers;try{answers=lookup.get(1500,TimeUnit.MILLISECONDS);}finally{lookup.cancel(true);}if(answers.length==0||answers.length>8)throw new Exception();var current=new TreeSet<String>();
  for(var a:answers){byte[] bytes=a.getAddress();if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isMulticastAddress()||a instanceof Inet6Address v6&&v6.getScopeId()!=0)throw new Exception();
   boolean prohibited=List.of("0.0.0.0/8","127.0.0.0/8","169.254.0.0/16","100.64.0.0/10","192.0.0.0/24","192.0.2.0/24","198.18.0.0/15","198.51.100.0/24","203.0.113.0/24","224.0.0.0/4","240.0.0.0/4","::/96","::ffff:0:0/96","64:ff9b::/96","64:ff9b:1::/48","100::/64","2001::/23","2001:db8::/32","2002::/16","3fff::/20","fec0::/10").stream().anyMatch(c->contains(c,a));
   if(bytes.length==16){boolean mapped=true;for(int i=0;i<10;i++)mapped&=bytes[i]==0;if(mapped&&bytes[10]==(byte)255&&bytes[11]==(byte)255)throw new Exception();}
   if(prohibited)throw new Exception();boolean privateAddress=a.isSiteLocalAddress()||bytes.length==16&&(bytes[0]&254)==252;
   if(privateAddress&&cidrs.stream().noneMatch(c->contains(c,a)))throw new Exception();if(bytes.length==16&&!privateAddress&&(bytes[0]&224)!=32)throw new Exception();current.add(a.getHostAddress());
  }
  Set<String> old=pins.putIfAbsent(target.canonical(),Set.copyOf(current));if(old!=null&&!old.equals(current))throw new Exception();return answers[0];
 }catch(Exception e){throw new RestFailure("REST_DESTINATION_DENIED");}}
 static boolean contains(String cidr,InetAddress a){try{String[] p=cidr.split("/");if(p.length!=2||!p[0].matches("[0-9a-fA-F:.]+"))return false;byte[] network=InetAddress.getByName(p[0]).getAddress(),address=a.getAddress();int bits=Integer.parseInt(p[1]);if(network.length!=address.length||bits<0||bits>network.length*8)return false;for(int i=0;i<bits;i++)if((network[i/8]&(1<<(7-i%8)))!=(address[i/8]&(1<<(7-i%8))))return false;return true;}catch(Exception e){return false;}}
 @Override public void close(){dns.shutdownNow();}
}
