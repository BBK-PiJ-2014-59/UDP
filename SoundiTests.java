import org.junit.*;
import static org.junit.Assert.*;
import java.net.ConnectException;
import java.io.IOException;



public class SoundiTests { 
  private SoundiServer server;
  private SoundiClient client1;
  private SoundiClient badClient;
  private static final int firstClientId = 100;
  private static final int badTcpPort= 5555;


  @Before
  public void before() { 
    server = new SoundiServerImpl();
    client1 = new SoundiClientImpl();
  }

  // meaningless test?
  @Test
  public void serverListensForTcp() { 
    assertTrue(server.isListeningForTcp());
  }

  @Test
  public void clientConnectsWithTcpAndGetsFirstId() { 
    client1.connectTcp();
    client1.requestAndSetId(); 
    assertEquals(firstClientId, client1.getId());
  }

  //@Test (expected=IOException.class)
  @Test (expected=Exception.class)
  public void clientCantConnectWithTcpToBadPortOnLocalhost() { 
    badClient = new SoundiClientImpl("localhost", badTcpPort);
    badClient.connectTcp();
  }

}
