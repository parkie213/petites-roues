package app.petitesroues.sync;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.media.MediaFingerprint;
import app.petitesroues.sync.model.Desktop;
import app.petitesroues.sync.model.Hello;
import app.petitesroues.sync.model.PairingCode;
import app.petitesroues.sync.model.TitleFile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The client against a stand-in phone listener on this machine, over HTTPS with a self-signed test
 * certificate; the server module's integration test runs it against the real one.
 */
class DesktopClientTest {
  private static final String OTHER_PIN = "sha256:" + "0".repeat(64);
  private final DesktopClient client = new DesktopClient();
  private HttpsServer server;
  private String url;
  private String pin;
  private String authorization;
  private String range;
  private final byte[] media = "0123456789".getBytes(StandardCharsets.UTF_8);
  @TempDir Path phone;

  @BeforeEach
  void start() throws Exception {
    var store = KeyStore.getInstance("PKCS12");
    char[] password = "testpass".toCharArray();
    try (var in = getClass().getResourceAsStream("/desktop-test.p12")) {
      store.load(in, password);
    }
    pin = CertificatePins.of(store.getCertificate("desk"));
    var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(store, password);
    var tls = SSLContext.getInstance("TLS");
    tls.init(keys.getKeyManagers(), null, null);
    server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(tls));
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
    // A desktop that ignores Range and always sends the whole file.
    server.createContext(
        PhoneRoutes.titleFile("film", TitleFile.MEDIA),
        exchange -> {
          range = exchange.getRequestHeaders().getFirst("Range");
          exchange.sendResponseHeaders(200, media.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(media);
          }
        });
    server.start();
    url = "https://127.0.0.1:" + server.getAddress().getPort();
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
      return "https://127.0.0.1:" + socket.getLocalPort();
    }
  }

  @Test
  void pairsThroughTheFirstAddressThatAnswersThenSendsItsToken() throws IOException {
    var desktop =
        client.pair(new PairingCode(2, "desk", List.of(deadUrl(), url), "good", pin), "Pixel");
    assertEquals(new Desktop("desk", url, "t0k", pin), desktop);
    assertEquals(new Hello("desk", "0.1.0", 3, 4), client.hello(desktop));
    assertEquals("Bearer t0k", authorization);
  }

  @Test
  void aRefusalStopsAtOnceWithTheDesktopsMessage() {
    var refused =
        assertThrows(
            DesktopException.class,
            () ->
                client.pair(
                    new PairingCode(2, "desk", List.of(url, deadUrl()), "old", pin), "Pixel"));
    assertEquals(400, refused.status());
    assertEquals("This pairing code has expired", refused.getMessage());
  }

  @Test
  void aDesktopWithAnotherCertificateIsRefusedBeforeTheTokenIsSent() {
    var impostor =
        assertThrows(
            DesktopException.class,
            () -> client.hello(new Desktop("desk", url, "t0k", OTHER_PIN)),
            "not retryable: the copy job must not keep trying");
    assertEquals(0, impostor.status());
    assertTrue(impostor.getMessage().contains("Pair the phone again"));
    assertNull(authorization, "the token never left the phone");
    assertThrows(
        DesktopException.class,
        () -> client.pair(new PairingCode(2, "desk", List.of(url), "good", OTHER_PIN), "Pixel"));
  }

  @Test
  void plainHttpIsRefused() {
    var error =
        assertThrows(
            IOException.class,
            () -> client.hello(new Desktop("desk", url.replace("https", "http"), "t0k", pin)));
    assertTrue(error.getMessage().contains("not HTTPS"));
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

  @Test
  void aDesktopIgnoringRangeRestartsThePartAndAChangedFileIsDropped() throws Exception {
    var desktop = new Desktop("desk", url, "t0k", pin);
    var folder = new PathTransferFolder(phone);
    var file =
        new TitleFile(
            TitleFile.MEDIA,
            "fr/film.mp4",
            media.length,
            fingerprint(media),
            PhoneRoutes.titleFile("film", TitleFile.MEDIA));
    try (var out = folder.appendPart(file.segments())) {
      out.write("01234".getBytes(StandardCharsets.UTF_8));
    }
    client.download(desktop, file, folder, bytes -> {}, () -> false);
    assertEquals("bytes=5-", range);
    assertArrayEquals(media, Files.readAllBytes(phone.resolve("fr/film.mp4")));

    var changed =
        new TitleFile(
            TitleFile.MEDIA,
            "fr/old.mp4",
            media.length,
            "v1:" + "0".repeat(64),
            PhoneRoutes.titleFile("film", TitleFile.MEDIA));
    var error =
        assertThrows(
            IOException.class,
            () -> client.download(desktop, changed, folder, bytes -> {}, () -> false));
    assertTrue(error.getMessage().contains("has changed on the desktop"));
    assertEquals(-1, folder.partSize(changed.segments()), "a mismatched part is deleted");
    assertEquals(-1, folder.size(changed.segments()));
  }

  @Test
  void pathsThatLeaveTheFolderAreRefused() {
    for (String path : List.of("../x", "/etc/x", "a//b", "a/./b", "a\\b", ""))
      assertThrows(
          IllegalArgumentException.class,
          () -> new TitleFile(TitleFile.MEDIA, path, 1, null, "/x").segments(),
          path);
  }

  private static String fingerprint(byte[] bytes) throws IOException {
    Path file = Files.createTempFile("media", ".bin");
    try {
      Files.write(file, bytes);
      return MediaFingerprint.of(file);
    } finally {
      Files.delete(file);
    }
  }
}
