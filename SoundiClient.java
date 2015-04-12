public interface SoundiClient { 
  /** 
    * @return unique ID provided by server
    */ 
  public int getId();
  /** 
    * Requests ID from server via TCP and sets it.   
    */ 
  public void requestAndSetId();
  /** 
    * Connects to server via TCP.   
    * @throws ConnectException if client cannot connect to server.
    */ 
  public void connectTcp();
}
