import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

  private static boolean fair = true;
  //private static boolean fair = false;
  private ReentrantReadWriteLock lock;

  // ServerThread "1" receives audio from sender client into the soundBytes array shared between the ServerThreads. ServerThread "1" (which handles the sender SoundClient) needs to lock it for writing before sending happens. Locking prevents the other ServerThreads from reading the array, if we use a ReentrantReadWriteLock(fair) lock, which should also guarantee that once the other ServerThreads have given up their (read) lock, and ServerThread "1" has been waiting the longest, it will get the lock and be able to write (again).

  private byte[] soundBytes = null; 
  private ByteArrayOutputStream byteStream;

  public SoundServer() { 
    defaultTcpPort = 789;
    firstTcpClientId = 1;
    nextTcpClientId = firstTcpClientId;
    nextUdpPort = firstUdpPort;
    isFirstClient = true;
    lock = new ReentrantReadWriteLock(fair);
    byteStream = new ByteArrayOutputStream();
  }

  private int nextTcpClientId() { 
    return nextTcpClientId++; 
  }

  private int nextUdpPort() { 
    return nextUdpPort++; 
  }

  void start() throws IOException { 
    log("Starting SoundServer");
    log("Creating TCP socket.");
    serverSocket = new ServerSocket(defaultTcpPort); 
    log("Listening for TCP client.");

    // A thread handles each client. First client will end up sender.

    Socket socket = serverSocket.accept();
    log("Connection with first client established. This client will be the sender.");
    new SoundServerThread(socket, nextTcpClientId(), nextUdpPort(), isFirstClient, lock, byteStream).start();

    isFirstClient = false;

    // Subsequent clients will be receivers (at least start out that way).

    while(true) { 
      socket = serverSocket.accept();
      log("Connection with additional client established. This client will be a receiver.");
      new SoundServerThread(socket, nextTcpClientId(), nextUdpPort(), isFirstClient, lock, byteStream).start();
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

