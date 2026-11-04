package io.ecosync.gateway;
import javax.net.SocketFactory;
import java.net.*;
import java.io.IOException;
public final class PostgresPinnedSocketFactory extends SocketFactory {
 private final String host;private final int port;
 public PostgresPinnedSocketFactory(String value){String[] p=value.split(":");if(p.length!=2)throw new GatewayFailure("DB_DESTINATION_DENIED");host=p[0];port=Integer.parseInt(p[1]);}
 @Override public Socket createSocket(){return PinnedSockets.socket(host,port);}
 @Override public Socket createSocket(String h,int p)throws IOException{var s=createSocket();s.connect(new InetSocketAddress(h,p),3000);return s;}
 @Override public Socket createSocket(InetAddress h,int p)throws IOException{return createSocket(h.getHostAddress(),p);}
 @Override public Socket createSocket(String h,int p,InetAddress local,int lp)throws IOException{var s=createSocket();s.bind(new InetSocketAddress(local,lp));s.connect(new InetSocketAddress(h,p),3000);return s;}
 @Override public Socket createSocket(InetAddress h,int p,InetAddress local,int lp)throws IOException{return createSocket(h.getHostAddress(),p,local,lp);}
}
