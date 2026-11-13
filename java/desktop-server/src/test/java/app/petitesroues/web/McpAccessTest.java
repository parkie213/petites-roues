package app.petitesroues.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import app.petitesroues.config.PhoneListener;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The MCP loopback restriction remains effective when a user changes the UI bind address. */
class McpAccessTest {
  @ParameterizedTest
  @ValueSource(strings = {"/mcp", "/agents"})
  void onlyLoopbackClientsCanReachTheMcpTransport(String endpoint) throws Exception {
    var filter = new LocalRequestFilter(mock(PhoneListener.class), endpoint);
    for (var address : new String[] {"127.0.0.1", "::1", "192.168.1.12"}) {
      var request = new MockHttpServletRequest("POST", endpoint);
      request.setRemoteAddr(address);
      request.setServerName("localhost");
      var response = new MockHttpServletResponse();
      var reached = new AtomicBoolean();
      filter.doFilter(request, response, (req, res) -> reached.set(true));
      assertEquals(!address.equals("192.168.1.12"), reached.get(), address);
      assertEquals(address.equals("192.168.1.12") ? 403 : 200, response.getStatus());
    }
  }
}
