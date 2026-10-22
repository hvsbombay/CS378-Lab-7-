import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;


public class VN {
    public static void main(String[] args) throws IOException{
        if(args.length !=3){
            System.out.println("Usage: java VN <ON_IP> <VN_IP> <VN_UDP_PORT>");
            return ;
        }

        String ON_IP= args[0];
        String VN_IP=args[1];
        int VN_UDP_PORT=Integer.parseInt(args[2]);

        InetAddress ON_Addr=InetAddress.getByName(ON_IP);
        InetAddress VN_Addr=InetAddress.getByName(VN_IP);
        if(VN_UDP_PORT<1 || VN_UDP_PORT>65535){
            System.out.println("invalid UDP port");
            return ;
        }
        System.out.println("ON IP: " + ON_Addr.getHostAddress());
        System.out.println("VN IP: " + VN_Addr.getHostAddress());
        System.out.println("VN UDP Port: " + VN_UDP_PORT);
        Selector selector = Selector.open();
        DatagramChannel UDP_Channel= DatagramChannel.open();
        UDP_Channel.configureBlocking(false);
        UDP_Channel.bind(new InetSocketAddress(VN_Addr,VN_UDP_PORT));
        UDP_Channel.register(selector,SelectionKey.OP_READ);
        SocketChannel TCP_Channel = SocketChannel.open();
        TCP_Channel.configureBlocking(false);
        TCP_Channel.connect(new InetSocketAddress(ON_Addr,5050));
        TCP_Channel.register(selector, SelectionKey.OP_CONNECT);
            
        
    }
}

