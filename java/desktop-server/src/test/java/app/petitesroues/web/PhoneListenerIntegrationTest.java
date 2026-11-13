package app.petitesroues.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.petitesroues.config.PhoneListener;
import app.petitesroues.config.PhoneRouteContext;
import app.petitesroues.phone.PhoneCertificateService;
import app.petitesroues.phone.PhonePairingService;
import app.petitesroues.support.HttpClients;
import app.petitesroues.support.HttpIntegrationTest;
import app.petitesroues.sync.DesktopClient;
import app.petitesroues.sync.DesktopException;
import app.petitesroues.sync.PairingCodeReader;
import app.petitesroues.sync.model.Desktop;
import app.petitesroues.sync.model.PairingCode;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/**
 * Pairing a phone and its authenticated requests, through a real phone listener and the phone's own
 * client ({@link DesktopClient}), so the two sides of the protocol are tested together.
 */
@TestPropertySource(properties = {"petitesroues.phone.enabled=true", "petitesroues.phone.port=0"})
class PhoneListenerIntegrationTest extends HttpIntegrationTest {
  @Autowired PhoneListener listener;
  @Autowired PhonePairingService pairing;
  @Autowired PhoneCertificateService certificates;
  @Autowired RequestMappingHandlerMapping desktopMappings;
  @MockitoSpyBean PhoneRouteContext routes;
  RestClient phone;

  @BeforeEach
  void phoneClient() {
    jdbc.update("DELETE FROM phones");
    jdbc.update("DELETE FROM settings");
    switchPhones(true);
    phone = HttpClients.phone("https://127.0.0.1:" + listener.port(), pin());
  }

  private String pin() {
    return certificates.certificate().pin();
  }

  @AfterEach
  void closeListener() {
    listener.setEnabled(false);
  }

  private JsonNode switchPhones(boolean enabled) {
    return mapper.readTree(
        http.put()
            .uri("/api/phone")
            .body(Map.of("enabled", enabled))
            .retrieve()
            .body(String.class));
  }

  private int status(Runnable call) {
    try {
      call.run();
      return 200;
    } catch (HttpClientErrorException e) {
      return e.getStatusCode().value();
    }
  }

  private JsonNode hello(String token) {
    return mapper.readTree(
        phone
            .get()
            .uri("/phone/hello")
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(String.class));
  }

  /** A local port nothing listens on: connecting is refused at once. */
  private static String deadUrl() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return "https://127.0.0.1:" + socket.getLocalPort();
    }
  }

  @Test
  void desktopRoutesAreAbsentEvenBeforeThePhoneServerIsPublished() {
    switchPhones(false);
    String secret = pairing.start("desk", List.of("https://192.0.2.1:1")).code().pair();
    String token = pairing.pair(secret, "Pixel", "desk").token();
    String phoneId = pairing.phones().getFirst().id();
    var opened = new AtomicReference<PhoneRouteContext.Running>();
    doAnswer(
            invocation -> {
              var running = (PhoneRouteContext.Running) invocation.callRealMethod();
              opened.set(running);
              try {
                assertFalse(listener.enabled(), "the HTTPS port is open before publication");
                assertEquals(-1, listener.port());
                assertNull(running.context().getParent(), "no inherited desktop MVC strategies");
                var mappings = running.context().getBean(RequestMappingHandlerMapping.class);
                assertFalse(mappings.getHandlerMethods().isEmpty());
                assertTrue(
                    mappings.getHandlerMethods().values().stream()
                        .allMatch(method -> method.getBeanType() == PhoneController.class));
                assertTrue(
                    desktopMappings.getHandlerMethods().values().stream()
                        .noneMatch(method -> method.getBeanType() == PhoneController.class));
                var early = HttpClients.phone("https://127.0.0.1:" + running.port(), pin());
                for (String path :
                    List.of("/", "/index.html", "/api/library", "/api/phone", "/error")) {
                  assertEquals(
                      404,
                      status(
                          () ->
                              early
                                  .get()
                                  .uri(path)
                                  .header("Authorization", "Bearer " + token)
                                  .retrieve()
                                  .toBodilessEntity()),
                      path);
                }
                // A real desktop write would revoke this token if the old race exposed the UI
                // context.
                assertEquals(
                    404,
                    status(
                        () ->
                            early
                                .delete()
                                .uri("/api/phones/" + phoneId)
                                .header("Origin", "http://127.0.0.1:" + running.port())
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .toBodilessEntity()));
                assertEquals(
                    1,
                    jdbc.queryForObject(
                        "SELECT count(*) FROM phones WHERE id=?", Integer.class, phoneId));
                assertEquals(
                    401,
                    status(() -> early.get().uri("/phone/hello").retrieve().toBodilessEntity()));
                var greeting =
                    early
                        .get()
                        .uri("/phone/hello")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(JsonNode.class);
                assertEquals(listener.desktopName(), greeting.path("name").asString());
                assertEquals(0, greeting.path("titles").asInt());
                return running;
              } catch (Throwable failure) {
                running.close();
                throw failure;
              }
            })
        .when(routes)
        .start(anyInt(), same(listener), any());

    assertTrue(switchPhones(true).path("enabled").asBoolean());
    assertTrue(pairing.authenticate(token).isPresent());
    var running = opened.get();
    int phonePort = running.port();
    switchPhones(false);
    assertFalse(running.context().isActive(), "disabling releases the isolated MVC context");
    assertThrows(
        ResourceAccessException.class,
        () ->
            HttpClients.phone("https://127.0.0.1:" + phonePort, pin())
                .get()
                .uri("/phone/hello")
                .retrieve()
                .toBodilessEntity());
    assertEquals(
        200, http.get().uri("/api/library").retrieve().toBodilessEntity().getStatusCode().value());
  }

  @Test
  void failedBindKeepsTheSwitchOffAndCanRetryTheSamePort() throws IOException {
    switchPhones(false);
    int occupiedPort;
    try (var occupied = new ServerSocket(0)) {
      occupiedPort = occupied.getLocalPort();
      doAnswer(invocation -> routes.start(occupiedPort, listener, invocation.getArgument(2)))
          .when(routes)
          .start(eq(0), same(listener), any());
      assertThrows(IllegalStateException.class, () -> listener.setEnabled(true));
      assertFalse(listener.enabled());
      assertEquals(-1, listener.port());
      assertEquals(
          "false",
          jdbc.queryForObject(
              "SELECT value FROM settings WHERE key='phone.enabled'", String.class));
    }
    assertTrue(switchPhones(true).path("enabled").asBoolean());
    assertEquals(occupiedPort, listener.port());
    var reopened = HttpClients.phone("https://127.0.0.1:" + occupiedPort, pin());
    assertEquals(
        401, status(() -> reopened.get().uri("/phone/hello").retrieve().toBodilessEntity()));
    assertEquals(
        404, status(() -> reopened.get().uri("/api/library").retrieve().toBodilessEntity()));
  }

  @Test
  void aPhonePairsOnceThenItsTokenOpensOnlyPhoneRoutes() throws IOException {
    assertTrue(listener.enabled());
    assertNotEquals(port, listener.port());
    var status = mapper.readTree(http.get().uri("/api/phone").retrieve().body(String.class));
    assertTrue(status.path("enabled").asBoolean());
    assertEquals(listener.port(), status.path("port").asInt());

    // What the phone scans: the desktop's own payload, as the web shows it in the QR code.
    String phoneUrl = "https://127.0.0.1:" + listener.port();
    var offered = pairing.start(listener.desktopName(), List.of(deadUrl(), phoneUrl)).code();
    PairingCode scanned = PairingCodeReader.read(mapper.writeValueAsString(offered));
    assertEquals(offered, scanned);

    var client = new DesktopClient();
    Desktop desktop = client.pair(scanned, "Pixel");
    assertEquals(new Desktop(listener.desktopName(), phoneUrl, desktop.token(), pin()), desktop);
    assertEquals(pin(), scanned.cert(), "the code carries the listener's pin");
    assertTrue(listener.urls().stream().allMatch(url -> url.startsWith("https://")));
    assertThrows(
        RestClientException.class,
        () ->
            HttpClients.client("http://127.0.0.1:" + listener.port())
                .get()
                .uri("/phone/hello")
                .retrieve()
                .toBodilessEntity(),
        "the listener speaks only HTTPS");
    assertFalse(desktop.token().isBlank());
    var spent = assertThrows(DesktopException.class, () -> client.pair(scanned, "Pixel"));
    assertEquals(400, spent.status(), "a pairing secret works once");

    assertEquals(0, client.hello(desktop).titles());
    String token = desktop.token();
    assertEquals(401, status(() -> hello("wrong")));
    assertEquals(401, status(() -> phone.get().uri("/phone/hello").retrieve().toBodilessEntity()));

    assertEquals(
        404,
        status(() -> phone.get().uri("/api/library").retrieve().toBodilessEntity()),
        "the phone listener serves only phone routes");
    assertEquals(
        404,
        status(() -> http.get().uri("/phone/hello").retrieve().toBodilessEntity()),
        "phone routes are not on the UI port");

    var phones = mapper.readTree(http.get().uri("/api/phones").retrieve().body(String.class));
    assertEquals("Pixel", phones.get(0).path("name").asString());
    assertFalse(phones.get(0).path("lastSeenAt").isNull(), "hello marks the phone seen");
    assertFalse(phones.get(0).has("token"));
    http.delete()
        .uri("/api/phones/{id}", phones.get(0).path("id").asString())
        .retrieve()
        .toBodilessEntity();
    var forgotten = assertThrows(DesktopException.class, () -> client.hello(desktop));
    assertEquals(401, forgotten.status(), "a forgotten phone's token stops working");
    assertEquals("Pair this phone with the desktop again", forgotten.getMessage());
  }

  @Test
  void theSwitchClosesThePortKeepsPhonesPairedAndIsRemembered() {
    String secret = pairing.start("desk", List.of("https://192.0.2.1:1")).code().pair();
    String token =
        mapper
            .readTree(
                phone
                    .post()
                    .uri("/phone/pair")
                    .body(Map.of("secret", secret, "name", "Pixel"))
                    .retrieve()
                    .body(String.class))
            .path("token")
            .asString();
    int open = listener.port();
    assertEquals(
        "true",
        jdbc.queryForObject("SELECT value FROM settings WHERE key='phone.enabled'", String.class));

    var off = switchPhones(false);
    assertFalse(off.path("enabled").asBoolean());
    assertEquals(-1, off.path("port").asInt());
    assertThrows(
        ResourceAccessException.class,
        () ->
            HttpClients.phone("https://127.0.0.1:" + open, pin())
                .get()
                .uri("/phone/hello")
                .retrieve()
                .toBodilessEntity(),
        "the port is closed");
    assertEquals(
        400,
        status(() -> http.post().uri("/api/phone/pairings").retrieve().toBodilessEntity()),
        "no pairing while phones cannot connect");
    assertEquals(
        "false",
        jdbc.queryForObject("SELECT value FROM settings WHERE key='phone.enabled'", String.class));

    switchPhones(true);
    phone = HttpClients.phone("https://127.0.0.1:" + listener.port(), pin());
    assertEquals(0, hello(token).path("titles").asInt(), "phones stay paired while it is off");
  }
}
