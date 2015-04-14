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
      pw = new PrintWriter(socket.getOutputStream(), true);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  void sendClientId() { 
    String expected = "ID";
    String request = null;
    log("Waiting for '" + expected + "' request from client.");
    try { 
      request = br.readLine();
    } catch (IOException e) { 
      e.printStackTrace();
    }
    log("Message from client received.");
    int id = nextTcpId();
    if (request.startsWith(expected)) { 
      log("Message from client was as expected.");
      pw.println(id);
      log("Sent this ID to client: " + id);
    } else { 
      log("Client sent this instead of '" + expected + "': " + request);
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
