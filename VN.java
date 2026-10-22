import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
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
            UDP_Socket.setSoTimeout(100);
            System.out.println("binding is done");

            TCP_Socket = new Socket();
            TCP_Socket.connect(new InetSocketAddress(ON_Addr, 5050));
            TCP_Socket.setSoTimeout(100);
            System.out.println("Connected to ON using TCP.");
            DataOutputStream out = new DataOutputStream(TCP_Socket.getOutputStream());
            out.writeByte(1); //Byte 0: message type
            byte[] ipBytes=((Inet4Address)VN_Addr).getAddress();
            out.write(ipBytes); // Bytes 1–4: VN IPv4 address
            out.writeShort(VN_UDP_PORT); // Bytes 5–6: VN UDP port
            out.flush();
            System.out.println("CONNECT message sent to ON");
            DataInputStream in = new DataInputStream(TCP_Socket.getInputStream());
            byte[] tcpBuffer = new byte[2048];
            int tcpCount = 0;
            byte[] udpBuffer = new byte[2048];
            TCP_Socket.setSoTimeout(10);
            UDP_Socket.setSoTimeout(10);
            // Read the LINK-STATE header
            while (true) {
                int nextbyte;
                try{
                    nextbyte=in.read();
                }
                catch(SocketTimeoutException e){
                    nextbyte=-2;
                }
                if (nextbyte==-1){
                    System.out.println("ON disconnected");
                    break;
                }
                if(nextbyte>=0){
                    if(tcpCount>=tcpBuffer.length){
                        System.err.println("tcp essage bffer fll");
                        break;
                    }
                    tcpBuffer[tcpCount]= (byte)nextbyte;
                    tcpCount++;
                }
                while (tcpCount>=2) {
                    int messageType = tcpBuffer[0] & OxFF;
                    int tupleCount= tcpBuffer[1] & OxFF;
                    if (messageType!=2){
                        System.err.println("Unexpected message type: " +messageType);
                        return ;
                    }
                    if (tupleCount>26){
                        System.err.println("inavalid tplecont"+ tupleCount);
                        return ;
                    }
                    int messagelen=2+tupleCount*11;
                    if(tcpCount<messagelen){
                        break;
                    }
                    System.out.println("Received LINK-STATE from ON.");
                    System.out.println("Number of tuples: " + tupleCount);
                    int pos=2;
                    for(int i=0 ; i<tupleCount ;i++){
                        char NodeId= (char)(tcpBuffer[pos] & 0xFF);
                        pos++;
                        byte[] ipBytesRecieved =new byte[4];
                        System.arraycopy(tcpBuffer, pos, ipBytesRecieved, 0, 4);
                        InetAddress NodeAddr =InetAddress.getByAddress(ipBytesRecieved);
                        pos+=4;
                        int NodePort= ((tcpBuffer[pos]& 0xFF)<<8) | (tcpBuffer[pos+1]& 0xFF);
                        pos+=2;
                        int cost= ByteBuffer.wrap(tcpBuffer,pos,4).getInt();
                        pos+=4;
                        System.out.println("Node: " + NodeId + ", IP: " + NodeAddr.getHostAddress() +", UDP Port: " + NodePort +", Cost: " + cost);
                    }
                    int restBytes=tcpCount-messagelen;
                    System.arraycopy(tcpBuffer, messagelen, tcpBuffer, 0, restBytes);
                    tcpCount=restBytes;
                }
                try{
                    DatagramPacket packet= new DatagramPacket(udpBuffer,udpBuffer.length);
                    UDP_Socket.receive(packet);
                    System.out.println("Received a UDP packet from " +packet.getAddress().getHostAddress() +":" + packet.getPort());
                }
                catch(SocketTimeoutException e){}
            }
        }
        catch(SocketTimeoutException e){}
        catch(SocketException e){
            System.err.println("Failed to bind VN UDP socket: " + e.getMessage());
            return ;
        }
        finally{
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

