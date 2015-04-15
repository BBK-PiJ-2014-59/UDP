import java.net.*;
import java.io.*;
import java.util.*;
import static util.SoundUtil.*;

public class SoundServerThread extends Thread { 

  private static String programName = "SoundServerThread";

  private Socket tcpSocket;
  private Integer tcpClientId;
  private String clientRole;

  private BufferedReader br;
  private PrintWriter pw;
  private InputStreamReader isr;

  private DatagramSocket udpSocket;
  private Integer udpPort;
  private boolean udpIsUp;

  SoundServerThread(Socket s, int id, int port) { 
    tcpSocket = s;
    tcpClientId = id; 
    udpPort = port;
    clientRole = "sender";
    udpIsUp = false;
    log("Initialized to listen on UDP port " + udpPort);
  }

  public void run() { 
    setUpTcpIo();
    expectAndSend("ID", tcpClientId.toString());
    expectAndSend("ROLE", clientRole);
    expectAndSend("UDP_PORT", udpPort.toString());
    setUpUdp();

    // test UDP

    byte[] buffer = new byte[1000];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length); 
    try { 
      udpSocket.receive(packet);
    } catch (IOException e) { 
      e.printStackTrace();
    }
    System.out.println(new String(packet.getData()));


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



  private static void log(String msg) {
    logger(programName, msg);
  }

}
