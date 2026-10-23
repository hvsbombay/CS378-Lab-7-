import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
public class VN {
    static final int ON_port= 5000;
    static final int N=26;
    static final int [][]graph= new int[N][N];
    static final int []lastseq= new int[N];
    static char ownnodeId ='?';
    static int seqNo=0, nbrCount=0;
    static final char[] nbrIds= new char[N];
    static final int[] nbrcosts = new int[26];
    static final int[] nbrPorts = new int[N];
    static final InetAddress[] nbrIPs = new InetAddress[26];
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
        for (int i=0; i<N; i++){
            Arrays.fill(graph[i],-2);
            lastseq[i]=-1;
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
            
            long lastPeriodicBroadcast= System.currentTimeMillis();
            // Read the LINK-STATE header
            while (true) {
                try{
                    int nextbyte=in.read();
                    if (nextbyte==-1){
                        System.out.println("ON disconnected");
                        break;
                    }
                    if(tcpCount>=tcpBuffer.length){
                        tcpBuffer[tcpCount++] = (byte) nextbyte;
                    }
                }
                catch(SocketTimeoutException e){}

                while (tcpCount>=2) {
                    int messageType = tcpBuffer[0] & 0xFF;
                    int tupleCount= tcpBuffer[1] & 0xFF;
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
                    nbrCount=0;
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

                        if(cost==0){
                            ownnodeId=NodeId;
                        }else{
                            nbrIds[nbrCount]= NodeId;
                            nbrIPs[nbrCount]=NodeAddr;
                            nbrPorts[nbrCount]= NodePort;
                            nbrcosts[nbrCount]= cost;
                            nbrCount++;
                        }
                    }

                    int selfIdx=ownnodeId-'A';
                    graph[selfIdx][selfIdx]=0;
                    for(int i =0 ; i<nbrCount;i++){
                        graph[selfIdx][nbrIds[i]-'A'] =nbrcosts[i];
                    }
                    broadcastLSP(UDP_Socket);
                    int restBytes=tcpCount-messagelen;
                    System.arraycopy(tcpBuffer, messagelen, tcpBuffer, 0, restBytes);
                    tcpCount=restBytes;
                }
                try{
                    DatagramPacket packet= new DatagramPacket(udpBuffer,udpBuffer.length);
                    UDP_Socket.receive(packet);
                    byte[] data= packet.getData();
                    int offset=packet.getOffset();
                    int len=packet.getLength();
                    if(len<6){
                        System.out.println("ignore incoplete lsp");
                    }
                    else{
                        char originId= (char)(data[offset]& 0xFF);
                        int originIndex=originId-'A';
                        int inseq= ByteBuffer.wrap(data, offset+1,4).getInt();
                        int lspnbrcnt=data[offset+5]& 0xFF;
                        int expectedlen=6+lspnbrcnt*5;
                        if (originIndex<0 || originIndex>=26 || lspnbrcnt>25 || len!=expectedlen){
                            System.out.println("Ignoring invalid lsp");
                        }
                        else{
                            char[] recvnbrIds=new char[lspnbrcnt];
                            int[] recvnbrCosts=new int[lspnbrcnt];
                            int pos=offset+6;
                            boolean valid=true;
                            for(int i=0;i<lspnbrcnt;i++){
                                char nbrId= (char)(data[pos]& 0xFF);
                                pos++;
                                int nbrIndex=nbrId-'A';
                                int cost=ByteBuffer.wrap(data,pos,4).getInt();
                                pos+=4;
                                if(nbrIndex<0 || nbrIndex>=26 || originIndex==nbrIndex){
                                    valid=false;
                                    break;
                                }
                                recvnbrIds[i]=nbrId;
                                recvnbrCosts[i]=cost;
                            }

                            if(valid && inseq>lastseq[originIndex]){
                                lastseq[originIndex]=inseq;
                                Arrays.fill(graph[originIndex],-1);
                                graph[originIndex][originIndex]=0;
                                for(int i=0;i<lspnbrcnt;i++){
                                    graph[originIndex][recvnbrIds[i]-'A']=recvnbrCosts[i];
                                }
                                System.out.println("Accepted LSP from " + originId + ", seqNo: " + inseq);
                            }
                        }
                    }
                }
                catch(SocketTimeoutException e){}
                if(System.currentTimeMillis()-lastPeriodicBroadcast> 15000){
                    broadcastLSP(UDP_Socket);
                    printGraph();
                    lastPeriodicBroadcast= System.currentTimeMillis();
                }
            }
        }
        catch(SocketException e){
            System.err.println("Socket error: " + e.getMessage());
        }
        catch(IOException e){
            System.err.println("I/O error: " + e.getMessage());
        }
        finally{
            if (TCP_Socket != null) {
                TCP_Socket.close();
            }
            if (UDP_Socket!=null){
                UDP_Socket.close();
            }
        }
    }
    
    private static void broadcastLSP(DatagramSocket udpSocket) throws IOException{
        if(ownnodeId=='?') return ;
        ByteBuffer lsp= ByteBuffer.allocate(6+nbrCount*5);
        lsp.put((byte)ownnodeId);
        lsp.putInt(++seqNo);
        lsp.put((byte)nbrCount);
        for(int i=0;i<nbrCount;i++){
            lsp.put((byte)nbrIds[i]);
            lsp.putInt(nbrcosts[i]);
        }
        byte[] lspBytes=lsp.array();

        for(int i=0;i<nbrCount;i++){
            DatagramPacket lspPacket=new DatagramPacket(lspBytes,lspBytes.length,nbrIPs[i],nbrPorts[i]);
            udpSocket.send(lspPacket);
        }
        System.out.println("Broadcasted LSP seq " + seqNo);
    }

    private static void printGraph(){
        System.out.println("----Current Graph----");
        for(int i=0;i< N;i++){
            boolean hasLinks = false;
            StringBuilder sb = new StringBuilder();
            sb.append((char) ('A' + i)).append(": ");
            for(int j = 0;j<N;j++){
                if(graph[i][j] >= 0){
                    sb.append((char) ('A' +j)).append("=").append(graph[i][j]).append(" ");
                    hasLinks = true;
                }
            }
            if(hasLinks){
                System.out.println(sb.toString().trim());
            }
        }
        System.out.println("--------------------------------------");
    }
}


