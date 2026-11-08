package com.petitesroues.web;

import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.config.PhoneListener;
import com.petitesroues.model.sync.Desktop;
import com.petitesroues.model.sync.PairingCode;
import com.petitesroues.phone.PhonePairingService;
import com.petitesroues.support.HttpClients;
import com.petitesroues.support.HttpIntegrationTest;
import com.petitesroues.sync.DesktopClient;
import com.petitesroues.sync.DesktopException;
import com.petitesroues.sync.PairingCodeReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Pairing a phone and its authenticated requests, through a real phone listener and the phone's own
 * client ({@link DesktopClient}), so the two sides of the protocol are tested together.
 */
@TestPropertySource(properties = {"petitesroues.phone.enabled=true", "petitesroues.phone.port=0"})
class PhoneListenerIntegrationTest extends HttpIntegrationTest {
  @Autowired PhoneListener listener;
  @Autowired PhonePairingService pairing;
  RestClient phone;

  @BeforeEach
  void phoneClient() {
    jdbc.update("DELETE FROM phones");
    jdbc.update("DELETE FROM settings");
    switchPhones(true);
    phone = HttpClients.client("http://127.0.0.1:" + listener.port());
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
      return "http://127.0.0.1:" + socket.getLocalPort();
    }
  }

  @Test
  void aPhonePairsOnceThenItsTokenOpensOnlyPhoneRoutes() throws IOException {
    assertTrue(listener.enabled());
    assertNotEquals(port, listener.port());
    var status = mapper.readTree(http.get().uri("/api/phone").retrieve().body(String.class));
    assertTrue(status.path("enabled").asBoolean());
    assertEquals(listener.port(), status.path("port").asInt());

    // What the phone scans: the desktop's own payload, as the web shows it in the QR code.
    String phoneUrl = "http://127.0.0.1:" + listener.port();
    var offered = pairing.start(listener.desktopName(), List.of(deadUrl(), phoneUrl)).code();
    PairingCode scanned = PairingCodeReader.read(mapper.writeValueAsString(offered));
    assertEquals(offered, scanned);

    var client = new DesktopClient();
    Desktop desktop = client.pair(scanned, "Pixel");
    assertEquals(new Desktop(listener.desktopName(), phoneUrl, desktop.token()), desktop);
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
    String secret = pairing.start("desk", List.of("http://192.0.2.1:1")).code().pair();
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
            HttpClients.client("http://127.0.0.1:" + open)
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
    phone = HttpClients.client("http://127.0.0.1:" + listener.port());
    assertEquals(0, hello(token).path("titles").asInt(), "phones stay paired while it is off");
  }
}
