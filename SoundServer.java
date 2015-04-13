import java.net.*;
import java.io.*;
import java.util.*;

public class SoundServer { 
  
  private static int defaultPort = 789;
  private ServerSocket serverSocket;

  void start() throws IOException { 
    serverSocket = new ServerSocket(defaultPort); 
    Socket socket = serverSocket.accept();
    sendClientId(socket);
  }

  void sendClientId(Socket s) { 
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
