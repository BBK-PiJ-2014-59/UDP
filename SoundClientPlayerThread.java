import java.io.*;
import java.nio.file.*;
import java.net.*;
import javax.sound.sampled.*;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import java.util.*;

public class SoundClientPlayerThread extends Thread { 
  private static final int udpMaxPayload = 512;
  private Queue circBuf; 
  public SoundClientPlayerThread(Queue queue) { 
    circBuf = queue;
  }

  public void run() { 
    int bufSize = 58192;
    byte[] soundBytes = new byte[bufSize];
    try { 
      Thread.sleep(1000);
    } catch (InterruptedException e) { 
      e.printStackTrace();
    }
    while(true) { 
      for(int i=0; i<bufSize; ++i) { 
        soundBytes[i] = (byte) circBuf.remove();
      }
      playAudio(soundBytes);
    }
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

}
