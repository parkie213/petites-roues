package app.petitesroues.speech;

import app.petitesroues.model.speech.CatalogueVoice;
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
import java.util.NoSuchElementException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads Piper's upstream catalogue and installs verified model/configuration pairs. HTTP and file
 * work is synchronous on the caller's thread; the composition layer owns the borrowed HTTP client.
 * No models are downloaded at startup. Existing complete installations are never overwritten.
 */
public final class VoiceCatalogueService {
  private final Path directory;
  private final HttpClient http;
  private final ObjectMapper json;
  private final URI catalogue;
  private final URI repository;
  private final URI samples;
  private JsonNode cached;
  private Instant fetched = Instant.EPOCH;

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

  /**
   * Fetches catalogue metadata on demand, caching it for one hour; installed state is always fresh.
   */
  public List<CatalogueVoice> voices() throws IOException, InterruptedException {
    return voices(false);
  }

  /** Explicit refresh bypasses the metadata cache, including after a provider-side model update. */
  public synchronized List<CatalogueVoice> voices(boolean refresh)
      throws IOException, InterruptedException {
    if (refresh) cached = null;
    var result = new ArrayList<CatalogueVoice>();
    for (var entry : catalogue().properties()) {
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
    return List.copyOf(result);
  }

  /** Returns speaker zero's upstream comparison sample without downloading the model. */
  public byte[] sample(String id) throws IOException, InterruptedException {
    var voice = entry(id);
    String model = modelPath(id, voice);
    URI uri = samples.resolve(model.substring(0, model.lastIndexOf('/') + 1) + "speaker_0.mp3");
    var response = http.send(request(uri), HttpResponse.BodyHandlers.ofByteArray());
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

  private synchronized JsonNode catalogue() throws IOException, InterruptedException {
    if (cached == null || Instant.now().isAfter(fetched.plus(Duration.ofHours(1)))) {
      var response = http.send(request(catalogue), HttpResponse.BodyHandlers.ofByteArray());
      requireSuccess(response.statusCode());
      if (response.body().length > 4 * 1024 * 1024)
        throw new IOException("Voice catalogue is too large");
      JsonNode next = json.readTree(response.body());
      if (next == null || !next.isObject()) throw new IOException("Invalid voice catalogue");
      cached = next;
      fetched = Instant.now();
    }
    return cached;
  }

  private JsonNode entry(String id) throws IOException, InterruptedException {
    if (!id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid voice ID");
    JsonNode voice = catalogue().get(id);
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
        http.send(request(repository.resolve(path)), HttpResponse.BodyHandlers.ofFile(target));
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

  private HttpRequest request(URI uri) {
    return HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3)).GET().build();
  }

  private void requireSuccess(int status) throws IOException {
    if (status != 200) throw new IOException("Voice provider returned HTTP " + status);
  }
}
