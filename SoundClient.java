import java.io.*;
import java.net.*;

import static util.SoundUtil.*;

public class SoundClient { 

  private static String clientName = "SoundClient";

  private String host;
  private int port;

  private static String defaultHost = "localhost";
  private static int defaultPort = 789;

  int id;
  private static int defaultId = 0;

  BufferedReader br;
  PrintWriter pw;
  Socket sock;
  InputStreamReader isr;

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

    try { 
      isr = new InputStreamReader(sock.getInputStream());
      br = new BufferedReader(isr);
      //pw = new PrintWriter(sock.getOutputStream(), true); // true autoFlushes output buffer
      pw = new PrintWriter(sock.getOutputStream());
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  private int getId() { 
    return id;
  }

  private void requestAndSetId() { 
    log("Requesting ID from server.");
    if (pw != null) { 
      pw.println("ID");   
      try { 
        id = Integer.parseInt(br.readLine()); // todo: make sure this is an int
      } catch (IOException e) { 
        e.printStackTrace(); 
      }
      log("ID received: " + getId());
    } else { 
      log("Can't get ID - no connection with server.");
    }
  }
  
  private static void log(String msg) { 
    logger(clientName, msg);
  }

  public static void main(String[] args) { 
    SoundClient soundClient = new SoundClient();  
    soundClient.connectTcp();
    soundClient.requestAndSetId();


  }
}
