package app.petitesroues.web;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.phone.PairedPhone;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.JsonNode;

/**
 * Replays pairing/revocation contracts and checks Spring's request-scoped, session-free identity.
 */
@ActiveProfiles("security")
@Import(PhoneSecurityIntegrationTest.ProbeConfiguration.class)
class PhoneSecurityIntegrationTest extends PhoneListenerIntegrationTest {
  @Autowired ApplicationContext context;

  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {
    @Bean
    RouterFunction<ServerResponse> identityProbe() {
      return RouterFunctions.route()
          .GET(
              "/phone/security-test/identity",
              request -> {
                var servlet = request.servletRequest();
                var authentication = (Authentication) servlet.getUserPrincipal();
                var phone = (PairedPhone) authentication.getPrincipal();
                return ServerResponse.ok()
                    .body(
                        Map.of(
                            "phoneId",
                            phone.id(),
                            "hasCredentials",
                            authentication.getCredentials() != null,
                            "hasSession",
                            servlet.getSession(false) != null,
                            "attributeMatches",
                            phone.equals(servlet.getAttribute(PhoneRequestFilter.PHONE))));
              })
          .build();
    }
  }

  private String pair(String name) {
    String secret =
        pairing.start("desk", List.of("https://127.0.0.1:" + listener.port())).code().pair();
    var paired =
        phone
            .post()
            .uri("/phone/pair")
            // The public pairing route ignores unrelated credentials, just like the original
            // filter.
            .header("Authorization", "Bearer unrelated")
            .body(Map.of("secret", secret, "name", name))
            .retrieve()
            .body(JsonNode.class);
    assertFalse(paired.path("token").asString().isBlank());
    assertEquals(
        1, jdbc.queryForObject("SELECT count(*) FROM phones WHERE name=?", Integer.class, name));
    return paired.path("token").asString();
  }

  @Test
  void identityIsPerRequestHasNoCredentialsAndNeverCreatesASession() {
    assertFalse(context.containsBean("phoneRequestFilter"));
    assertEquals(1, context.getBeansOfType(SecurityFilterChain.class).size());
    String token = pair("One");
    String id = jdbc.queryForObject("SELECT id FROM phones WHERE name='One'", String.class);
    var response =
        phone
            .get()
            .uri("/phone/security-test/identity")
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .toEntity(JsonNode.class);
    var identity = response.getBody();
    assertEquals(id, identity.path("phoneId").asString());
    assertFalse(identity.path("hasCredentials").asBoolean());
    assertFalse(identity.path("hasSession").asBoolean());
    assertTrue(identity.path("attributeMatches").asBoolean());
    assertNull(response.getHeaders().getFirst("Set-Cookie"));
    assertNotNull(
        jdbc.queryForObject("SELECT last_seen_at FROM phones WHERE id=?", String.class, id));

    for (String authorization : new String[] {"", "Basic abc", "Bearer ", "Bearer wrong"}) {
      var rejected =
          assertThrows(
              HttpClientErrorException.Unauthorized.class,
              () ->
                  phone
                      .get()
                      .uri("/phone/security-test/identity")
                      .header("Authorization", authorization)
                      .header("Cookie", "JSESSIONID=fake")
                      .retrieve()
                      .toBodilessEntity());
      assertEquals("Bearer", rejected.getResponseHeaders().getFirst("WWW-Authenticate"));
      assertEquals(
          "Pair this phone with the desktop again",
          mapper.readTree(rejected.getResponseBodyAsString()).path("error").asString());
      assertNull(rejected.getResponseHeaders().getFirst("Location"));
      assertNull(rejected.getResponseHeaders().getFirst("Set-Cookie"));
    }
    String other = pair("Two");
    var second =
        phone
            .get()
            .uri("/phone/security-test/identity")
            .header("Authorization", "Bearer " + other)
            .retrieve()
            .body(JsonNode.class);
    assertNotEquals(id, second.path("phoneId").asString());
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM phones", Integer.class));
    assertEquals(
        id,
        phone
            .get()
            .uri("/phone/security-test/identity")
            .header("Authorization", "bEaReR " + token)
            .retrieve()
            .body(JsonNode.class)
            .path("phoneId")
            .asString());
  }

  @Test
  void localWritesKeepOriginChecksAndNoLoginPageIsAdded() {
    assertThrows(
        HttpClientErrorException.NotFound.class,
        () -> http.get().uri("/login").retrieve().toBodilessEntity());
    assertThrows(
        HttpClientErrorException.Forbidden.class,
        () ->
            http.put()
                .uri("/api/phone")
                .header("Origin", "https://example.org")
                .body(Map.of("enabled", false))
                .retrieve()
                .toBodilessEntity());
    assertThrows(
        HttpClientErrorException.Forbidden.class,
        () ->
            http.put()
                .uri("/api/phone")
                .header("Sec-Fetch-Site", "cross-site")
                .body(Map.of("enabled", false))
                .retrieve()
                .toBodilessEntity());
    assertTrue(listener.enabled());
    assertEquals(
        "true",
        jdbc.queryForObject("SELECT value FROM settings WHERE key='phone.enabled'", String.class));
    // A non-browser local client still writes through the existing REST contract.
    var off =
        http.put().uri("/api/phone").body(Map.of("enabled", false)).retrieve().body(JsonNode.class);
    assertFalse(off.path("enabled").asBoolean());
    assertFalse(listener.enabled());
    assertEquals(
        "false",
        jdbc.queryForObject("SELECT value FROM settings WHERE key='phone.enabled'", String.class));
  }
}
