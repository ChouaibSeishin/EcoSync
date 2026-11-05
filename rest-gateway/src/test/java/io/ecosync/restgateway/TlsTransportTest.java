package io.ecosync.restgateway;
import io.ecosync.restcontract.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class TlsTransportTest {
 @TempDir Path temp;SSLContext context;
 @BeforeEach void certificate()throws Exception{Path store=temp.resolve("test.p12");String tool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();var process=new ProcessBuilder(tool,"-genkeypair","-alias","fixture","-keyalg","EC","-dname","CN=fixture","-ext","SAN=dns:fixture","-validity","2","-storetype","PKCS12","-keystore",store.toString(),"-storepass","ephemeral-test-passphrase","-noprompt").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();assertThat(process.waitFor(20,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();var keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){keys.load(in,"ephemeral-test-passphrase".toCharArray());}var managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(keys,"ephemeral-test-passphrase".toCharArray());var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(keys);context=SSLContext.getInstance("TLS");context.init(managers.getKeyManagers(),trust.getTrustManagers(),null);}
 void run(boolean trusted,boolean matching)throws Exception{run(trusted,matching,false);}
 void run(boolean trusted,boolean matching,boolean redirect)throws Exception{var fixture=new SocketTransportTest();var address=fixture.address();try(var server=(SSLServerSocket)context.getServerSocketFactory().createServerSocket(0,4,address);var trap=new ServerSocket(0,4,address);var pool=Executors.newVirtualThreadPerTaskExecutor();var operations=new Operations();var op=operations.begin(UUID.randomUUID(),"tls")){
  String host=matching?"fixture":"different";try(var policy=new DestinationPolicy("https://"+host+":"+server.getLocalPort()+"|tls",address.getHostAddress()+"/32",false,h->new InetAddress[]{address})){
   var received=new java.util.concurrent.atomic.AtomicBoolean();var source=pool.submit(()->{try(var socket=server.accept()){socket.setSoTimeout(5000);var reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));String first=reader.readLine();if(first!=null)received.set(true);while(first!=null&&!first.isEmpty())first=reader.readLine();if(redirect)socket.getOutputStream().write(("HTTP/1.1 302 Found\r\nLocation: http://"+address.getHostAddress()+":"+trap.getLocalPort()+"/steal\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));else socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 11\r\n\r\n{\"ok\":true}".getBytes(StandardCharsets.US_ASCII));}catch(IOException expected){if(trusted&&matching)throw expected;}return true;});var config=new HashMap<>(fixture.config(server.getLocalPort()));config.put("baseUrl","https://"+host+":"+server.getLocalPort());var transport=trusted?new PinnedHttp(policy,context.getSocketFactory()):new PinnedHttp(policy);
   if(trusted&&matching&&!redirect)assertThat(transport.get(config,Map.of(),op,()->{}).get("status")).isEqualTo(200);else assertThatThrownBy(()->transport.get(config,Map.of(),op,()->{})).isInstanceOf(RestFailure.class).extracting(e->((RestFailure)e).code).isEqualTo(redirect?"REST_REDIRECT_DENIED":"REST_TLS_FAILED");if(redirect){trap.setSoTimeout(200);assertThatThrownBy(trap::accept).isInstanceOf(SocketTimeoutException.class);}source.get(8,TimeUnit.SECONDS);assertThat(received.get()).isEqualTo(trusted&&matching);
  }
 }}
 @Test void pinnedTlsUsesOriginalHostnameAndVerifiedTrust()throws Exception{run(true,true);}
 @Test void mismatchedHostnameFailsBeforeCredentialsAreSent()throws Exception{run(true,false);}
 @Test void httpsToHttpRedirectNeverOpensOrLeaksToAnotherOrigin()throws Exception{run(true,true,true);}
 @Test void untrustedCertificateFailsBeforeCredentialsAreSent()throws Exception{run(false,true);}
}
