import java.net.*;
import java.io.*;
import java.util.*;
import static util.SoundUtil.*;

public class SoundServerThread extends Thread { 

  private static String programName = "SoundServerThread";

  private Socket socket;
  private Integer tcpClientId;
  private String clientRole;

  private BufferedReader br;
  private PrintWriter pw;
  private InputStreamReader isr;

  SoundServerThread(Socket s, int id) { 
    socket = s;
    tcpClientId = id; 
    clientRole = "sender";
  }

  public void run() { 
    setUpTcpIo();
    expectAndSend("ID", tcpClientId.toString());
    expectAndSend("ROLE", clientRole);
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

  private void expectAndSend(String expected, String sendThis) { 
    String request = null;
    log("Waiting for '" + expected + "' request from client.");
    request = tcpListen();
    log("Message from client received.");
    if (request.startsWith(expected)) {
      log("Message from client was as expected.");
      pw.println(sendThis);
      log("Sent this " + expected + " to client: " + sendThis);
    } else {
      log("Client sent this instead of '" + expected + "': " + request);
    }
  }

  private String tcpListen() { 
    String msg = null;
    try {
      msg = br.readLine();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return msg;
  }

  private static void log(String msg) {
    logger(programName, msg);
  }

}
