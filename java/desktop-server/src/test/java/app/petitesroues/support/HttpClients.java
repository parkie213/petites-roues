package app.petitesroues.support;

import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.sync.CertificatePins;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

/** Integration-test HTTP clients using the explicit document JSON adapter. */
public final class HttpClients {
  private HttpClients() {}

  public static RestClient client(String url) {
    return builder(url).build();
  }

  /** Builds a real atomic command request against the current displayed state. */
  public static RestClient.RequestBodySpec command(
      RestClient http, String title, String type, String target, Object value) {
    var json = StudyDocumentJson.newMapper();
    Object body = value;
    if (value instanceof String text) body = json.readTree(text);
    var input = new java.util.LinkedHashMap<String, Object>();
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
    java.util.List<?> commands = java.util.List.of(input);
    if (type.equals("review-requests")) {
      var items = json.valueToTree(body);
      var list = new java.util.ArrayList<Object>();
      for (var item : items)
        list.add(
            java.util.Map.of(
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
            java.util.Map.of(
                "requestId",
                java.util.UUID.randomUUID().toString(),
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
