package com.petitesroues.web;

import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.config.PhoneListener;
import com.petitesroues.phone.PhonePairingService;
import com.petitesroues.support.HttpClients;
import com.petitesroues.support.HttpIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Pairing a phone and its authenticated requests, through a real phone listener. */
@TestPropertySource(properties = {"petitesroues.phone.enabled=true", "petitesroues.phone.port=0"})
class PhoneListenerIntegrationTest extends HttpIntegrationTest {
  @Autowired PhoneListener listener;
  @Autowired PhonePairingService pairing;
  RestClient phone;

  @BeforeEach
  void phoneClient() {
    jdbc.update("DELETE FROM phones");
    phone = HttpClients.client("http://127.0.0.1:" + listener.port());
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

  @Test
  void aPhonePairsOnceThenItsTokenOpensOnlyPhoneRoutes() {
    assertTrue(listener.enabled());
    assertNotEquals(port, listener.port());
    var status = mapper.readTree(http.get().uri("/api/phone").retrieve().body(String.class));
    assertTrue(status.path("enabled").asBoolean());
    assertEquals(listener.port(), status.path("port").asInt());

    String secret = pairing.start("desk", List.of("http://192.0.2.1:1")).code().pair();
    var pair = Map.of("secret", secret, "name", "Pixel");
    var paired =
        mapper.readTree(phone.post().uri("/phone/pair").body(pair).retrieve().body(String.class));
    String token = paired.path("token").asString();
    assertFalse(token.isBlank());
    assertEquals(
        400,
        status(() -> phone.post().uri("/phone/pair").body(pair).retrieve().toBodilessEntity()),
        "a pairing secret works once");

    assertEquals(0, hello(token).path("titles").asInt());
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
    assertEquals(401, status(() -> hello(token)), "a forgotten phone's token stops working");
  }
}
