package app.petitesroues.mcp;

import static org.junit.jupiter.api.Assertions.assertThrows;

import app.petitesroues.support.HttpIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

/** Ordinary app startup does not expose the trial agent protocol. */
class McpDisabledIntegrationTest extends HttpIntegrationTest {
  @Test
  void endpointIsAbsentWithoutTheMcpProfile() {
    assertThrows(
        HttpClientErrorException.NotFound.class,
        () -> http.post().uri("/mcp").body(Map.of()).retrieve().toBodilessEntity());
  }
}
