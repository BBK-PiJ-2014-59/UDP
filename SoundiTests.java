import org.junit.*;
import static org.junit.Assert.*;

public class SoundiTests { 
  private SoundiServer server;
  private SoundiClient client1;
  private final int firstClientId = 100;

  @Before
  public void before { 
    server = new SoundiServerImpl();
  }

  @Test
  public void serverListensForTcp { 
    assertTrue(server.isListeningForTcp());
  }

  @Test
  public void clientConnectsWithTcpAndGetsFirstId { 
    client1 = new SoundiClientImpl();
    assertEquals(firstClientId, client1.getId());
  }
}
