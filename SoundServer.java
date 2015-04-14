import java.net.*;
import java.io.*;
import java.util.*;

import static util.SoundUtil.*;

public class SoundServer { 
  
  private static String programName = "SoundServer";

  private int defaultPort;
  private ServerSocket serverSocket;
  private final int firstTcpClientId;
  private int nextTcpClientId;

  public SoundServer() { 
    defaultPort = 789;
    firstTcpClientId = 1;
    nextTcpClientId = firstTcpClientId;
  }

  private int nextTcpClientId() { 
    return nextTcpClientId++; 
  }

  void start() throws IOException { 
    log("Creating TCP socket.");
    serverSocket = new ServerSocket(defaultPort); 
    log("Listening for TCP client.");
    while(true) { 
      Socket socket = serverSocket.accept();
      log("Connection with client established.");
      new SoundServerThread(socket, nextTcpClientId()).start();
    }
  }

  static void log(String msg) { 
    logger(programName, msg);
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

