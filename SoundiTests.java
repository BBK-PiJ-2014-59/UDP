import org.junit.*;
import static org.junit.Assert.*;
import java.net.ConnectException;



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
    badClient = new SoundiClientImpl(badTcpPort); // Connect with TCP using non-default port
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

  @Test (expected=ConnectException.class)
  public void clientCantConnectWithTcp() { 
    badClient.connectTcp();
  }

}
