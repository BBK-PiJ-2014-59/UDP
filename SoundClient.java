import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Clip;
import javax.sound.sampled.AudioSystem;

import javax.sound.sampled.LineEvent.Type;

import static util.SoundUtil.*;

public class SoundClient { 

  /**
    * Program's name as displayed in log messages.
    */
  private static String loggingName = "SoundClient";

  /**
    * Name of wav file for sender client to send audio of.
    */
  private final String audioFilename;

  /**
    * Hostname of SoundServer.
    */
  private String ipHost;

  /**
    * TCP port of SoundServer.
    */
  private int tcpPort;

  /**
    * Spec didn't require multiple hosts, so client and server
    * are both "localost".
    */
  private static String defaultHost = "localhost";

  /**
    * Default TCP port of SoundServer.
    */
  private final static int defaultPort = 789;

  /**
    * Unique client ID given by server used for eg logging.
    */
  private int id; // todo: make same as thread.getId();

  /**
    * ID we have until given one by server.
    */
  private static int defaultId = 0;

  /**
    * For receiving TCP signalling from server.
    */
  private BufferedReader bufferedReader;

  /**
    * For receiving TCP signalling from server.
    */
  private InputStreamReader isr;

  /**
    * For sending TCP signalling to server.
    */
  private PrintWriter printWriter;

  /**
    * For TCP signalling with server.
    */
  private Socket sock;

  /**
    * Whether UDP has been set up, so we don't re-attempt.
    */
  private boolean udpReceiverIsUp;

  /**
    * For receiving audio from server.
    */
  private DatagramSocket udpReceiverSocket;

  private int udpReceiverPort;
  private DatagramSocket udpSocket;
  private InetAddress udpHost;
  private int udpPort;

  /**
    * UDP guarantees delivery of 576 bytes/packet without having to rely on
    * automatic splitting up and reassembling at the other end. Such reassembly
    * fails if any packets are lost. As we are not dealing with lost packets, we need
    * to stay below 576. 512 seemed to be common practice when working with
    * this constraint.
    */
  private static final int udpMaxPayload = 512;

  /**
    * Sent to receiver client to trigger it to change its role to sender during failover situation.
    * This is done when receiver client is expecting an array length from its handling server thread.
    * Client must be set to expect same value shown here.
    */
  private static int resetClient = -1;
  
  /**
    * Possible Roles we can take on. NOT_SET is before we are assigned a role by server.
    */
  private enum Role {
    NOT_SET,
    SENDER,
    RECEIVER
  }

  /**
    * Determines whether we send or receive audio. Can change in a failover situation.
    */
  private Role role;

  /**
    * Requests we need to send to server.
    */
  private enum Request {
    ID,
    ROLE,
    UDP_PORT,
    ACK_LENGTH,
    READY_TO_SEND
  }

  /**
    * Replies we may get.
    */
  private enum Replies { 
    ACK_LENGTH
  }

  /**
    * Audio to be sent to server if we are sender.
    */
  private byte[] soundBytesToSend; 

  /**
    * Audio to be played if we're a receiver.
    */
  private byte[] soundBytes; 

  /**
    * Length of audio receive array.
    */
  private int receiveArrayLength; // todo: change variable name?

  public SoundClient(String audioFilename) {
    this(defaultHost, defaultPort, audioFilename);
  }

  public SoundClient(String host, int port, String audioFilename) {
    ipHost = host;
    tcpPort = port;
    id = defaultId;
    role = Role.NOT_SET;
    udpReceiverIsUp = false;
    this.audioFilename = audioFilename;
  }

  public static void main(String[] args) { 

    String filename = null;

    if (args.length == 1) { 
      filename = args[0];
    } else { 
      System.out.println("Usage: java " + loggingName + " <wav_filename>");
      System.exit(0);
    }

    SoundClient soundClient = new SoundClient(filename);  
    soundClient.launch();

  }


  /**
    * Runs one of two loops: audio sender or receiver (which plays).
    */
  private void launch() { 

    connectAndSetUp();

    // main loop:

    while(true) {  

      if (getRole() == Role.SENDER) { 
        loopSendingAudio();
      }

      else if (getRole() == Role.RECEIVER) { 

        udpSetUpReceiverSocket();

        // receiver loop:

        while(true) {

          System.out.println();
          tcpSend("READY_FOR_ARRAY_LENGTH"); // todo: what if this is sent before ServerThread does tcpWaitForMessage("READY_FOR_ARRAY_LENGTH")? Not heard!!
          String length = tcpListen(); // todo: what if connection is broken and length is null? Add exception handling to tcpListen();
          log("Received array length: " + length);
          setArrayLength(Integer.parseInt(length)); 

          if (getArrayLength() == resetClient) { 
            // This means there is a failover situation.
            // Become a sender instead of a receiver.
            setRole(Role.SENDER);
            log("Role changing from RECEIVER to SENDER");
            break;
          }

          soundBytes = new byte[getArrayLength()];
          tcpWaitForMessage("READY_FOR_UDP_PORT");
          tcpSend(new Integer(getUdpReceiverPort()).toString());
          tcpSend("READY_TO_RECEIVE"); 
          udpReceiveAudioFromSender();
          playAudio(soundBytes); // rename

        } // end of receiver loop

      } // end of receiver block

    } // end of launch's main while loop 

  }

  /**
    * Loops audio to server
    */
  private void loopSendingAudio() {

    readSoundFileIntoByteArray(audioFilename);
    tcpSendArrayLength();
    int audioSendCount = 0;
    
    while(true) { 
      System.out.println();
      log("Audio send count: " + audioSendCount++);
      String reply = tcpWaitForMessage("READY_TO_RECEIVE");
      if (reply == null) {
        error("Lost connection with receiver on server thread.");
        //break;
        System.exit(0);
      }
      tcpSend("READY_TO_SEND");
      try {
        Thread.sleep(1000); // todo: remove after testing?
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      udpSendSoundBytesToServerThread();
    }
  }

  /**
    * Set up TCP and UDP links with server.
    */
  private void connectAndSetUp() {
    connectTcp();
    setUpTcpIo();
    requestAndSetId();
    requestAndSetRole();
    requestAndSetUdpPort();
    setUpUdpSending();
  }

  private int getUdpReceiverPort() {
    return udpReceiverSocket.getLocalPort();
  }

  private void udpSetTimeout(int ms) {
    try {
      udpSocket.setSoTimeout(ms);
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  
  /**
    * Transfers audio from server for playing.
    */
  private void udpReceiveAudioFromSender() {
    DatagramPacket packet;
    byte[] packetBytes = new byte[udpMaxPayload];

    int byteI = 0;

    try {
      udpReceiverSocket.setSoTimeout(2000);
    } catch (SocketException e) {
      e.printStackTrace();
    }

    int arrLen = getArrayLength();
    while (byteI < arrLen - udpMaxPayload) {
        packet = new DatagramPacket(packetBytes, packetBytes.length);

        try {
          udpReceiverSocket.receive(packet);
        } catch (SocketTimeoutException e) {
          log("**** UDP TIMEOUT ****");
          break; // This is the normal course of events.
        } catch (IOException e) {
          e.printStackTrace();
        }

        try { 
          System.arraycopy(packetBytes, 0, soundBytes, byteI, packetBytes.length);
        } catch (ArrayIndexOutOfBoundsException e) { 
          e.printStackTrace();
          log("packetBytes.length: " + packetBytes.length);
          log("soundBytes.length: " + soundBytes.length);
          log("byteI: " + byteI);
          log("arrLen: " + arrLen);
        }

        byteI += udpMaxPayload;
    
    }


    log("Received final byte: " + byteI);

    udpSetTimeout(100);
    

  }

  /**
    * Listen for a TCP message from server.
    *
    * @return message
    */

  private String tcpListen() {
    String msg = null;
    try {
      msg = bufferedReader.readLine();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return msg;
  }

  /**
    * Wait for a message from server and reply.
    *
    * @param expected   expected message, for eg logging and checking.
    * @param sendThis   message to reply with.
    * @return           what was received.
    */
  private String tcpExpectAndSend(String expected, String sendThis) {
    String request = null;
    log("Waiting for message from SoundServerThread: " + expected);
    request = tcpListen();
    log("Message received.");
    if (request.startsWith(expected)) {
      log("Message received was as expected.");
      printWriter.println(sendThis);
      log("Sent this message: " + sendThis);
    } else {
      log("SoundServerThread sent this instead of '" + expected + "': " + request);
    }
    return request;
  }

  private void tcpExpectAndSetArrayLength() {
    String request = tcpExpectAndSend("ACK_LENGTH", "ACK_LENGTH"); // todo: rewrite, this is confusing.
    Pattern p = Pattern.compile("\\d+");
    Matcher m = p.matcher(request);
    m.find();
    setArrayLength(Integer.parseInt(m.group(0)));
    log("Array length set to " + getArrayLength());
  }
  
  private void setArrayLength(int len) {
    receiveArrayLength = len;
  }

  private int getArrayLength() {
    return receiveArrayLength;
  }



  private void tcpSend(String message) {
    log("Sending TCP message: " + message);
    printWriter.println(message);
  }


  private void udpSetUpReceiverSocket() {
    if (!udpReceiverIsUp) {
      try {
        udpReceiverSocket = new DatagramSocket();
        udpReceiverIsUp = true;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }


  /**
    * Play audio received from server.
    */
  private void playAudio(byte[] bytes) {

    log("Playing audio byte array of length " + bytes.length);
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(bytes, 0, bytes.length);
      log("available: " + bais.available());

      AudioInputStream stream; // input stream with format and length
      AudioFormat format;
      DataLine.Info info;
      Clip clip;

      stream = AudioSystem.getAudioInputStream(bais);
      format = stream.getFormat();
      info = new DataLine.Info(Clip.class, format);
      clip = (Clip) AudioSystem.getLine(info);
      clip.open(stream);
      clip.start();
      do {
        Thread.sleep(1);
      } while (clip.isActive());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
    * Send server length of audio array (prior to sending audio).
    */
  private void tcpSendArrayLength() { 
    String request = Replies.ACK_LENGTH.toString() + " " + soundBytesToSend.length;
    String reply = tcpRequest(request);

    if (reply != null && reply.startsWith(Replies.ACK_LENGTH.toString()))
      log("Server thread says it's ready to receive audio of requested length.");
    else { 
      log("Unexpected reply when sending array length.");
    }
  }

  /**
    * Prepare wav audio for sending to server.
    */
  private void readSoundFileIntoByteArray(String filename) { 
    Path path = Paths.get(filename);

    try { 
      log("Reading file " + filename + " into byte array.");
      soundBytesToSend = Files.readAllBytes(path);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  /**
    * Send audio via UDP.
    */
  private void udpSendSoundBytesToServerThread() { 

    DatagramPacket packet;

    int i = 0;
    
    log("Sending sound to server thread.");
    while (i < soundBytesToSend.length - udpMaxPayload) { 
      //log("i: " + i);
      packet = new DatagramPacket(soundBytesToSend, i, udpMaxPayload, udpHost, udpPort);
      try { 
        udpSocket.send(packet);
      } catch (IOException e) { 
        e.printStackTrace();
      }
      i += udpMaxPayload;
    }
  }

  private void setUpUdpSending() { 
    try { 
      udpSocket = new DatagramSocket();
    } catch (IOException e) { 
      e.printStackTrace();
    }
    try { 
      udpHost = InetAddress.getByName(defaultHost); // todo: get it working over the network.
    } catch (UnknownHostException e) { 
      e.printStackTrace();
    }
  }


  private void connectTcp() { 

    // Set up socket

    log("Setting up TCP connection with server.");
    try { 
      sock = new Socket(ipHost, tcpPort);
    } catch (ConnectException e) { // Connection refused
      e.printStackTrace();
    } catch (UnknownHostException e) { // couldn't resolve name. 
      e.printStackTrace();
    } catch (IOException e) { // some other problem setting up socket 
      e.printStackTrace();
    }
  }

  private void setUpTcpIo() { 

    // Set up I/O over socket 

    log("Setting up TCP IO streams with server.");
    try { 
      isr = new InputStreamReader(sock.getInputStream());
      bufferedReader = new BufferedReader(isr);
      //printWriter = new PrintWriter(sock.getOutputStream(), true); // true autoFlushes output buffer
      printWriter = new PrintWriter(sock.getOutputStream(), true);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  private int getId() { 
    return id;
  }

  private Role getRole() { 
    return role;
  }

  private void setRole(Role role) { 
    this.role = role;
  }

  private int getUdpPort() { 
    return udpPort;
  }

  private void requestAndSetId() { 
    String request = "ID";
    String reply = tcpRequest(request);
    if (reply != null) { 
      id = Integer.parseInt(reply); 
      log(request + " received: " + getId());
    } else { 
      log("Got null reply from server when requesting " + request);
    }
  }

  private void requestAndSetUdpPort() { 
    String request = "UDP_PORT";
    String reply = tcpRequest(request);
    if (reply != null) { 
      udpPort = Integer.parseInt(reply); 
      log(request + " received: " + getUdpPort());
    } else { 
      log("Got null reply from server when requesting " + request);
    }
  }

  private void requestAndSetRole() { 
    String request = "ROLE";
    String reply = tcpRequest(request);

    if (reply == null) 
      log("Got null reply from server when requesting " + request);
    else {
      if (reply.contains("RECEIVER")) { 
        role = Role.RECEIVER;
        log("Role set to " + getRole()); 
      }
      else if (reply.contains("SENDER")) {
        role = Role.SENDER;
        log("Role set to " + getRole()); 
      }
      else {
        log("Unexpected reply from server when requesting " + request + ": " + reply);
      }
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

  private String tcpSendAndWaitForReply(String message) { 
    String reply = null;
    if (printWriter != null) { 
      log("Sent TCP message: " + message);
      printWriter.println(message);   
      try { 
        reply = bufferedReader.readLine();
        log("Received TCP reply from server: " + reply);
      } catch (IOException e) { 
        e.printStackTrace(); 
      }
    } else { 
      log("Can't send message - no IO stream set up with server.");
    }
    return reply;
  }

  private String tcpWaitForMessage(String message) { 
    log("Waiting for TCP message: " + message);
    try { 
      message = bufferedReader.readLine();
      log("Received TCP message: " + message);
    } catch (IOException e) { 
      e.printStackTrace(); 
    }
    return message;
  }

  private void log(String msg) { 
    logger(loggingName + "-" + getId(), msg);
  }

  private void error(String msg) { 
    logger(loggingName + "-" + getId() + ": ERROR",  msg);
  }

}
