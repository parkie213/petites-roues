package com.petitesroues.sync;

import com.petitesroues.model.sync.Desktop;
import com.petitesroues.model.sync.Hello;
import com.petitesroues.model.sync.PairRequest;
import com.petitesroues.model.sync.PairingCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The phone's side of the phone listener protocol ({@link PhoneRoutes}). It uses {@link
 * HttpURLConnection} because {@code java.net.http} is missing on Android. Calls block, so Android
 * runs them off the main thread. Every request but pairing carries the phone's bearer token.
 */
public class DesktopClient {
  private static final int CONNECT_TIMEOUT_MS = 3_000;
  private static final int READ_TIMEOUT_MS = 10_000;

  private final JsonMapper mapper = JsonMapper.builder().build();

  /**
   * Pairs with the desktop: tries each of the code's URLs in order and pairs through the first that
   * answers. A refusal stops at once, since every address leads to the same desktop.
   *
   * @throws DesktopException if the desktop refuses (the code expired or was already used)
   * @throws IOException the last connection failure, when no address answers
   */
  public Desktop pair(PairingCode code, String phoneName) throws IOException {
    String body = mapper.writeValueAsString(new PairRequest(code.pair(), phoneName));
    IOException failure = new IOException("No address to try");
    for (String url : code.urls()) {
      JsonNode answer;
      try {
        answer = request("POST", url + PhoneRoutes.PAIR, null, body);
      } catch (DesktopException refused) {
        throw refused;
      } catch (IOException unreachable) {
        failure = unreachable;
        continue;
      }
      return new Desktop(
          answer.path("name").asString(code.name()), url, answer.path("token").asString());
    }
    throw failure;
  }

  /**
   * The connection test.
   *
   * @throws DesktopException if the desktop refuses, typically because it forgot this phone (401)
   */
  public Hello hello(Desktop desktop) throws IOException {
    JsonNode answer = request("GET", desktop.url() + PhoneRoutes.HELLO, desktop.token(), null);
    JsonNode version = answer.path("version");
    return new Hello(
        answer.path("name").asString(desktop.name()),
        version.isString() ? version.asString() : null,
        answer.path("titles").asInt(),
        answer.path("media").asInt());
  }

  private JsonNode request(String method, String url, String token, String body)
      throws IOException {
    var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    try {
      connection.setRequestMethod(method);
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);
      connection.setRequestProperty("Accept", "application/json");
      if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
      if (body != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = connection.getOutputStream()) {
          out.write(body.getBytes(StandardCharsets.UTF_8));
        }
      }
      int status = connection.getResponseCode();
      boolean ok = status >= 200 && status < 300;
      String text = read(ok ? connection.getInputStream() : connection.getErrorStream());
      if (!ok) throw new DesktopException(status, message(status, text));
      try {
        return mapper.readTree(text);
      } catch (JacksonException garbled) {
        throw new IOException("The desktop's answer was not JSON", garbled);
      }
    } finally {
      connection.disconnect();
    }
  }

  private static String read(InputStream stream) throws IOException {
    if (stream == null) return "";
    try (stream) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * The desktop's error message, or a plain one for its status. The app's own errors carry it in
   * {@code error}; Spring's error page in {@code message}, beside the status name in {@code error}.
   */
  String message(int status, String text) {
    String given = "";
    try {
      JsonNode json = mapper.readTree(text);
      // Spring's error page has a status, and its error is only that status's name.
      given = json.path(json.has("status") ? "message" : "error").asString("");
    } catch (JacksonException notJson) {
      // Fall through to the plain message.
    }
    if (!given.isBlank()) return given;
    return switch (status) {
      case 401 -> "This phone is no longer paired. Pair it again.";
      case 404 -> "That address is not a petites roues desktop.";
      default -> "The desktop answered " + status + ".";
    };
  }
}
