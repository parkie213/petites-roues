package app.petitesroues.speech;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class VoiceCatalogueServiceTest {
  @TempDir Path directory;
  private HttpServer server;
  private HttpClient http;
  private VoiceCatalogueService service;
  private final Map<String, byte[]> files = new LinkedHashMap<>();
  private static final String ID = "fr_FR-test-medium";
  private static final String FOLDER = "fr/fr_FR/test/medium/";
  private String catalogue;
  private int catalogueStatus = 200;
  private int catalogueRequests;
  private URI root;

  @BeforeEach
  void provider() throws Exception {
    files.put(FOLDER + ID + ".onnx", "model".getBytes(StandardCharsets.UTF_8));
    files.put(FOLDER + ID + ".onnx.json", "{}".getBytes(StandardCharsets.UTF_8));
    files.put(FOLDER + "MODEL_CARD", "Test model license".getBytes(StandardCharsets.UTF_8));
    files.put(FOLDER + "speaker_0.mp3", new byte[] {1, 2, 3});
    var metadata = new LinkedHashMap<String, Object>();
    for (var file : files.entrySet()) {
      if (file.getKey().endsWith(".mp3")) continue;
      metadata.put(
          file.getKey(),
          Map.of(
              "size_bytes",
              file.getValue().length,
              "md5_digest",
              HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(file.getValue()))));
    }
    catalogue =
        JsonMapper.builder()
            .build()
            .writeValueAsString(
                Map.of(
                    ID,
                    Map.of(
                        "name",
                        "test",
                        "language",
                        Map.of("family", "fr", "code", "fr_FR"),
                        "quality",
                        "medium",
                        "num_speakers",
                        1,
                        "files",
                        metadata)));
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath().substring(1);
          byte[] body =
              path.equals("voices.json")
                  ? catalogue.getBytes(StandardCharsets.UTF_8)
                  : files.get(path);
          int status = body == null ? 404 : 200;
          if (path.equals("voices.json")) {
            catalogueRequests++;
            status = catalogueStatus;
          }
          exchange.sendResponseHeaders(status, body == null ? -1 : body.length);
          if (body != null) exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    http = HttpClient.newHttpClient();
    root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    service =
        new VoiceCatalogueService(
            directory, http, JsonMapper.builder().build(), root.resolve("voices.json"), root, root);
  }

  @AfterEach
  void close() {
    if (http != null) http.close();
    if (server != null) server.stop(0);
  }

  @Test
  void listsSamplesAndInstallsVerifiedPairsWithoutOverwritingExistingModels() throws Exception {
    var voice = service.voices().getFirst();
    assertEquals(ID, voice.id());
    assertEquals("fr-FR", voice.region());
    assertFalse(voice.installed());
    assertArrayEquals(new byte[] {1, 2, 3}, service.sample(ID));
    service.install(ID);
    assertTrue(service.voices().getFirst().installed());
    assertEquals("model", Files.readString(directory.resolve(ID + ".onnx")));
    assertEquals("{}", Files.readString(directory.resolve(ID + ".onnx.json")));
    assertEquals("Test model license", Files.readString(directory.resolve(ID + ".MODEL_CARD")));
    files.clear();
    service.install(ID);
    try (var installed = Files.list(directory)) {
      assertEquals(4, installed.count());
    }
  }

  @Test
  void checksumFailureLeavesNoInstalledFilesAndCanBeRetried() throws Exception {
    byte[] original = files.put(FOLDER + ID + ".onnx", "wrong".getBytes(StandardCharsets.UTF_8));
    var failure = assertThrows(IOException.class, () -> service.install(ID));
    assertTrue(failure.getMessage().contains("checksum"));
    try (var installed = Files.list(directory)) {
      assertEquals(1, installed.count());
    }
    files.put(FOLDER + ID + ".onnx", original);
    service.install(ID);
    assertTrue(service.voices().getFirst().installed());
  }

  @Test
  void refreshFetchesNewMetadataWithoutWaitingForCacheExpiry() throws Exception {
    assertEquals(1, service.voices().size());
    catalogue = "{}";
    assertEquals(1, service.voices().size());
    assertTrue(service.catalogue(true).voices().isEmpty());
  }

  private VoiceCatalogueService restarted() {
    return new VoiceCatalogueService(
        directory, http, JsonMapper.builder().build(), root.resolve("voices.json"), root, root);
  }

  @Test
  void failedRefreshRetainsDataTimestampAndSavedCopyAcrossRestart() throws Exception {
    var first = service.catalogue(false);
    byte[] saved = Files.readAllBytes(directory.resolve("catalogue-cache.json"));
    catalogueStatus = 503;
    var fallback = service.catalogue(true);
    assertEquals(first.voices(), fallback.voices());
    assertEquals(first.fetchedAt(), fallback.fetchedAt());
    assertNotNull(fallback.warning());
    assertArrayEquals(saved, Files.readAllBytes(directory.resolve("catalogue-cache.json")));
    var restored = restarted().catalogue(false);
    assertEquals(first.voices(), restored.voices());
    assertEquals(first.fetchedAt(), restored.fetchedAt());
    assertEquals(2, catalogueRequests, "Fresh saved copy needs no network request after restart");
  }

  @Test
  void expiredSavedCopyRemainsUsableDuringOutageWithoutRepeatedAutomaticRetries() throws Exception {
    service.voices();
    Path file = directory.resolve("catalogue-cache.json");
    var mapper = JsonMapper.builder().build();
    var saved = (ObjectNode) mapper.readTree(Files.readAllBytes(file));
    Instant old = Instant.now().minus(Duration.ofHours(2));
    saved.put("fetchedAt", old.toString());
    Files.write(file, mapper.writeValueAsBytes(saved));
    catalogueStatus = 503;
    service = restarted();
    var fallback = service.catalogue(false);
    assertEquals(old, fallback.fetchedAt());
    assertTrue(fallback.stale());
    assertNotNull(fallback.warning());
    assertEquals(ID, fallback.voices().getFirst().id());
    service.sample(ID);
    service.voices();
    assertEquals(2, catalogueRequests, "Reuse stale metadata briefly between failed refreshes");
  }

  @Test
  void malformedProviderResponseCannotReplaceTheLastGoodCatalogue() throws Exception {
    var first = service.catalogue(false);
    catalogue = "{broken";
    var fallback = service.catalogue(true);
    assertEquals(first.voices(), fallback.voices());
    assertEquals(first.fetchedAt(), fallback.fetchedAt());
    assertNotNull(fallback.warning());
    assertEquals(first.voices(), restarted().voices());
  }

  @Test
  void corruptSavedCopyIsReplacedFromProviderButColdOutageReportsFailure() throws Exception {
    Files.writeString(directory.resolve("catalogue-cache.json"), "broken");
    assertEquals(ID, service.voices().getFirst().id());
    Files.delete(directory.resolve("catalogue-cache.json"));
    catalogueStatus = 503;
    assertThrows(IOException.class, () -> restarted().catalogue(false));
  }

  @Test
  void rejectsInvalidIdsAndPreservesIncompleteExistingFiles() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> service.install("../elsewhere"));
    Files.writeString(directory.resolve(ID + ".onnx"), "local file");
    assertThrows(IllegalArgumentException.class, () -> service.install(ID));
    assertEquals("local file", Files.readString(directory.resolve(ID + ".onnx")));
  }

  @Test
  void failedConfigOrModelCardDoesNotPublishAModel() throws Exception {
    files.remove(FOLDER + "MODEL_CARD");
    assertThrows(IOException.class, () -> service.install(ID));
    assertFalse(service.voices().getFirst().installed());
    try (var installed = Files.list(directory)) {
      assertEquals(1, installed.count());
    }
  }
}
