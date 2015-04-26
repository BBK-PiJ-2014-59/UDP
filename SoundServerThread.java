import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.concurrent.locks.ReentrantReadWriteLock; 
import java.util.concurrent.TimeUnit; 

import javax.sound.sampled.AudioInputStream; 
import javax.sound.sampled.AudioFormat; 
import javax.sound.sampled.DataLine; 
import javax.sound.sampled.Clip; 
import javax.sound.sampled.AudioSystem; 

import static util.SoundUtil.*;

public class SoundServerThread extends Thread { 

  /**
    * Program's name as displayed in log messages.
    */
  private final static String loggingName = "SoundServerThread";

  /**
    * Spec didn't require multiple hosts, so client and server
    * are both "localost".
    */
  private final static String defaultClientHostname = "localhost";

  /**
    * For signalling with a single client (one per thread).
    */
  private Socket tcpSocket;

  /**
    * Unique ID of the client this thread handles.
    */
  private Integer clientId;

  /**
    * Client's role ie sender of audio or receiver of it. 
    */
  private ClientRoles clientRole;

  /**
    * Whether client this thread handles is the first client. The first client is the audio sender (until failover).
    */
  private boolean isFirstClient;

  /**
    * For receiving TCP signalling from client.
    */
  private BufferedReader bufferedReader;

  /**
    * For receiving TCP signalling from client.
    */
  private InputStreamReader isr;

  /**
    * For sending TCP signalling to client.
    */
  private PrintWriter printWriter;

  /**
    * For receiving audio via UDP from sender client (if this thread handles the sender client).
    */
  private DatagramSocket udpReceivingSocket;

  /**
    * UDP port this thread receives audio on from sender client (if this thread handles the sender client).
    */
  private Integer udpPort;

  /**
    * Whether UDP socket has been set up (so eg setup isn't reattempted).
    */
  private boolean udpIsUp;

  /**
    * Possible client roles.
    */
  private enum ClientRoles { 
    SENDER,
    RECEIVER
  }

  /**
    * Possible client requests - not exhaustive.
    */
  private enum ClientRequests { 
    ID,
    ROLE,
    UDP_PORT,
    ACK_LENGTH
  }

  /**
    * Possible replies to client requests - not exhaustive.
    */
  private enum Replies { 
    ACK_LENGTH
  }

  /**
    * UDP guarantees delivery of 576 bytes/packet without having to rely on 
    * automatic splitting up and reassembling at the other end. Such reassembly
    * fails if any packets are lost. As we are not dealing with lost packets, we need
    * to stay below 576. 512 seemed to be common practice when working with
    * this constraint.
    */
  private static int udpMaxPayload = 512; 

  /**
    * Shared between sending and receiving threads for storing audio.
    */
  private ByteArrayOutputStream byteArrayOutputStream;

  /**
    * Client sends this length before sending the audio itself so that storage can be made for it.
    */ 
  private int receivedAudioArrayLength;

  /**
    * For ensuring that sender-handling thread does not write to shared audio storage while receiver-handling threads are reading from it, and vice versa. 
    */ 
  private ReentrantReadWriteLock rwLock;

  /**
    * For sending packets to receiver client (if this thread is a receiver-handler).
    */
  private DatagramSocket udpSenderSocket;

  /**
    * For sending packets to receiver client (if this thread is a receiver-handler).
    */
  private InetAddress udpReceiverHost;

  /**
    * For information, shared between threads, to manage failover (ie when sender client dies and a receiver client must take over). 
    */
  private SharedFailoverInfo failoverInfo;


  /** 
    * Sent to receiver client to trigger it to change its role to sender during failover situation.
    * This is done when receiver client is expecting an array length from its handling server thread.
    * Client must be set to expect same value shown here.
    */
  private static int resetClient = -1;

  SoundServerThread(Socket s, int id, int port, boolean isFirst, ReentrantReadWriteLock rwLock, ByteArrayOutputStream byteArrayOutputStream, SharedFailoverInfo info) { 
    tcpSocket = s;
    clientId = id; 
    udpPort = port;
    isFirstClient = isFirst;
    clientRole = isFirstClient ? ClientRoles.SENDER : ClientRoles.RECEIVER;   
    udpIsUp = false;
    this.rwLock = rwLock;
    this.byteArrayOutputStream = byteArrayOutputStream;
    failoverInfo = info;

    log("Initialized to listen on UDP port " + udpPort);
  }

  /**
    * Contains the two main loops:
    * One for sender-client handling (SCH) and another receiver-client handling (RCH). 
    */
  public void run() { 

    setUpClient();

    boolean takingOverHandlingSender = false; // During failover situation 
                                              // this tells thread if it 
                                              // should change role from 
                                              // receiver-handler to sender handler 
    ///////////////////////////////
    //                          //
    // main server thread loop // 
    //                        //
    ///////////////////////////

    while(true) {


      // ******** Receiver-client handler block *********

      if (clientRole == ClientRoles.RECEIVER) { 

        boolean readLocked = false;
        boolean readLockTimedOut = false;

        while(true) {

          // First check whether we are supposed to take over as sender-client handler 
          // thread (SCHT) ie because the current SCHT said there was problem with its sender client.

          log("failoverInfo.isFailed(): " + failoverInfo.isFailed() + " failoverInfo.getUdpPort(): " + failoverInfo.getUdpPort());
          if (failoverInfo.isFailed() && udpPort == failoverInfo.getUdpPort()) { 
            log("Taking over as sender handler thread. Changing client role to sender client.");
            clientRole = ClientRoles.SENDER; 
            takingOverHandlingSender = true;
            break;
          }

          int timeout = 10; // In seconds - causes lock attempt to timeout during failover.

          try { 
            log("Waiting for read lock.");
            readLocked = rwLock.readLock().tryLock(timeout, TimeUnit.SECONDS); // Has to be able to time out here during failover. 

            if (readLocked)
              log("Read lock obtained.");
            else { 
              log("Timed out waiting for read lock");
              readLockTimedOut = true;
            }

          } catch (InterruptedException e) { 
            e.printStackTrace();
          }

          try {

            sendAudio();

          } finally {
            if (readLocked) { 
              rwLock.readLock().unlock(); 
              log("Read lock released.");
            }
          }       

        } // end of while loop for receiver-client handler

      } // end of if block for receiver-client handler
 



      // ******** Sender-client handler block *********

      if (clientRole == ClientRoles.SENDER) { 

        // Check whether we are now taking over as sender-client handler thread (due to a failover situation) 
        // and therefore need to reset our client to be a sender.

        if (takingOverHandlingSender) { 
          log("Notifying client it needs to be sender now.");
          tcpExpectAndSend("READY_FOR_ARRAY_LENGTH", new Integer(resetClient).toString());  
        }

        udpSetUpReceivingSocket();
        tcpExpectAndSetArrayLength();
        int audioReceiveCount = 0;
        boolean lostConnection = false;
        boolean iShouldDie = false;
          
        // sender-handling loop

        while(true) { 
          
          System.out.println();
          log("Audio receive count: " + audioReceiveCount++);
          log("Waiting for write lock.");
          rwLock.writeLock().lock(); 

          try {

            log("Write lock obtained.");

            try {
              Thread.sleep(2000);
            } catch (InterruptedException e) { 
              e.printStackTrace();
            }

            tcpSend("READY_TO_RECEIVE");
            String reply = tcpListen();

            if (reply == null) {
              log("Lost connection with sender client");
              lostConnection = true; 
              log("Releasing write lock so a new sender handling thread can take over.");
              rwLock.writeLock().unlock(); // unlock soundBytes so it can be read by thread we're failing over to. 
              failOver();
              break;
            } else if (reply.equals("READY_TO_SEND"))
              udpReceiveAudioFromClient(); // write soundBytes

          } finally {

            rwLock.writeLock().unlock(); // unlock soundBytes.
            log("Write lock released.");

          }       

        } // end of while loop for sender-client handler
        
      } // end of if block for sender-client handler

    } // end of run() loop

  } // end of run()


  /**
    * Set up communication with client for later data transfer
    */
  private void setUpClient() { 

    tcpSetUpIo();
    tcpExpectAndSend(ClientRequests.ID.toString(), clientId.toString());
    tcpExpectAndSend(ClientRequests.ROLE.toString(), clientRole.toString());
    tcpExpectAndSend(ClientRequests.UDP_PORT.toString(), udpPort.toString());

  }

  /**
    * Negotiate and carry out transfer of audio to client
    */
  private void sendAudio() { 

    tcpWaitForMessage("READY_FOR_ARRAY_LENGTH"); // First message to expect from client. 
    log("byteArrayOutputStream.size(): " + byteArrayOutputStream.size());
    tcpSend(new Integer(byteArrayOutputStream.size()).toString());
    tcpSend("READY_FOR_UDP_PORT");
    String reply = tcpListen(); // todo: check for null (here and elsewhere!).
    int port = Integer.parseInt(reply);
    log("Received receiver's UDP port: " + port);
    udpSetUpSenderSocket();
    tcpWaitForMessage("READY_TO_RECEIVE");  
    udpSendSoundBytesToClient(port);

  }

  /**
    * Update object shared between threads for managing failover so that another thread takes over as sender-client handler.
    */
  private void failOver() {

    log("Sender-handling thread needs to fail over.");

    failoverInfo.setNeedFailover(true);
    failoverInfo.incrementUdpPort();

  }

  /**
    * Set up UDP sending.
    */
  private void udpSetUpSenderSocket() {
    try {
      log("Setting up UDP sender socket.");
      udpSenderSocket = new DatagramSocket();
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      udpReceiverHost = InetAddress.getByName(defaultClientHostname); // todo: get it working over the network.
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }


  /**
    * Send audio to receiver client.
    */
  private void udpSendSoundBytesToClient(int clientUdpPort) {

    DatagramPacket packet;

    int i = 0;

    //log("soundBytes: " + soundBytes);
    log("Sending sound to client.");
    byte[] bytes = byteArrayOutputStream.toByteArray();
    while (i < bytes.length - udpMaxPayload) {
      //log("i: " + i);
      packet = new DatagramPacket(bytes, i, udpMaxPayload, tcpSocket.getInetAddress(), clientUdpPort);
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

  /**
    * Displays the message it's TCP waiting for from client
    * and returns the message it actually received.
    * 
    * @return message received.
    */
  private String tcpWaitForMessage(String message) { 
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

  /**
    * Sets timeout on UDP socket for receiving audio packets from sender client
    */
  private void udpSetReceivingTimeout(int ms) { 
    try { 
      udpReceivingSocket.setSoTimeout(ms);
    } catch (SocketException e) { 
      e.printStackTrace();
    }
  }

  /**
    * Receive audio from sender client via UDP and store in object shared by
    * receiver-handling threads
    */
  private void udpReceiveAudioFromClient() { 
    DatagramPacket packet;
    byte[] packetBytes = new byte[udpMaxPayload];
  
    udpSetReceivingTimeout(1000);

    byteArrayOutputStream.reset(); // clear it, otherwise we're just appending.


    // First get packets with constant payload size (udpMaxPayload) 

    int arrLen = getAudioReceivedArrayLength();
    int byteNum = 0;
    log("Receiving byte " + byteNum);
    log("Array length: " + arrLen);

    while (byteNum < arrLen - udpMaxPayload) {
        packet = new DatagramPacket(packetBytes, packetBytes.length);

        try {
          udpReceivingSocket.receive(packet);
        } catch (SocketTimeoutException e) {
          log("**** UDP TIMEOUT ****"); 
          break; // This is the normal course of events.
        } catch (IOException e) { 
          e.printStackTrace(); 
        }

        byteArrayOutputStream.write(packetBytes, 0, packetBytes.length);
        byteNum += udpMaxPayload;
    }

        log("byteArrayOutputStream.size(): " + byteArrayOutputStream.size());
        //log("byteNum: " + byteNum); 

    // get final packet, size being what ever is left after getting contant length packets.

    if (byteNum < arrLen) { 
      int finLen = arrLen - byteNum;
      log("Last packet length: " + finLen);
      byte[] finBytes = new byte[finLen];
      packet = new DatagramPacket(finBytes, finLen);

      try {
        udpReceivingSocket.receive(packet);
      } catch (SocketTimeoutException e) {
        e.printStackTrace();
      } catch (IOException e) { 
        e.printStackTrace(); 
      }

      byteArrayOutputStream.write(finBytes, 0, finBytes.length);
      byteNum += finLen;
    }

    log("Received final byte: " + byteNum);

    log("byteArrayOutputStream.size(): " + byteArrayOutputStream.size());

    udpSetReceivingTimeout(100); 

  }


  /**
    * Set up TCP signalling with client.
    */
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

  /**
    * Gets audio array length from sender client and sets it locally.
    */
  private void tcpExpectAndSetArrayLength() { 
    String message = tcpExpectAndSend(ClientRequests.ACK_LENGTH.toString(), Replies.ACK_LENGTH.toString()); // todo: check length ok before ack?
    if (message != null) { 
      Pattern p = Pattern.compile("\\d+");
      Matcher m = p.matcher(message);
      m.find();
      setAudioReceivedArrayLength(Integer.parseInt(m.group(0)));
      log("Array length set to " + getAudioReceivedArrayLength());
    } else { 
      error("Array length can't be set to specified value: " + message);
    }
  }

  /**
    * Sets local audio receiver array length.
    *
    * @param  length of local audio receiver array.
    */
  private void setAudioReceivedArrayLength(int len) { 
    receivedAudioArrayLength = len; 
  }

  /**
    * Returns local audio receiver array length.
    * 
    * @return local audio receiver array length.
    */
  private int getAudioReceivedArrayLength() { 
    return receivedAudioArrayLength; 
  }

  /**
    * Wait for a message from client and reply.
    *
    * @param expected   expected message, for eg logging and checking.
    * @param sendThis   message to reply with. 
    * @return           what was received. 
    */
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

  /**
    * Send a String via TCP to client.
    * 
    * @param message to send
    */
  private void tcpSend(String message) { 
    log("Sending TCP message: " + message);
    printWriter.println(message);
  }

  /**
    * Listen for a TCP message from client.
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
    * Set up to receive UDP packets from client.
    */
  private void udpSetUpReceivingSocket() {
    if (!udpIsUp) { 
      try {
        log("Instantiating UDP datagram socket.");
        udpReceivingSocket = new DatagramSocket(udpPort);
        udpIsUp = true;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
    * For logging (normal) messages to the console. Relies on logger from "util.SoundUtil.*" import.
    */
  private void log(String msg) {
    logger(loggingName + "-" + getId(), msg);
  }

  /**
    * For logging errors to the console. Relies on logger from "util.SoundUtil.*" import.
    */
  private void error(String msg) { 
    logger(loggingName + "-" + getId() + ": ERROR",  msg);
  }

}
