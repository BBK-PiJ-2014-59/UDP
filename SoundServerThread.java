import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.locks.ReentrantReadWriteLock; 
import javax.sound.sampled.*; // for testing only
import java.util.concurrent.TimeUnit; 


import static util.SoundUtil.*;

public class SoundServerThread extends Thread { 

  private static String programName = "SoundServerThread";

  private static String defaultHost = "localhost"; // for local testing

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

  private static int udpMaxPayload = 512;
  private byte[] soundBytes = null;
  private ByteArrayOutputStream byteStream; // object shared between threads for storing audio.
  private int arrayLength; // todo: change variable name?

  private ReentrantReadWriteLock lock;

  private DatagramSocket udpSenderSocket;
  private InetAddress udpReceiverHost;

  private SharedFailoverInfo failoverInfo;
  private static int resetClient = -1; // This needs to be the same on the client for failover purposes.

  SoundServerThread(Socket s, int id, int port, boolean isFirst, ReentrantReadWriteLock lock, ByteArrayOutputStream byteStream, SharedFailoverInfo info) { 
    tcpSocket = s;
    tcpClientId = id; 
    udpPort = port;
    isFirstClient = isFirst;
    clientRole = isFirstClient ? ClientRoles.SENDER : ClientRoles.RECEIVER;   
    udpIsUp = false;
    this.lock = lock;
    this.byteStream = byteStream;
    failoverInfo = info;

    log("Initialized to listen on UDP port " + udpPort);
  }

  public void run() { 
    tcpSetUpIo();
    tcpExpectAndSend(ClientRequests.ID.toString(), tcpClientId.toString());
    tcpExpectAndSend(ClientRequests.ROLE.toString(), clientRole.toString());
    tcpExpectAndSend(ClientRequests.UDP_PORT.toString(), udpPort.toString());

    boolean takingOverHandlingSender = false;

    while(true) { // main loop 

      if (clientRole == ClientRoles.RECEIVER) { 

        boolean readLocked = false;
        boolean readLockTimedOut = false;

        while(true) {

          // Check whether we are supposed to take over as sender-client handler thread (SCHT) ie because the current SCHT said there was problem with its sender client.

          log("failoverInfo.isFailed(): " + failoverInfo.isFailed() + " failoverInfo.getUdpPort(): " + failoverInfo.getUdpPort());
          if (failoverInfo.isFailed() && udpPort == failoverInfo.getUdpPort()) { 
            log("Taking over as sender handler thread. Changing client role to sender client.");
            //failoverInfo.setNeedFailover(false);
            clientRole = ClientRoles.SENDER; 
            takingOverHandlingSender = true;
            break;
          }

          //lock.readLock().lock(); // lock soundBytes so it can't be written by the ServerThread handling sender client.

          int timeout = 10;

          try { 
            log("Waiting for read lock.");
            readLocked = lock.readLock().tryLock(timeout, TimeUnit.SECONDS); // lock soundBytes so it can't be written by the ServerThread handling sender client.

            if (readLocked)
              log("Read lock obtained.");
            else { 
              log("Timed out waiting for read lock");
              readLockTimedOut = true;
            }

          } catch (InterruptedException e) { 
            e.printStackTrace();
            //continue;
          }

          try {
            tcpWaitForMessage("READY_FOR_ARRAY_LENGTH"); 
            //tcpSend(new Integer(getArrayLength()).toString());
            log("byteStream.size(): " + byteStream.size());
            tcpSend(new Integer(byteStream.size()).toString());
            tcpSend("READY_FOR_UDP_PORT"); // race condition?
            String reply = tcpListen(); // todo: check for null
            int port = Integer.parseInt(reply);
            log("Received receiver's UDP port: " + port);
            udpSetUpSenderSocket();
            tcpWaitForMessage("READY_TO_RECEIVE");  
            //tcpSendArrayLength();
            udpSendSoundBytesToClient(port);
          } finally {
            if (readLocked) { 
              lock.readLock().unlock(); 
              log("Read lock released.");
            }
          }       

        } // end of while loop for receiver-client handler

      } // end of if block for receiver-client handler
 

      // Sender-client handler block 

      if (clientRole == ClientRoles.SENDER) { 

        // Check whether we are now taking over as sender-client handler thread (due to a failover situation) 
        // and therefore need to reset our client to be a sender.

        if (takingOverHandlingSender) { 
          log("Notifying client it needs to be sender now.");
          tcpExpectAndSend("READY_FOR_ARRAY_LENGTH", new Integer(resetClient).toString());  
        }

        udpSetUpSocket();
        tcpExpectAndSetArrayLength();
        int audioReceiveCount = 0;
        boolean lostConnection = false;
        boolean iShouldDie = false;
          
        // sender-handling loop

        while(true) { 
          
          System.out.println();
          log("Audio receive count: " + audioReceiveCount++);
          log("Initializing sound storage array of length " + getArrayLength());
          soundBytes = new byte[getArrayLength()]; 
          log("Waiting for write lock.");
          lock.writeLock().lock(); // lock soundBytes so it can't be read by other ServerThreads.

          try {

            log("Write lock obtained.");

            try {
              Thread.sleep(2000);
            } catch (InterruptedException e) { 
              e.printStackTrace();
            }

            tcpSend("READY_TO_RECEIVE");
            String reply = tcpListen();
            //String reply = tcpListenInTimeoutLoop();
            //if (reply == "READY_TO_SEND")

            if (reply == null) {
              log("Lost connection with sender client");
              lostConnection = true; 
              log("Releasing write lock so a new sender handling thread can take over.");
              lock.writeLock().unlock(); // unlock soundBytes so it can be read by thread we're failing over to. 
              failOver();
              //iShouldDie = true;
              break;
            } else if (reply.equals("READY_TO_SEND"))
              udpReceiveAudioFromClient(); // write soundBytes

          } finally {

            lock.writeLock().unlock(); // unlock soundBytes.
            log("Write lock released.");

          }       

        } // end of while loop for sender-client handler
        
      } // end of if block for sender-client handler

    } // end of run() loop

  } // end of run()



  private void failOver() {

    log("Sender-handling thread needs to fail over.");

    failoverInfo.setNeedFailover(true);
    failoverInfo.incrementUdpPort();

    //while(failoverInfo.isFailed())
    //  ;

  }

  private void udpSetUpSenderSocket() {
    try {
      log("Setting up UDP sender socket.");
      udpSenderSocket = new DatagramSocket();
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      udpReceiverHost = InetAddress.getByName(defaultHost); // todo: get it working over the network.
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }


  private void playTest() {
    log("Play test.");

    try {
      //ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
      ByteArrayInputStream bais = new ByteArrayInputStream(byteStream.toByteArray());
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

  private void udpSendSoundBytesToClient2(int udpPort) {

    DatagramPacket packet;

    int i = 0;

    //log("soundBytes: " + soundBytes);
    log("Sending sound to client.");
    byte[] soundBytes = byteStream.toByteArray();
    while (i < soundBytes.length - udpMaxPayload) {
      //log("i: " + i);
      packet = new DatagramPacket(soundBytes, i, udpMaxPayload, tcpSocket.getInetAddress(), udpPort);
      if (packet == null) log("null packet");
      try {
        if (udpSenderSocket == null) log("null udpSenderSocket");
        udpSenderSocket.send(packet);
      } catch (IOException e) {
        e.printStackTrace();
      }
      i += udpMaxPayload;
    }
  }

  private void udpSendSoundBytesToClient(int udpPort) {

    DatagramPacket packet;

    int i = 0;

    //log("soundBytes: " + soundBytes);
    log("Sending sound to client.");
    byte[] bytes = byteStream.toByteArray();
    while (i < bytes.length - udpMaxPayload) {
      //log("i: " + i);
      packet = new DatagramPacket(bytes, i, udpMaxPayload, tcpSocket.getInetAddress(), udpPort);
      if (packet == null) log("null packet");
      try {
        if (udpSenderSocket == null) log("null udpSenderSocket");
        udpSenderSocket.send(packet);
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

  private String tcpWaitForMessage(String message) { // todo: add checking to this. Did we get what we expected?
    String received = null;
    log("Waiting for TCP message: " + message);
    try {
      received = bufferedReader.readLine();
      log("Received TCP message: " + received);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return received;
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
    
  
    udpSetTimeout(1000);

    byteStream.reset(); // clear it, otherwise we're just appending.


    // get packets with constant payload size (udpMaxPayload) 
    int arrLen = getArrayLength();
    int i = 0;
    log("Receiving byte " + i);
    log("Array length: " + arrLen);
    /*
    try { 
      log("UDP buf size: " + udpSocket.getReceiveBufferSize());
      udpSocket.setReceiveBufferSize(udpSocket.getReceiveBufferSize()*2); // not helping
      log("UDP buf size: " + udpSocket.getReceiveBufferSize());
    } catch (SocketException e) { 
      e.printStackTrace();
    }
    */

    while (i < arrLen - udpMaxPayload) {
        //log("i: " + i); 
        packet = new DatagramPacket(packetBytes, packetBytes.length);

        try {
          udpSocket.receive(packet);
        } catch (SocketTimeoutException e) {
          log("**** UDP TIMEOUT ****"); 
          break; // This is the normal course of events.
        } catch (IOException e) { 
          e.printStackTrace(); 
        }

        //System.arraycopy(packetBytes, 0, soundBytes, i, packetBytes.length);
        //log("packetBytes.length: " + packetBytes.length);
        byteStream.write(packetBytes, 0, packetBytes.length);
        //log("byteStream.size(): " + byteStream.size());
        i += udpMaxPayload;
    }

        log("byteStream.size(): " + byteStream.size());
        log("i: " + i); 
    //udpSetTimeout(5000); // todo: remove because for testing only, ie so we have time to start client in terminal.

/*
    // get final packet, size being what ever is left after getting contant length packets.
    if (i < arrLen) { 
      int finLen = arrLen - i;
      log("Last packet length: " + finLen);
      byte[] finBytes = new byte[finLen];
      packet = new DatagramPacket(finBytes, finLen);

      try {
        udpSocket.receive(packet);
      } catch (SocketTimeoutException e) {
        //break; // This is the normal course of events.
      } catch (IOException e) { 
        e.printStackTrace(); 
      }

      //System.arraycopy(finBytes, 0, soundBytes, i, finLen);
      byteStream.write(finBytes, 0, finBytes.length);
      i += finLen;
    }
*/


    log("Received final byte: " + i);

    log("byteStream.size(): " + byteStream.size());

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
    String message = tcpExpectAndSend(ClientRequests.ACK_LENGTH.toString(), Replies.ACK_LENGTH.toString()); // todo: check length ok before ack?
    if (message != null) { 
      Pattern p = Pattern.compile("\\d+");
      Matcher m = p.matcher(message);
      m.find();
      setArrayLength(Integer.parseInt(m.group(0)));
      log("Array length set to " + getArrayLength());
    } else { 
      error("Array length can't be set to specified value: " + message);
    }
  }

  private void setArrayLength(int len) { 
    arrayLength = len; 
  }

  private int getArrayLength() { 
    return arrayLength; 
  }

  private String tcpExpectAndSend(String expected, String sendThis) { 
    String message = null;
    log("Waiting for message: " + expected);
    message = tcpListen();

    log("Message received: " + message);

    if (message != null) { 
      if (message.startsWith(expected)) {
        printWriter.println(sendThis);
        log("Replied with: " + sendThis);
      } else {
        log("Unexpected message. Not replying.") ;
      }
    }

    return message; 
  }

  private void tcpSend(String message) { 
    log("Sending TCP message: " + message);
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

  private String tcpListenInTimeoutLoop() { 
    String msg = null;
    int to = 0;
    try { 
      to = tcpSocket.getSoTimeout();
    } catch (SocketException e) { 
      e.printStackTrace();
    }
    log("TCP timeout: " + to); 
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
        log("Instantiating UDP datagram socket.");
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

  private void error(String msg) { // only use this when we shouldn't have gotten somewhere.
    logger(programName + "-" + getId() + ": ERROR",  msg);
  }

}
