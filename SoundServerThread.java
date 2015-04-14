import java.net.*;
import java.io.*;
import java.util.*;
import static util.SoundUtil.*;

public class SoundServerThread extends Thread { 

  private static String programName = "SoundServerThread";

  private Socket socket;
  private int tcpClientId;

  private BufferedReader br;
  private PrintWriter pw;
  private InputStreamReader isr;

  SoundServerThread(Socket s, int id) { 
    socket = s;
    tcpClientId = id; 
  }

  public void run() { 
    setUpTcpIo();
    sendClientId();
  }

  private void setUpTcpIo() {
    log("Setting up TCP IO stream with client.");
    try {
      isr = new InputStreamReader(socket.getInputStream());
      br = new BufferedReader(isr);
      pw = new PrintWriter(socket.getOutputStream(), true);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void sendClientId() {
    String expected = "ID";
    String request = null;
    log("Waiting for '" + expected + "' request from client.");
    try {
      request = br.readLine();
    } catch (IOException e) {
      e.printStackTrace();
    }
    log("Message from client received.");
    if (request.startsWith(expected)) {
      log("Message from client was as expected.");
      pw.println(tcpClientId);
      log("Sent this ID to client: " + tcpClientId);
    } else {
      log("Client sent this instead of '" + expected + "': " + request);
    }
  }

  private static void log(String msg) {
    logger(programName, msg);
  }

}
