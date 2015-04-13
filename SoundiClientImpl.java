import java.io.*;
import java.net.*;

public class SoundiClientImpl implements SoundiClient { 

  private String host;
  private int port;

  private String defaultHost = "localhost";
  private int defaultPort = "789";

  BufferedReader br;
  PrintWriter pw;
  Socket sock;
  InputStreamReader isr;

  public SoundiClientImpl() {
    this(defaultHost, defaultPort);
  }

  public SoundiClientImpl(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public void connectTcp() { 
    try { 
      sock = new Socket(host, port);
    } catch (IOException e) { // problem setting up socket 
      e.printStackTrace();
    } catch (UnknownHostException e) { // problem with host 
      e.printStackTrace();
    }

    try { 
      isr = new InputStreamReader(sock.getInputStream());
      br = new BufferedReader(isr);
      pw = new PrintWriter(sock.getOutPutStream(), true);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  public int getId() { 
    return 0;
  }

  public void requestAndSetId() { 

  }
}
