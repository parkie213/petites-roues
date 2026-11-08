package com.petitesroues.sync;

import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.model.sync.Desktop;
import com.petitesroues.model.sync.Hello;
import com.petitesroues.model.sync.PairingCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The client against a stand-in phone listener on this machine; the server module's integration
 * test runs it against the real one.
 */
class DesktopClientTest {
  private final DesktopClient client = new DesktopClient();
  private HttpServer server;
  private String url;
  private String authorization;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        PhoneRoutes.PAIR,
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          if (body.contains("\"secret\":\"good\""))
            answer(exchange, 200, "{\"token\":\"t0k\",\"name\":\"desk\"}");
          else answer(exchange, 400, "{\"error\":\"This pairing code has expired\"}");
        });
    server.createContext(
        PhoneRoutes.HELLO,
        exchange -> {
          authorization = exchange.getRequestHeaders().getFirst("Authorization");
          answer(
              exchange, 200, "{\"name\":\"desk\",\"version\":\"0.1.0\",\"titles\":3,\"media\":4}");
        });
    server.start();
    url = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private static void answer(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  /** A local port nothing listens on: connecting is refused at once. */
  private static String deadUrl() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return "http://127.0.0.1:" + socket.getLocalPort();
    }
  }

  @Test
  void pairsThroughTheFirstAddressThatAnswersThenSendsItsToken() throws IOException {
    var desktop = client.pair(new PairingCode(1, "desk", List.of(deadUrl(), url), "good"), "Pixel");
    assertEquals(new Desktop("desk", url, "t0k"), desktop);
    assertEquals(new Hello("desk", "0.1.0", 3, 4), client.hello(desktop));
    assertEquals("Bearer t0k", authorization);
  }

  @Test
  void aRefusalStopsAtOnceWithTheDesktopsMessage() {
    var refused =
        assertThrows(
            DesktopException.class,
            () -> client.pair(new PairingCode(1, "desk", List.of(url, deadUrl()), "old"), "Pixel"));
    assertEquals(400, refused.status());
    assertEquals("This pairing code has expired", refused.getMessage());
  }

  @Test
  void errorMessagesComeFromTheDesktop() {
    assertEquals("Code expired", client.message(400, "{\"error\":\"Code expired\"}"));
    assertEquals(
        "Pair this phone with the desktop again",
        client.message(
            401,
            """
            {"status":401,"error":"Unauthorized","message":"Pair this phone with the desktop again"}"""));
    assertEquals("This phone is no longer paired. Pair it again.", client.message(401, ""));
    assertEquals(
        "This phone is no longer paired. Pair it again.",
        client.message(401, "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"\"}"),
        "Spring's status name is not a message");
  }
}
