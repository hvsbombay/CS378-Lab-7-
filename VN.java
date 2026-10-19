import java.net.*;
import java.io.*;

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
        DatagramSocket UDP_Socket= null;
        Socket TCP_Socket= null;
        try{
            UDP_Socket= new DatagramSocket(null);
            UDP_Socket.bind(new InetSocketAddress(VN_Addr,VN_UDP_PORT));
            System.out.println("binding is done");

            TCP_Socket = new Socket();
            TCP_Socket.connect(new InetSocketAddress(ON_Addr, 5000));
            System.out.println("Connected to ON using TCP.");
            DataOutputStream out = new DataOutputStream(TCP_Socket.getOutputStream());
            out.writeByte(1); //Byte 0: message type
            byte[] ipBytes=((Inet4Address)VN_Addr).getAddress();
            out.write(ipBytes); // Bytes 1–4: VN IPv4 address
            out.writeShort(VN_UDP_PORT); // Bytes 5–6: VN UDP port
            out.flush();
            System.out.println("CONNECT message sent to ON");
            DataInputStream in = new DataInputStream(TCP_Socket.getInputStream());
            // Read the LINK-STATE header
            int messageType = in.readUnsignedByte();
            int tupleCount= in.readUnsignedByte();
            if (messageType!=2){
                System.err.println("Unexpected message type: " +messageType);
                return ;
            }
            System.out.println("Received LINK-STATE from ON.");
            System.out.println("Number of tuples: " + tupleCount);
            for(int i=0 ; i<tupleCount ;i++){
                char NodeId= (char) in.readUnsignedByte();

                byte[] ipBytesRecieved =new byte[4];
                in.readFully(ipBytesRecieved);
                InetAddress NodeAddr =InetAddress.getByAddress(ipBytesRecieved);

                int NodePort=in.readUnsignedShort();

                int cost= in.readInt();
                System.out.println("Node: " + NodeId + ", IP: " + NodeAddr.getHostAddress() +
                                    ", UDP Port: " + NodePort +", Cost: " + cost);
            }
        }catch(SocketException e){
            System.err.println("Failed to bind VN UDP socket: " + e.getMessage());
            return ;
        }finally{
            if (TCP_Socket != null) {
                try {
                    TCP_Socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing TCP socket.");
                }
            }
            if (UDP_Socket!=null){
                UDP_Socket.close();
            }
        }
    }
}

