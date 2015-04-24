import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.locks.ReentrantReadWriteLock; 

import static util.SoundUtil.*;

public class SoundServerThread extends Thread { 

  private static String programName = "SoundServerThread";

  private Socket tcpSocket;
  private Integer tcpClientId;
  private ClientRoles clientRole;
  private boolean isFirstClient;

  private BufferedReader bufferedReader;
  private PrintWriter printWriter;
  private InputStreamReader isr;

  private DatagramSocket udpSocket;
  private Integer udpPort;
  private boolean udpIsUp;

  private enum ClientRoles { 
    SENDER,
    RECEIVER
  }

  private enum ClientRequests { 
    ID,
    ROLE,
    UDP_PORT,
    ACK_LENGTH
  }

  private enum Replies { 
    ACK_LENGTH
  }

  private MulticastSocket mcSocket; 
  private InetAddress mcGroup;
  private static String mcAddress = "224.111.111.111";
  private static int mcPort = 10000;

  private static int udpMaxPayload = 512;
  private byte[] soundBytes;
  private int arrayLength; // todo: change variable name?

  private ReentrantReadWriteLock lock;

  SoundServerThread(Socket s, int id, int port, boolean isFirst, ReentrantReadWriteLock lock) { 
    tcpSocket = s;
    tcpClientId = id; 
    udpPort = port;
    isFirstClient = isFirst;
    clientRole = isFirstClient ? ClientRoles.SENDER : ClientRoles.RECEIVER;   
    udpIsUp = false;
    this.lock = lock;

    log("Initialized to listen on UDP port " + udpPort);
  }

  public void run() { 
    tcpSetUpIo();
    tcpExpectAndSend(ClientRequests.ID.toString(), tcpClientId.toString());
    tcpExpectAndSend(ClientRequests.ROLE.toString(), clientRole.toString());
    tcpExpectAndSend(ClientRequests.UDP_PORT.toString(), udpPort.toString());

    if (clientRole == ClientRoles.SENDER) { 
      udpSetUpSocket();
      tcpExpectAndSetArrayLength();
      int audioReceiveCount = 0;
      boolean lostConnection = false;
      while(true) {
        log("Audio receive count: " + audioReceiveCount++);
        if (soundBytes == null)
          soundBytes = new byte[getArrayLength()]; 
        lock.writeLock().lock(); // lock soundBytes so it can't be read by other ServerThreads.
        log("Write lock obtained.");
        try {
          tcpSend("READY_TO_RECEIVE");
          String reply = tcpListen();
          //if (reply == "READY_TO_SEND")
          if (reply == null) {
            log("Lost connection with sender client");
            lostConnection = true; 
            break;
          } else if (reply.equals("READY_TO_SEND"))
            udpReceiveAudioFromClient(); // write soundBytes
        } finally {
          lock.writeLock().unlock(); // unlock soundBytes.
        }       
      }
    }

    if (clientRole == ClientRoles.RECEIVER) { 
      tcpWaitForMessage("READY_TO_RECEIVE");  
      tcpSendArrayLength();
      tcpSend("SEND_UDP_PORT");
      String reply = tcpListen();
      int port = Integer.parseInt(reply);
      udpSendSoundBytesToClient(port);
      

    }
  }


  private void udpSendSoundBytesToClient(int udpPort) {

    DatagramPacket packet;

    int i = 0;

    log("Sending sound to client.");
    while (i < soundBytes.length - udpMaxPayload) {
      //log("i: " + i);
      packet = new DatagramPacket(soundBytes, i, udpMaxPayload, tcpSocket.getInetAddress(), udpPort);
      try {
        udpSocket.send(packet);
      } catch (IOException e) {
        e.printStackTrace();
      }
      i += udpMaxPayload;
    }
  }


  private String tcpRequest(String request) {
    String reply = null;
    if (printWriter != null) {
      log("Requesting " + request + " from server.");
      printWriter.println(request);
      try {
        reply = bufferedReader.readLine();
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      log("Can't request " + request + " - no IO stream set up with server.");
    }
    return reply;
  }

  private void tcpSendArrayLength() {
    String request = Replies.ACK_LENGTH.toString() + " " + soundBytes.length;
    String reply = tcpRequest(request);

    if (reply != null && reply.startsWith(Replies.ACK_LENGTH.toString()))
      log("Server thread says it's ready to receive audio of requested length.");
    else {
      log("Unexpected reply when sending array length.");
      // todo: handle problem (need to do this everwhere if there's time)
    }
  }

  private void tcpWaitForMessage(String message) {
    log("Waiting for TCP message: " + message);
    try {
      message = bufferedReader.readLine();
      log("Received TCP message: " + message);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void mcBroadcastAudio() {
    // byte[] dummy = new byte[0];
    DatagramPacket mcPacket; 

    log("Broadcasting audio to receiver clients.");

    int i = 0;

    while (i < soundBytes.length - udpMaxPayload) {
      //log("i: " + i);
      mcPacket = new DatagramPacket(soundBytes, i, udpMaxPayload, mcGroup, mcPort);
      try {
        mcSocket.send(mcPacket);
      } catch (IOException e) {
        e.printStackTrace();
      }
      i += udpMaxPayload;
    }
  }

  private void mcTestSend() { //  
    byte[] dummy = new byte[0];
    DatagramPacket mcPacket = new DatagramPacket(dummy, 0, mcGroup, mcPort);
    int i = 0;
    while(true) { 
      ++i;
      byte[] bytes = ("multicast test " + i).getBytes(); 
      mcPacket.setData(bytes);
      mcPacket.setLength(bytes.length);
      try { 
        mcSocket.send(mcPacket);
        log(""+i);
      } catch (IOException e) { 
        e.printStackTrace();
      }
    }
  }

  private void udpSetTimeout(int ms) { 
    try { 
      udpSocket.setSoTimeout(ms);
    } catch (SocketException e) { 
      e.printStackTrace();
    }
  }

  private void udpReceiveAudioFromClient() { 
    DatagramPacket packet;
    byte[] packetBytes = new byte[udpMaxPayload];
    
    int i = 0;
  
    udpSetTimeout(100);

    log("Receiving byte " + i);

    // get packets with constant payload size (udpMaxPayload) 
    int arrLen = getArrayLength();
    while (i < arrLen - udpMaxPayload) {
        packet = new DatagramPacket(packetBytes, packetBytes.length);

        try {
          udpSocket.receive(packet);
        } catch (SocketTimeoutException e) {
          break; // This is the normal course of events.
        } catch (IOException e) { 
          e.printStackTrace(); 
        }

        System.arraycopy(packetBytes, 0, soundBytes, i, packetBytes.length);
        i += udpMaxPayload;
    }

    //udpSetTimeout(5000); // todo: remove because for testing only, ie so we have time to start client in terminal.

    // get final packet, size being what ever is left after getting contant length packets.
    if (i < arrLen) { 
      int finLen = arrLen - i;
      byte[] finBytes = new byte[finLen];
      packet = new DatagramPacket(finBytes, finLen);

      try {
        udpSocket.receive(packet);
      } catch (SocketTimeoutException e) {
        //break; // This is the normal course of events.
      } catch (IOException e) { 
        e.printStackTrace(); 
      }

      System.arraycopy(finBytes, 0, soundBytes, i, finLen);
      i += finLen;
    }


    log("Received final byte: " + i);

    udpSetTimeout(100); 

  }


  private void udpReceiveString() { // max length of udpMaxPayload - just for testing at this point

    byte[] bytes = new byte[udpMaxPayload];
    DatagramPacket udpPacket = new DatagramPacket(bytes, bytes.length); 
    try { 
      udpSocket.receive(udpPacket);
    } catch (IOException e) { 
      e.printStackTrace();
    }
    log("Received string: " + new String(udpPacket.getData()));
  }

  private void mcSetUpBroadcaster() {
    log("Setting up multicast broadcaster.");
    try { 
      mcSocket = new MulticastSocket(); 
      mcGroup = InetAddress.getByName(mcAddress);
    } catch (UnknownHostException e) { 
      e.printStackTrace();
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  private void tcpSetUpIo() {
    log("Setting up TCP IO stream with client.");
    try {
      isr = new InputStreamReader(tcpSocket.getInputStream());
      bufferedReader = new BufferedReader(isr);
      printWriter = new PrintWriter(tcpSocket.getOutputStream(), true);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void tcpExpectAndSetArrayLength() { 
    String request = tcpExpectAndSend(ClientRequests.ACK_LENGTH.toString(), Replies.ACK_LENGTH.toString()); // todo: check length ok before ack?
    Pattern p = Pattern.compile("\\d+");
    Matcher m = p.matcher(request);
    m.find();
    setArrayLength(Integer.parseInt(m.group(0)));
    log("Array length set to " + getArrayLength());
  }

  private void setArrayLength(int len) { 
    arrayLength = len; 
  }

  private int getArrayLength() { 
    return arrayLength; 
  }

  private String tcpExpectAndSend(String expected, String sendThis) { 
    String request = null;
    log("Waiting for '" + expected + "' request from client.");
    request = tcpListen();
    log("Message from client received.");
    if (request.startsWith(expected)) {
      log("Message from client was as expected.");
      printWriter.println(sendThis);
      log("Sent this to client: " + sendThis);
    } else {
      log("Client sent this instead of '" + expected + "': " + request);
    }
    return request; 
  }

  private void tcpSend(String message) { 
    log("Sending TCP message to client: " + message);
    printWriter.println(message);
  }

  private String tcpListen() { 
    String msg = null;
    try {
      msg = bufferedReader.readLine();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return msg;
  }

  private void udpSetUpSocket() {
    if (!udpIsUp) { 
      try {
        udpSocket = new DatagramSocket(udpPort);
        udpIsUp = true;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }



  private void log(String msg) {
    logger(programName + "-" + getId(), msg);
  }

}
