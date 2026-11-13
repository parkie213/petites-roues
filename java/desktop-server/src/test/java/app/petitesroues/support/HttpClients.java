package app.petitesroues.support;

import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.model.library.LibraryJob;
import app.petitesroues.sync.CertificatePins;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/** Integration-test HTTP clients using the explicit document JSON adapter. */
public final class HttpClients {
  private HttpClients() {}

  public static RestClient client(String url) {
    return builder(url).build();
  }

  /**
   * Runs the public job contract to completion and independently reads its committed repository.
   */
  public static <T> T discover(
      RestClient http, String kind, String repositoryId, Object value, Class<T> type) {
    var json = StudyDocumentJson.newMapper();
    var request = new LinkedHashMap<String, Object>();
    if (value != null)
      request.putAll(json.convertValue(value, new TypeReference<Map<String, Object>>() {}));
    request.put("kind", kind);
    if (repositoryId != null) request.put("repositoryId", repositoryId);
    var started = http.post().uri("/api/jobs").body(request).retrieve().body(LibraryJob.class);
    String id = started.id();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> http.get().uri("/api/jobs/" + id).retrieve().body(LibraryJob.class).finished());
    var job = http.get().uri("/api/jobs/" + id).retrieve().body(LibraryJob.class);
    Assertions.assertEquals("succeeded", job.state(), job.error());
    if (job.outcomes().isEmpty()) return null;
    var outcome = job.outcomes().getFirst();
    var repository = http.get().uri("/api/repositories").retrieve().body(JsonNode.class);
    for (var row : repository)
      if (row.path("id").asString().equals(outcome.repositoryId()))
        return json.treeToValue(row, type);
    throw new AssertionError("Completed discovery has no persisted repository");
  }

  /** Builds a real atomic command request against the current displayed state. */
  public static RestClient.RequestBodySpec command(
      RestClient http, String title, String type, String target, Object value) {
    var json = StudyDocumentJson.newMapper();
    Object body = value;
    if (value instanceof String text) body = json.readTree(text);
    var input = new LinkedHashMap<String, Object>();
    input.put("type", type);
    if (target != null)
      input.put(
          type.equals("edit-comic-text")
              ? "lineId"
              : type.contains("comic-box") ? "boxId" : "cueId",
          target);
    switch (type) {
      case "timing" -> input.put("edits", body);
      case "edit-video-cue", "add-video-cue", "edit-comic-text" -> input.put("edit", body);
      case "split-video-cue" -> input.put("split", body);
      case "create-comic-box" -> input.put("create", body);
      case "place-comic-box" -> input.put("update", body);
      case "comment", "suggestion" ->
          input.put(
              "text",
              json.valueToTree(body)
                  .path(type.equals("comment") ? "text" : "suggestion")
                  .asString(null));
      default -> {}
    }
    List<?> commands = List.of(input);
    if (type.equals("review-requests")) {
      var items = json.valueToTree(body);
      var list = new ArrayList<Object>();
      for (var item : items)
        list.add(
            Map.of(
                "type",
                "review-request",
                "cueId",
                item.path("cueId").asString(),
                "text",
                item.path("text").asString()));
      commands = list;
    }
    var version =
        http.get()
            .uri(title)
            .retrieve()
            .toBodilessEntity()
            .getHeaders()
            .getFirst("X-State-Version");
    return http.post()
        .uri(title + "/commands")
        .body(
            Map.of(
                "requestId",
                UUID.randomUUID().toString(),
                "expectedVersion",
                version,
                "commands",
                commands));
  }

  /**
   * A client for the phone listener at {@code url}, trusting only the certificate {@code pin}
   * names, as a phone does.
   */
  public static RestClient phone(String url, String pin) {
    var pinned =
        new SimpleClientHttpRequestFactory() {
          @Override
          protected void prepareConnection(HttpURLConnection connection, String method)
              throws IOException {
            CertificatePins.secure(connection, pin);
            super.prepareConnection(connection, method);
          }
        };
    return builder(url).requestFactory(pinned).build();
  }

  /** Decodes domain objects through the explicit wire adapter, just like a real JSON client. */
  private static RestClient.Builder builder(String url) {
    return RestClient.builder()
        .baseUrl(url)
        .messageConverters(
            converters ->
                converters.replaceAll(
                    converter ->
                        converter instanceof JacksonJsonHttpMessageConverter
                            ? new JacksonJsonHttpMessageConverter(StudyDocumentJson.newMapper())
                            : converter));
  }
}
