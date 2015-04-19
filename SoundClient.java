import java.io.*;
import java.nio.file.*;
import java.net.*;
import javax.sound.sampled.*;

import static util.SoundUtil.*;

public class SoundClient { 

  private static String loggingName = "SoundClient";

  private static String audioFilename = "Roland-JX-8P-Bell-C5.wav";
  private String tcpHost;
  private int tcpPort;

  private static String defaultHost = "localhost";
  private static int defaultPort = 789;

  private int id; // todo: make same as thread.getId();
  private static int defaultId = 0;

  //private String role; // todo: use enum/check for valid role reply from server.
  //private String role; // todo: use enum/check for valid role reply from server.

  private BufferedReader br;
  private PrintWriter pw;
  private Socket sock;
  private InputStreamReader isr;

  private DatagramSocket udpSocket;
  private InetAddress udpHost;
  private int udpPort;

  private MulticastSocket mcSocket;
  private InetAddress mcGroup;
  private static String mcAddress = "224.111.111.111";
  private static int mcPort = 10000;

  private static final int udpMaxPayload = 512; // Seems best practice not to exceeed this.
  
  private enum Role {
    NOT_SET,
    SENDER,
    RECEIVER
  }

  private Role role;

  private enum Request {
    ID,
    ROLE,
    UDP_PORT,
    ACK_LENGTH
  }

  private enum Replies { 
    ACK_LENGTH
  }

  byte[] soundBytesToSend; // if sender
  byte[] soundBytesToPlay; // if receiver

  public SoundClient() {
    this(defaultHost, defaultPort);
  }

  public SoundClient(String host, int port) {
    tcpHost = host;
    tcpPort = port;
    id = defaultId;
    role = Role.NOT_SET;
  }

  public static void main(String[] args) { 

    SoundClient soundClient = new SoundClient();  

    soundClient.connectTcp();
    soundClient.setUpTcpIo();
    soundClient.requestAndSetId();
    soundClient.requestAndSetRole();
    soundClient.requestAndSetUdpPort();
    soundClient.setUpUdp();

    //soundClient.udpSendString("UDP test123"); // test multicast send

    if (soundClient.getRole() == Role.SENDER) { 
      soundClient.readSoundFileIntoByteArray(audioFilename);
      //soundClient.playAudio(soundClient.getSoundBytesToSend()); // test play
      soundClient.tcpSendArrayLength();
      while(true) { 
        soundClient.udpSendSoundBytesToServerThread();
      }
    }

    if (soundClient.getRole() == Role.RECEIVER) { 
      soundClient.mcSetUpReceiver();
      //soundClient.mcReceiveString(udpMaxPayload); // test multicast receive.
      while(true) {
        byte[] playBytes = soundClient.mcReceiveAudioBroadcast();
        soundClient.playAudio(playBytes);
      }
    }
  }

  private byte[] getSoundBytesToSend() { 
    return soundBytesToSend;
  }

  private byte[] getSoundBytesToPlay() { 
    return soundBytesToPlay;
  }

  private void playAudio(byte[] bytes) {

    int sampleRate = 44100;
    int sampleSize = 16;
    int channels = 2;
    boolean signed = true;
    boolean bigEndian = false;

    AudioFormat format = new AudioFormat(sampleRate, sampleSize, channels, signed, bigEndian);

    try {
      DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
      SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
      sourceDataLine.open(format);
      sourceDataLine.start();
      sourceDataLine.write(bytes, 0, bytes.length);
      sourceDataLine.drain();
      sourceDataLine.close();
    } catch (Exception e) { // todo: make exceptions more specific
      e.printStackTrace();
    }
  }

  //private void mcReceiveAudioBroadcast() { 
  private byte[] mcReceiveAudioBroadcast() { 
    log("Listening for multicast audio broadcast.");
    DatagramPacket packet;  
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    //byte[] result = new byte[1000000];
    byte[] packetBytes = new byte[udpMaxPayload];

    mcSetTimeout(1000);

    int i = 0;
    while(true) { 
      packet = new DatagramPacket(packetBytes, packetBytes.length);
      try { 
        mcSocket.receive(packet);
      } catch (SocketTimeoutException e) {
        log("TIMEOUT"); 
        break; // This is the normal course of events.
      } catch (IOException e) {
        e.printStackTrace();
      }
      byteStream.write(packetBytes, 0, packetBytes.length);
      //System.arraycopy(packetBytes, 0, result, i, packetBytes.length); 
      i += udpMaxPayload;
    }
    byte[] result = byteStream.toByteArray();

    log("Bytes to play: " + result.length); 
    return result;
  }

  private void mcReceiveString(int len) { 
    byte[] buf = new byte[len];
    DatagramPacket mcPacket = new DatagramPacket(buf, buf.length);
    try { 
      log("Listening for multicast");
      mcSocket.receive(mcPacket);
      log("Received: " + new String(buf));
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  private void mcSetTimeout(int ms) {
    try {
      mcSocket.setSoTimeout(ms);
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  private void tcpSendArrayLength() { 
    String request = Replies.ACK_LENGTH.toString() + " " + soundBytesToSend.length;
    String reply = tcpRequest(request);

    if (reply != null && reply.startsWith(Replies.ACK_LENGTH.toString()))
      log("Server thread says it's ready to receive audio of requested length.");
    else { 
      log("Unexpected reply when sending array length.");
      // todo: handle problem (need to do this everwhere if there's time)
    }
  }

  private void readSoundFileIntoByteArray(String filename) { 
    Path path = Paths.get(filename);

    try { 
      log("Reading file " + filename + " into byte array.");
      soundBytesToSend = Files.readAllBytes(path);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  private void udpSendSoundBytesToServerThread() { 

    DatagramPacket packet;

    int i = 0;
    
    //log("Sending sound to server thread.");
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

  private void udpSendString(String msg) { 
    log("Sending string via UDP: " + msg);
    byte[] bytes = msg.getBytes();
    DatagramPacket packet = new DatagramPacket(bytes, bytes.length, udpHost, udpPort);
    if (packet == null)
      log("packet null"); 
    try { 
      udpSocket.send(packet);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }



  private void mcSetUpReceiver() {
    log("Setting up multicast receiver.");
    try {
      mcSocket = new MulticastSocket(mcPort);
      mcGroup = InetAddress.getByName(mcAddress);
      mcSocket.joinGroup(mcGroup);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  private void setUpUdp() { 
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
      sock = new Socket(tcpHost, tcpPort);
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
      br = new BufferedReader(isr);
      //pw = new PrintWriter(sock.getOutputStream(), true); // true autoFlushes output buffer
      pw = new PrintWriter(sock.getOutputStream(), true);
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

  private void requestAndSetRole() {  // todo: DRY
    String request = "ROLE";
    String reply = tcpRequest(request);

    if (reply.contains("RECEIVER")) 
      role = Role.RECEIVER;
    if (reply.contains("SENDER"))
      role = Role.SENDER;
    log("Role set to " + getRole()); 

    if (reply == null) 
      log("Got null reply from server when requesting " + request);
    else
      log("Unexpected reply from server when requesting " + request + ": " + reply);

    // todo: add exceptions (everywhere) for bad/no replies.
  }

  private String tcpRequest(String request) { 
    String reply = null;
    if (pw != null) { 
      log("Requesting " + request + " from server.");
      pw.println(request);   
      try { 
        reply = br.readLine();
      } catch (IOException e) { 
        e.printStackTrace(); 
      }
    } else { 
      log("Can't request " + request + " - no IO stream set up with server.");
    }
    return reply;
  }


  private void log(String msg) { 
    logger(loggingName + "-" + getId(), msg);
  }

}
