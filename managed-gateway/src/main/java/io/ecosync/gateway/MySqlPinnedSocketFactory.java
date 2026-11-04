package io.ecosync.gateway;
import com.mysql.cj.conf.PropertySet;
import com.mysql.cj.protocol.StandardSocketFactory;
import java.io.IOException;
import java.net.Socket;
public final class MySqlPinnedSocketFactory extends StandardSocketFactory {
 private String host;private int port;
 @Override public <T extends java.io.Closeable>T connect(String hostname,int port,PropertySet props,int loginTimeout)throws IOException{this.host=hostname;this.port=port;return super.connect(hostname,port,props,loginTimeout);}
 @Override protected Socket createSocket(PropertySet props){return PinnedSockets.socket(host,port);}
}
