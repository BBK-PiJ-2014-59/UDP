import java.io.*;
import java.net.*;

import static util.SoundUtil.*;

public class SoundClient { 

  private static String clientName = "SoundClient";

  private String host;
  private int port;

  private static String defaultHost = "localhost";
  private static int defaultPort = 789;

  private int id;
  private static int defaultId = 0;

  private String role; // todo: use enum/check for valid role reply from server.

  private BufferedReader br;
  private PrintWriter pw;
  private Socket sock;
  private InputStreamReader isr;

  private DatagramSocket udpSock;
  private InetAddress udpHost;
  private int udpPort;

  public SoundClient() {
    this(defaultHost, defaultPort);
  }

  public SoundClient(String host, int port) {
    this.host = host;
    this.port = port;
    id = defaultId;
    role = null;
  }

  public static void main(String[] args) { 

    SoundClient soundClient = new SoundClient();  

    soundClient.connectTcp();
    soundClient.setUpTcpIo();
    soundClient.requestAndSetId();
    soundClient.requestAndSetRole();
    soundClient.requestAndSetUdpPort();

    soundClient.setUpUdp();
    soundClient.udpSendString("test123");

  }

  private void setUpUdp() { 
    try { 
      udpSock = new DatagramSocket();
    } catch (IOException e) { 
      e.printStackTrace();
    }
    try { 
      udpHost = InetAddress.getByName(defaultHost); // todo: get it working over the network.
    } catch (UnknownHostException e) { 
      e.printStackTrace();
    }
  }

  private void udpSendString(String msg) { 
    log("Sending string via UDP: " + msg );
    byte[] bytes = msg.getBytes();
    DatagramPacket packet = new DatagramPacket(bytes, bytes.length, udpHost, udpPort);
    if (packet == null)
      log("packet null"); 
    try { 
      udpSock.send(packet);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }


  private void connectTcp() { 

    // Set up socket

    log("Setting up TCP connection with server.");
    try { 
      sock = new Socket(host, port);
    } catch (ConnectException e) { // Connection refused
      e.printStackTrace();
    } catch (UnknownHostException e) { // couldn't resolve name. 
      e.printStackTrace();
    } catch (IOException e) { // some other problem setting up socket 
      e.printStackTrace();
    }
  }

  private void setUpTcpIo() { 

    // Set up I/O over socket 

    log("Setting up TCP IO streams with server.");
    try { 
      isr = new InputStreamReader(sock.getInputStream());
      br = new BufferedReader(isr);
      //pw = new PrintWriter(sock.getOutputStream(), true); // true autoFlushes output buffer
      pw = new PrintWriter(sock.getOutputStream(), true);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  private int getId() { 
    return id;
  }

  private String getRole() { 
    return role;
  }

  private int getUdpPort() { 
    return udpPort;
  }

  private void requestAndSetId() { 
    String request = "ID";
    String reply = tcpRequest(request);
    if (reply != null) { 
      id = Integer.parseInt(reply); 
      log(request + " received: " + getId());
    } else { 
      log("Got null reply from server when requesting " + request);
    }
  }

  private void requestAndSetUdpPort() { 
    String request = "UDP_PORT";
    String reply = tcpRequest(request);
    if (reply != null) { 
      udpPort = Integer.parseInt(reply); 
      log(request + " received: " + getUdpPort());
    } else { 
      log("Got null reply from server when requesting " + request);
    }
  }

  private void requestAndSetRole() {  // todo: DRY
    String request = "ROLE";
    String reply = tcpRequest(request);
    if (reply != null) { 
      role = reply; 
      log(request + " received: " + getRole());
    } else { 
      log("Got null reply from server when requesting " + request);
    }
  }

  private String tcpRequest(String request) { 
    String reply = null;
    if (pw != null) { 
      log("Requesting " + request + " from server.");
      pw.println(request);   
      try { 
        reply = br.readLine();
      } catch (IOException e) { 
        e.printStackTrace(); 
      }
    } else { 
      log("Can't request " + request + " - no IO stream set up with server.");
    }
    return reply;
  }


  private static void log(String msg) { 
    logger(clientName, msg);
  }

}
