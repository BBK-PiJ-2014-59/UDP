import java.net.*;
import java.io.*;
import java.util.*;

import static util.SoundUtil.*;

public class SoundServer { 
  
  private static String serverName = "SoundServer";

  private int defaultPort;
  private ServerSocket serverSocket;
  private Socket socket;
  private final int firstTcpId;
  private int nextTcpId;

  BufferedReader br;
  PrintWriter pw;
  InputStreamReader isr;

  public SoundServer() { 
    defaultPort = 789;
    firstTcpId = 100;
    nextTcpId = firstTcpId;
  }

  private int nextTcpId() { 
    return nextTcpId++; 
  }
  void start() throws IOException { 
    log("Creating TCP socket.");
    serverSocket = new ServerSocket(defaultPort); 
    log("Listening for TCP client.");
    socket = serverSocket.accept();
    log("Connection with client established.");
    setUpTcpIo();
    sendClientId();
  }

  void setUpTcpIo() { 
    log("Setting up TCP IO stream with client.");
    try { 
      isr = new InputStreamReader(socket.getInputStream());
      br = new BufferedReader(isr);
      pw = new PrintWriter(socket.getOutputStream());
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  void sendClientId() { 
    log("Waiting for ID request from client.");
    try { 
      String cmd = br.readLine();
    } catch (IOException e) { 
      e.printStackTrace();
    }
    log("ID request from client received.");
    String request = null;
    int id = nextTcpId();
    if (request == "ID") { 
      pw.println(id);
      log("Sent this ID to client: " + id);
    } else { 
      log("Expected ID request from client but got this instead: " + request);
    }
  }

  static void log(String msg) { 
    logger(serverName, msg);
  }

  public static void main(String[] args) { 
    SoundServer soundServer = new SoundServer();
    try { 
      soundServer.start();
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }
}
