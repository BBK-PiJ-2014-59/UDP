import java.net.*;
import java.io.*;
import java.util.*;

import static util.SoundUtil.*;

public class SoundServer { 
  
  private static String programName = "SoundServer";

  private int defaultTcpPort;
  private ServerSocket serverSocket;
  private final int firstTcpClientId;
  private int nextTcpClientId;
  private final static int firstUdpPort = 42001;
  private int nextUdpPort;
  private boolean isFirstClient;

  public SoundServer() { 
    defaultTcpPort = 789;
    firstTcpClientId = 1;
    nextTcpClientId = firstTcpClientId;
    nextUdpPort = firstUdpPort;
    isFirstClient = true;
  }

  private int nextTcpClientId() { 
    return nextTcpClientId++; 
  }

  private int nextUdpPort() { 
    return nextUdpPort++; 
  }

  void start() throws IOException { 
    log("Creating TCP socket.");
    serverSocket = new ServerSocket(defaultTcpPort); 
    log("Listening for TCP client.");

    // A thread handles each client. First client will end up sender.

    Socket socket = serverSocket.accept();
    log("Connection with client established.");
    new SoundServerThread(socket, nextTcpClientId(), nextUdpPort(), isFirstClient).start();

    isFirstClient = false;

    // Subsequent clients will be receivers (at least start out that way).

    while(true) { 
      socket = serverSocket.accept();
      log("Connection with client established.");
      new SoundServerThread(socket, nextTcpClientId(), nextUdpPort(), isFirstClient).start();
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

