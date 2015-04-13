import java.io.*;
import java.net.*;

public class SoundClient { 

  private String host;
  private int port;

  private static String defaultHost = "localhost";
  private static int defaultPort = 789;

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
  }

  public void connectTcp() { 

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

  public void setUpTcpIo() { 

    // Set up I/O over socket 

    try { 
      isr = new InputStreamReader(sock.getInputStream());
      br = new BufferedReader(isr);
      pw = new PrintWriter(sock.getOutputStream(), true);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  public int getId() { 
    return 0;
  }

  public void requestAndSetId() { 

  }
  
  public static void main(String[] args) { 
    SoundClient sc = new SoundClient();  
    sc.connectTcp();
  }
}
