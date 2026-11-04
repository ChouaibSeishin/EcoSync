package io.ecosync.gateway;
import java.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DestinationPolicyTest {
 Map<String,Object> config(){return Map.of("databaseType","POSTGRESQL","host","db.example","port",5432,"databaseName","sales","schemas",List.of("sales"),"tls",true);}
 @Test void allDnsAnswersAreCheckedAndMetadataAddressesRemainDenied()throws Exception{for(String ip:List.of("127.0.0.1","0.0.0.0","169.254.169.254","100.100.100.200","::1","fe80::1","224.0.0.1")){var policy=new DestinationPolicy("POSTGRESQL|db.example|5432|sales|sales|tls","10.0.0.0/8",false,h->new InetAddress[]{InetAddress.getByName("8.8.8.8"),InetAddress.getByName(ip)});assertThatThrownBy(()->policy.check(config())).isInstanceOf(GatewayFailure.class);}}
 @Test void privateDestinationsRequireExplicitOperatorNetwork()throws Exception{var deny=new DestinationPolicy("POSTGRESQL|db.example|5432|sales|sales|tls","",false,h->new InetAddress[]{InetAddress.getByName("10.2.3.4")});assertThatThrownBy(()->deny.check(config())).isInstanceOf(GatewayFailure.class);var allow=new DestinationPolicy("POSTGRESQL|db.example|5432|sales|sales|tls","10.2.0.0/16",false,h->new InetAddress[]{InetAddress.getByName("10.2.3.4")});allow.check(config());}
 @Test void changedDnsSetIsFencedUntilOperatorRestart()throws Exception{var answer=new java.util.concurrent.atomic.AtomicReference<>("8.8.8.8");var p=new DestinationPolicy("POSTGRESQL|db.example|5432|sales|sales|tls","",false,h->new InetAddress[]{InetAddress.getByName(answer.get())});p.check(config());answer.set("1.1.1.1");assertThatThrownBy(()->p.check(config())).isInstanceOf(GatewayFailure.class);}
 @Test void rawUrlsPropertiesAndExpandedPortsAreRejected(){var p=new DestinationPolicy("POSTGRESQL|db.example|5432|sales|sales|tls","",false,h->new InetAddress[]{InetAddress.getByName("8.8.8.8")});for(String host:List.of("db.example/evil","db.example?socketFactory=x","jdbc:postgresql://db.example","DB.EXAMPLE")){var c=new HashMap<>(config());c.put("host",host);assertThatThrownBy(()->p.check(c)).isInstanceOf(GatewayFailure.class);}var c=new HashMap<>(config());c.put("port",5433);assertThatThrownBy(()->p.check(c)).isInstanceOf(GatewayFailure.class);}
}
