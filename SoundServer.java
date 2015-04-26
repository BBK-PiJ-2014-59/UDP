import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

import static util.SoundUtil.*;

public class SoundServer { 

  private static String programName = "SoundServer";

  private final int defaultTcpPort;
  private ServerSocket serverSocket;

  /**
    * First unique ID a server thread will give to a connecting client.
    */
  private final int firstClientId;

  /**
    * Next unique ID a server thread will give to a connecting client.
    */
  private int nextClientId;

  /**
    * UDP listener port of first server thread.
    */
  private final static int firstUdpPort = 42001;

  /**
    * UDP listener port of next server thread.
    */
  private int nextUdpPort;

  /**
    * Whether client connecting to server thread was first (ie sender client).
    */
  private boolean isFirstClient;


  /**
    * This the lock shared between the server threads which keeps audio from being written as it's read.
    */
  private ReentrantReadWriteLock lock;

  /**
    * Storage for audio, shared between server threads, for each receiving into or sending out of.
    */
  private ByteArrayOutputStream byteStream;

  /**
    * Info shared between threads for managing failover in the event that sender client dies.
    */
  private SharedFailoverInfo failoverInfo;

  public SoundServer() { 
    defaultTcpPort = 789;
    firstClientId = 1;
    nextClientId = firstClientId;
    nextUdpPort = firstUdpPort;
    isFirstClient = true;
    lock = new ReentrantReadWriteLock(true);  // 'true' means lock should go to waiting 
                                              // writer when all readers have given up their lock
    byteStream = new ByteArrayOutputStream();
    failoverInfo = new SharedFailoverInfo(firstUdpPort);  
  }

  /**
    * @return next unique ID to be given to a thread, which it will give its client. 
    */
  private int nextClientId() { 
    return nextClientId++; 
  }

  /**
    * @return next unique UDP port to give a thread. 
    */
  private int nextUdpPort() { 
    return nextUdpPort++; 
  }

  /**
    * Launches server, which sets up a thread to handle each client.
    * @throws IOException on socket setup failure.  
    */
  public void launch() throws IOException { 
    log("Starting SoundServer");
    log("Creating TCP socket.");
    serverSocket = new ServerSocket(defaultTcpPort); 
    log("Listening for TCP client.");

    // A thread handles each client. First client will end up sender.

    Socket socket = serverSocket.accept();
    log("Connection with first client established. This client will be the sender.");
    new SoundServerThread(socket, nextClientId(), nextUdpPort(), isFirstClient, lock, byteStream, failoverInfo).start();

    isFirstClient = false;

    // Subsequent clients will be receivers (at least start out that way).

    while(true) { 
      socket = serverSocket.accept();
      log("Connection with additional client established. This client will be a receiver.");
      new SoundServerThread(socket, nextClientId(), nextUdpPort(), isFirstClient, lock, byteStream, failoverInfo).start();
    }
  }

  static void log(String msg) { 
    logger(programName, msg);
  }

  public static void main(String[] args) { 
    SoundServer soundServer = new SoundServer();
    try { 
      soundServer.launch();
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }
}

