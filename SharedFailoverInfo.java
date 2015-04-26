import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

public class SharedFailoverInfo {

  private Lock failoverLock;
  private boolean needFailover;
  private int udpPort; // of sender handling thread;

  public SharedFailoverInfo(int udpPortOfFirstThread) {
    failoverLock = new ReentrantLock();
    needFailover = false;
    udpPort = udpPortOfFirstThread;
  }

  void lock() { 
    failoverLock.lock();
  }

  void unlock() { 
    failoverLock.unlock();
  }

  synchronized void setNeedFailover(boolean b) { 
    needFailover = b;
  }

  synchronized boolean isFailed() { 
    return needFailover;
  }

  synchronized void incrementUdpPort() { 
    ++udpPort;
  }

  synchronized int getUdpPort() { 
    return udpPort;
  }

}

