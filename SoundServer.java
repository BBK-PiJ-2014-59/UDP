import java.net.*;
import java.io.*;
import java.util.*;

public class SoundServer { 
  
  private static String serverName = "SoundServer";
  private static int defaultPort = 789;
  private ServerSocket serverSocket;

  void start() throws IOException { 
    log("Creating TCP socket.");
    serverSocket = new ServerSocket(defaultPort); 
    log("Listening for TCP client.");
    Socket socket = serverSocket.accept();
    log("Connection with client established.");
    sendClientId(socket);
  }

  void sendClientId(Socket s) { 
  }
  
  public static void log(String s) { 
    System.out.println(serverName + ": " + s); 
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
