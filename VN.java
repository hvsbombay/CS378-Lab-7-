import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.Socket;
import java.io.IOException;

public class VN {
    public static void main(String[] args){
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
        DatagramSocket UDP_Socket= null;
        try{
            UDP_Socket= new DatagramSocket(null);
            UDP_Socket.bind(new InetSocketAddress(VN_Addr,VN_UDP_PORT));
            System.out.println("binding is done");
        }catch(SocketException e){
            System.err.println("Failed to bind VN UDP socket: " + e.getMessage());
            return ;
        }finally{
            if (UDP_Socket!=null){
                UDP_Socket.close();
            }
        }
    }
}

