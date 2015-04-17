import java.net.*;
import java.io.*;
import java.util.*;
import static util.SoundUtil.*;

public class SoundServerThread extends Thread { 

  private static String programName = "SoundServerThread";

  private Socket tcpSocket;
  private Integer tcpClientId;
  private ClientRole clientRole;
  private boolean isFirstClient;

  private BufferedReader br;
  private PrintWriter pw;
  private InputStreamReader isr;

  private DatagramSocket udpSocket;
  private Integer udpPort;
  private boolean udpIsUp;

  private enum ClientRole { 
    SENDER,
    RECEIVER
  }

  private enum Request { 
    ID,
    ROLE,
    UDP_PORT
  }

  private MulticastSocket mcSocket; 
  private InetAddress mcGroup;
  private static String mcAddress = "224.111.111.111";
  private static int mcPort = 10000;

  SoundServerThread(Socket s, int id, int port, boolean isFirst) { 
    tcpSocket = s;
    tcpClientId = id; 
    udpPort = port;
    isFirstClient = isFirst;
    clientRole = isFirstClient ? ClientRole.SENDER : ClientRole.RECEIVER;   
    udpIsUp = false;

    log("Initialized to listen on UDP port " + udpPort);
  }

  public void run() { 
    setUpTcpIo();
    expectAndSend(Request.ID.toString(), tcpClientId.toString());
    expectAndSend(Request.ROLE.toString(), clientRole.toString());
    expectAndSend(Request.UDP_PORT.toString(), udpPort.toString());
    setUpUdp();

    // test UDP

    byte[] buffer = new byte[1000];
    DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length); 
    try { 
      udpSocket.receive(udpPacket);
    } catch (IOException e) { 
      e.printStackTrace();
    }
    System.out.println(new String(udpPacket.getData()));

    // Send/receive via multicast


    // test multicast send

    if (clientRole == ClientRole.SENDER) { 
      setUpMulticast();
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
  }

  private void setUpMulticast() {
    log("Setting up multicast.");
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

  private void expectAndSend(String expected, String sendThis) { 
    String request = null;
    log("Waiting for '" + expected + "' request from client.");
    request = tcpListen();
    log("Message from client received.");
    if (request.startsWith(expected)) {
      log("Message from client was as expected.");
      pw.println(sendThis);
      log("Sent this " + expected + " to client: " + sendThis);
    } else {
      log("Client sent this instead of '" + expected + "': " + request);
    }
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

  private void setUpUdp() {
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
