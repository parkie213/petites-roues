package app.petitesroues.speech;

import app.petitesroues.model.speech.CatalogueVoice;
import app.petitesroues.model.speech.VoiceCatalogue;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads Piper's upstream catalogue and installs verified model/configuration pairs. HTTP and file
 * work is synchronous on the caller's thread; the composition layer owns the borrowed HTTP client.
 * No models are downloaded at startup. Existing complete installations are never overwritten.
 */
public final class VoiceCatalogueService {
  private static final Logger LOG = LoggerFactory.getLogger(VoiceCatalogueService.class);
  private final Path directory;
  private final HttpClient http;
  private final ObjectMapper json;
  private final URI catalogue;
  private final URI repository;
  private final URI samples;
  private JsonNode cached;
  private Instant fetched = Instant.EPOCH;
  private Instant retryAfter = Instant.EPOCH;
  private String warning;

  public VoiceCatalogueService(
      Path directory,
      HttpClient http,
      ObjectMapper json,
      URI catalogue,
      URI repository,
      URI samples) {
    this.directory = directory;
    this.http = http;
    this.json = json;
    this.catalogue = catalogue;
    this.repository = repository;
    this.samples = samples;
  }

  /** Lists voices using fresh or saved metadata; installed state is always read from disk. */
  public List<CatalogueVoice> voices() throws IOException, InterruptedException {
    return catalogue(false).voices();
  }

  /** Returns freshness and last-known-good data, including when an explicit refresh fails. */
  public synchronized VoiceCatalogue catalogue(boolean refresh)
      throws IOException, InterruptedException {
    JsonNode source = loadCatalogue(refresh);
    var result = new ArrayList<CatalogueVoice>();
    for (var entry : source.properties()) {
      String id = entry.getKey();
      if (!id.matches("[A-Za-z0-9_-]+")) continue;
      JsonNode voice = entry.getValue();
      String model = modelPath(id, voice);
      result.add(
          new CatalogueVoice(
              id,
              voice.path("name").asString(),
              voice.path("language").path("family").asString(),
              voice.path("language").path("code").asString().replace('_', '-'),
              voice.path("quality").asString(),
              voice.path("num_speakers").asInt(),
              voice.path("files").path(model).path("size_bytes").asLong(),
              repository
                  .resolve(model.substring(0, model.lastIndexOf('/') + 1) + "MODEL_CARD")
                  .toString(),
              installed(id)));
    }
    result.sort(Comparator.comparing(CatalogueVoice::region).thenComparing(CatalogueVoice::id));
    return new VoiceCatalogue(
        result, fetched, Instant.now().isAfter(fetched.plus(Duration.ofHours(1))), warning);
  }

  /** Returns speaker zero's upstream comparison sample without downloading the model. */
  public byte[] sample(String id) throws IOException, InterruptedException {
    var voice = entry(id);
    String model = modelPath(id, voice);
    URI uri = samples.resolve(model.substring(0, model.lastIndexOf('/') + 1) + "speaker_0.mp3");
    var response =
        http.send(request(uri, Duration.ofSeconds(30)), HttpResponse.BodyHandlers.ofByteArray());
    requireSuccess(response.statusCode());
    if (response.body().length > 8 * 1024 * 1024)
      throw new IOException("Voice sample is too large");
    return response.body();
  }

  /**
   * Installs both verified files before publishing the configuration last, making the voice
   * visible. A failed download leaves no partial installed pair. Calls are serialized and repeat
   * installs are harmless. The upstream model card is retained beside the model under a unique
   * name.
   */
  public synchronized void install(String id) throws IOException, InterruptedException {
    JsonNode voice = entry(id);
    if (installed(id)) return;
    String model = modelPath(id, voice);
    String config = model + ".json";
    String card = model.substring(0, model.lastIndexOf('/') + 1) + "MODEL_CARD";
    Files.createDirectories(directory);
    Path targetModel = directory.resolve(id + ".onnx");
    Path targetConfig = directory.resolve(id + ".onnx.json");
    if (Files.exists(targetModel) || Files.exists(targetConfig))
      throw new IllegalArgumentException("An incomplete voice already exists: " + id);
    Path stage = Files.createTempDirectory(directory, ".download-");
    boolean publishedModel = false;
    boolean complete = false;
    try {
      download(model, voice.path("files").path(model), stage.resolve("model"));
      download(config, voice.path("files").path(config), stage.resolve("config"));
      download(card, voice.path("files").path(card), stage.resolve("card"));
      // Parse configuration before publishing it. Piper performs full model validation at load.
      if (!json.readTree(Files.readAllBytes(stage.resolve("config"))).isObject())
        throw new IOException("Invalid voice configuration");
      Files.move(
          stage.resolve("card"),
          directory.resolve(id + ".MODEL_CARD"),
          StandardCopyOption.REPLACE_EXISTING);
      Files.move(stage.resolve("model"), targetModel, StandardCopyOption.ATOMIC_MOVE);
      publishedModel = true;
      Files.move(stage.resolve("config"), targetConfig, StandardCopyOption.ATOMIC_MOVE);
      complete = true;
    } finally {
      if (publishedModel && !complete) Files.deleteIfExists(targetModel);
      try (var files = Files.list(stage)) {
        for (Path file : files.toList()) Files.deleteIfExists(file);
      }
      Files.deleteIfExists(stage);
    }
  }

  private boolean installed(String id) {
    return Files.isRegularFile(directory.resolve(id + ".onnx"))
        && Files.isRegularFile(directory.resolve(id + ".onnx.json"));
  }

  /**
   * Loads saved metadata lazily. A failed refresh preserves both the old data and its original
   * fetch time; expired copies remain usable. Automatic retries after failure wait one minute,
   * while an explicit refresh always retries immediately. Interruption is never swallowed.
   */
  private synchronized JsonNode loadCatalogue(boolean refresh)
      throws IOException, InterruptedException {
    if (cached == null) readSavedCatalogue();
    Instant now = Instant.now();
    if (refresh
        || cached == null
        || (now.isAfter(fetched.plus(Duration.ofHours(1))) && now.isAfter(retryAfter))) {
      try {
        var response =
            http.send(
                request(catalogue, Duration.ofSeconds(20)),
                HttpResponse.BodyHandlers.ofByteArray());
        requireSuccess(response.statusCode());
        if (response.body().length > 4 * 1024 * 1024)
          throw new IOException("Voice catalogue is too large");
        JsonNode next = validateCatalogue(json.readTree(response.body()));
        Instant nextFetched = Instant.now();
        String saveWarning = null;
        try {
          saveCatalogue(next, nextFetched);
        } catch (IOException failure) {
          LOG.warn("Could not save voice catalogue", failure);
          saveWarning = "Catalogue fetched, but could not save it for offline use.";
        }
        cached = next;
        fetched = nextFetched;
        warning = saveWarning;
        retryAfter = Instant.EPOCH;
      } catch (IOException | JacksonException failure) {
        if (cached == null)
          throw new IOException(
              "Could not fetch voice catalogue: " + failure.getMessage(), failure);
        LOG.warn("Could not refresh voice catalogue; retaining previous copy", failure);
        warning = "Could not refresh the catalogue. Showing the last available copy.";
        retryAfter = Instant.now().plus(Duration.ofMinutes(1));
      }
    }
    return cached;
  }

  private JsonNode validateCatalogue(JsonNode value) throws IOException {
    if (value == null || !value.isObject()) throw new IOException("Invalid voice catalogue");
    for (var entry : value.properties()) {
      if (entry.getKey().matches("[A-Za-z0-9_-]+")) modelPath(entry.getKey(), entry.getValue());
    }
    return value;
  }

  private Path cacheFile() {
    return directory.resolve("catalogue-cache.json");
  }

  private void readSavedCatalogue() {
    Path file = cacheFile();
    if (!Files.isRegularFile(file)) return;
    try {
      if (Files.size(file) > 4 * 1024 * 1024 + 1024)
        throw new IOException("Saved catalogue is too large");
      JsonNode saved = json.readTree(Files.readAllBytes(file));
      if (saved == null || !catalogue.toString().equals(saved.path("source").asString())) return;
      Instant savedAt = Instant.parse(saved.path("fetchedAt").asString());
      JsonNode voices = validateCatalogue(saved.get("voices"));
      cached = voices;
      fetched = savedAt;
    } catch (IOException | RuntimeException failure) {
      LOG.warn("Ignoring invalid saved voice catalogue", failure);
    }
  }

  /** Atomic replacement keeps a previous complete catalogue intact if writing fails. */
  private void saveCatalogue(JsonNode voices, Instant fetchedAt) throws IOException {
    Files.createDirectories(directory);
    Path temporary = Files.createTempFile(directory, ".catalogue-", ".tmp");
    try {
      Files.write(
          temporary,
          json.writeValueAsBytes(
              Map.of(
                  "source",
                  catalogue.toString(),
                  "fetchedAt",
                  fetchedAt.toString(),
                  "voices",
                  voices)));
      Files.move(
          temporary,
          cacheFile(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private JsonNode entry(String id) throws IOException, InterruptedException {
    if (!id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid voice ID");
    JsonNode voice = loadCatalogue(false).get(id);
    if (voice == null) throw new NoSuchElementException("No catalogue voice: " + id);
    return voice;
  }

  private String modelPath(String id, JsonNode voice) throws IOException {
    for (var file : voice.path("files").properties()) {
      String path = file.getKey();
      if (path.matches("[A-Za-z0-9_/-]+\\.onnx") && path.endsWith("/" + id + ".onnx")) return path;
    }
    throw new IOException("No model in catalogue for " + id);
  }

  private void download(String path, JsonNode metadata, Path target)
      throws IOException, InterruptedException {
    long size = metadata.path("size_bytes").asLong();
    String digest = metadata.path("md5_digest").asString();
    if (size <= 0 || size > 512L * 1024 * 1024 || !digest.matches("[a-f0-9]{32}"))
      throw new IOException("Invalid voice file metadata");
    var response =
        http.send(
            request(repository.resolve(path), Duration.ofMinutes(3)),
            HttpResponse.BodyHandlers.ofFile(target));
    requireSuccess(response.statusCode());
    if (Files.size(target) != size) throw new IOException("Voice download size mismatch");
    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      try (var input = Files.newInputStream(target)) {
        byte[] buffer = new byte[65536];
        for (int count; (count = input.read(buffer)) != -1; ) md5.update(buffer, 0, count);
      }
      if (!HexFormat.of().formatHex(md5.digest()).equals(digest))
        throw new IOException("Voice download checksum mismatch");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private HttpRequest request(URI uri, Duration timeout) {
    return HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
  }

  private void requireSuccess(int status) throws IOException {
    if (status != 200) throw new IOException("Voice provider returned HTTP " + status);
  }
}
