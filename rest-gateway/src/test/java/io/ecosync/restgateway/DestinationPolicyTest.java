package io.ecosync.restgateway;
import io.ecosync.restcontract.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DestinationPolicyTest {
 static final String ENTRY="http://fixture:8095/directory|dev";
 DestinationPolicy policy(DestinationPolicy.Resolver resolver){return new DestinationPolicy(ENTRY,"172.30.0.0/24",true,resolver);}
 @Test void exactOperatorPolicyCannotBeExpanded(){for(String url:List.of("http://127.0.0.1:8095/directory","http://[::1]:8095/directory","http://fixture:8096/directory","http://user@fixture:8095/directory","http://fixture:8095/directory/../x","http://fixture:8095/directory%2fsecret","http://fixture:8095/directory?x=1","http://fixture:8095/directory#x","http://fixture.:8095/directory","HTTP://fixture:8095/directory","file://fixture/directory","http://fixture:8095//directory"))try(var p=policy(h->new InetAddress[]{InetAddress.getByName("172.30.0.2")})){assertThatThrownBy(()->p.target(url)).isInstanceOf(RestFailure.class);}}
 @Test void fullSetRejectsReservedPrivateAndMetadataAddresses()throws Exception{for(String bad:List.of("127.0.0.1","0.0.0.0","169.254.169.254","224.0.0.1","240.0.0.1","10.0.0.1","100.64.0.1","192.0.2.1","198.18.0.1","::","::1","fe80::1","ff02::1","fc00::1","2001:db8::1","2002::1"))try(var p=policy(h->new InetAddress[]{InetAddress.getByName("172.30.0.2"),InetAddress.getByName(bad)})){assertThatThrownBy(()->p.resolve(p.target("http://fixture:8095/directory"))).isInstanceOf(RestFailure.class);}}
 @Test void approvedPrivateAddressesArePinnedAndChangedAnswersFail(){var call=new AtomicInteger();try(var p=policy(h->new InetAddress[]{InetAddress.getByName(call.incrementAndGet()<3?"172.30.0.2":"172.30.0.3")})){var target=p.target("http://fixture:8095/directory");assertThat(p.resolve(target).getHostAddress()).isEqualTo("172.30.0.2");p.resolve(target);assertThatThrownBy(()->p.resolve(target)).isInstanceOf(RestFailure.class);}}
 @Test void failedDnsAndEmptySetsFailClosed(){for(DestinationPolicy.Resolver r:List.<DestinationPolicy.Resolver>of(h->{throw new UnknownHostException();},h->new InetAddress[0]))try(var p=policy(r)){assertThatThrownBy(()->p.resolve(p.target("http://fixture:8095/directory"))).isInstanceOf(RestFailure.class);}}
 @Test void productionCannotOptIntoHttpWithDevelopmentEntry(){try(var p=new DestinationPolicy(ENTRY,"172.30.0.0/24",false)){assertThatThrownBy(()->p.target("http://fixture:8095/directory")).isInstanceOf(RestFailure.class);}}
 @Test void mixedIpv6AndIpv4AddressesAreAllValidated()throws Exception{try(var p=policy(h->new InetAddress[]{InetAddress.getByName("172.30.0.2"),InetAddress.getByName("::ffff:127.0.0.1")})){assertThatThrownBy(()->p.resolve(p.target("http://fixture:8095/directory"))).isInstanceOf(RestFailure.class);}}
}
