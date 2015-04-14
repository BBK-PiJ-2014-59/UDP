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

  private BufferedReader br;
  private PrintWriter pw;
  private Socket sock;
  private InputStreamReader isr;

  public SoundClient() {
    this(defaultHost, defaultPort);
  }

  public SoundClient(String host, int port) {
    this.host = host;
    this.port = port;
    id = defaultId;
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

  private void requestAndSetId() { 
    if (pw != null) { 
      String request = "ID";
      log("Requesting " + request + " from server.");
      pw.println(request);   
      String reply = null;
      try { 
        reply = br.readLine();
      } catch (IOException e) { 
        e.printStackTrace(); 
      }
      if (reply != null) { 
        id = Integer.parseInt(reply); 
        log(request + " received: " + getId());
      } else { 
        log("Got null reply from server when requesting " + request);
      }
    } else { 
      log("Can't request ID - no IO stream set up with server.");
    }
  }
  
  private static void log(String msg) { 
    logger(clientName, msg);
  }

  public static void main(String[] args) { 
    SoundClient soundClient = new SoundClient();  
    soundClient.connectTcp();
    soundClient.setUpTcpIo();
    soundClient.requestAndSetId();
    //soundClient.requestAndSetRole();


  }
}
