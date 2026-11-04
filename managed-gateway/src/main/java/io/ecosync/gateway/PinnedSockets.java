package io.ecosync.gateway;
import java.net.*;
import java.io.IOException;
/** Both drivers retain the original JDBC hostname for verified TLS; only TCP address is pinned. */
public final class PinnedSockets {
 static volatile DestinationPolicy policy;
 public static void install(DestinationPolicy p){policy=p;}
 public static Socket socket(String host,int port){if(policy==null)throw new GatewayFailure("DB_DESTINATION_DENIED");return new Socket(){@Override public void connect(SocketAddress ignored,int timeout)throws IOException{try{super.connect(new InetSocketAddress(policy.resolve(host,port),port),Math.min(timeout<=0?3000:timeout,3000));setSoTimeout(5000);}catch(GatewayFailure e){throw new IOException("Database destination denied",e);}}};}
}
