import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import static util.SoundUtil.*;

public class SoundServerThread extends Thread { 

  private static String programName = "SoundServerThread";

  private Socket tcpSocket;
  private Integer tcpClientId;
  private ClientRoles clientRole;
  private boolean isFirstClient;

  private BufferedReader br;
  private PrintWriter pw;
  private InputStreamReader isr;

  private DatagramSocket udpSocket;
  private Integer udpPort;
  private boolean udpIsUp;

  private enum ClientRoles { 
    SENDER,
    RECEIVER
  }

  private enum ClientRequests { 
    ID,
    ROLE,
    UDP_PORT,
    ACK_LENGTH
  }

  private enum Replies { 
    ACK_LENGTH
  }

  private MulticastSocket mcSocket; 
  private InetAddress mcGroup;
  private static String mcAddress = "224.111.111.111";
  private static int mcPort = 10000;

  private static int udpMaxPayload = 512;
  private int arrayLength; // todo: change variable name?

  SoundServerThread(Socket s, int id, int port, boolean isFirst) { 
    tcpSocket = s;
    tcpClientId = id; 
    udpPort = port;
    isFirstClient = isFirst;
    clientRole = isFirstClient ? ClientRoles.SENDER : ClientRoles.RECEIVER;   
    udpIsUp = false;

    log("Initialized to listen on UDP port " + udpPort);
  }

  public void run() { 
    setUpTcpIo();
    expectAndSend(ClientRequests.ID.toString(), tcpClientId.toString());
    expectAndSend(ClientRequests.ROLE.toString(), clientRole.toString());
    expectAndSend(ClientRequests.UDP_PORT.toString(), udpPort.toString());
    setUpUdpSocket();
    expectAndSetArrayLength();
     

    //if (clientRole == ClientRoles.SENDER) { 
      //setUpMulticastSender();
      //mcTestSend();
    //}

  }


  private void mcTestSend() { //  
    byte[] dummy = new byte[0];
    DatagramPacket mcPacket = new DatagramPacket(dummy, 0, mcGroup, mcPort);
    int i = 0;
    while(true) { 
      ++i;
      byte[] bytes = ("multicast test " + i).getBytes(); 
      mcPacket.setData(bytes);
      mcPacket.setLength(bytes.length);
      try { 
        mcSocket.send(mcPacket);
        log(""+i);
      } catch (IOException e) { 
        e.printStackTrace();
      }
    }
  }

  private void udpReceiveString() { // max length of udpMaxPayload - just for testing at this point

    byte[] bytes = new byte[udpMaxPayload];
    DatagramPacket udpPacket = new DatagramPacket(bytes, bytes.length); 
    try { 
      udpSocket.receive(udpPacket);
    } catch (IOException e) { 
      e.printStackTrace();
    }
    log("Received string: " + new String(udpPacket.getData()));
  }

  private void setUpMulticastSender() {
    log("Setting up multicast sender.");
    try { 
      mcSocket = new MulticastSocket(); 
      mcGroup = InetAddress.getByName(mcAddress);
    } catch (UnknownHostException e) { 
      e.printStackTrace();
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  private void setUpTcpIo() {
    log("Setting up TCP IO stream with client.");
    try {
      isr = new InputStreamReader(tcpSocket.getInputStream());
      br = new BufferedReader(isr);
      pw = new PrintWriter(tcpSocket.getOutputStream(), true);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void expectAndSetArrayLength() { 
    String request = expectAndSend(ClientRequests.ACK_LENGTH.toString(), Replies.ACK_LENGTH.toString()); // todo: check length ok before ack?
    log("Received request: " + request);
    Pattern p = Pattern.compile("\\d+");
    Matcher m = p.matcher(request);
    m.find();
    setArrayLength(Integer.parseInt(m.group(0)));
    log("Array length set to " + getArrayLength());
  }

  private void setArrayLength(int len) { 
    arrayLength = len; 
  }

  private int getArrayLength() { 
    return arrayLength; 
  }

  private String expectAndSend(String expected, String sendThis) { 
    String request = null;
    log("Waiting for '" + expected + "' request from client.");
    request = tcpListen();
    log("Message from client received.");
    if (request.startsWith(expected)) {
      log("Message from client was as expected.");
      pw.println(sendThis);
      log("Sent this to client: " + sendThis);
    } else {
      log("Client sent this instead of '" + expected + "': " + request);
    }
    return request; 
  }

  private String tcpListen() { 
    String msg = null;
    try {
      msg = br.readLine();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return msg;
  }

  private void setUpUdpSocket() {
    if (!udpIsUp) { 
      try {
        udpSocket = new DatagramSocket(udpPort);
        udpIsUp = true;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }



  private void log(String msg) {
    logger(programName + "-" + getId(), msg);
  }

}
