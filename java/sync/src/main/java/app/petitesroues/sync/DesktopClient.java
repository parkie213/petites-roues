package app.petitesroues.sync;

import app.petitesroues.sync.model.Desktop;
import app.petitesroues.sync.model.DesktopTitle;
import app.petitesroues.sync.model.Hello;
import app.petitesroues.sync.model.PairingCode;
import app.petitesroues.sync.model.TitleFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import javax.net.ssl.SSLException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The phone's side of the phone listener protocol ({@link PhoneRoutes}). It uses {@link
 * HttpURLConnection} because {@code java.net.http} is missing on Android. Calls block, so Android
 * runs them off the main thread. Every request goes over HTTPS to a desktop whose certificate has
 * the pinned key ({@link CertificatePins}), and every one but pairing carries the phone's bearer
 * token.
 */
public class DesktopClient {
  private static final int CONNECT_TIMEOUT_MS = 3_000;
  private static final int READ_TIMEOUT_MS = 10_000;
  private static final int CHUNK = 64 * 1024;

  private final JsonMapper mapper = JsonMapper.builder().build();

  /**
   * Pairs with the desktop: tries each of the code's URLs in order and pairs through the first that
   * answers. A refusal stops at once, since every address leads to the same desktop.
   *
   * @throws DesktopException if the desktop refuses (the code expired or was already used), or its
   *     certificate is not the one the code names
   * @throws IOException the last connection failure, when no address answers
   */
  public Desktop pair(PairingCode code, String phoneName) throws IOException {
    // A PairRequest, written by hand: Android's D8 compiles records into plain classes, which
    // Jackson cannot serialise (it writes {}), though it still reads them through their
    // constructor.
    String body =
        mapper.createObjectNode().put("secret", code.pair()).put("name", phoneName).toString();
    IOException failure = new IOException("No address to try");
    for (String url : code.urls()) {
      JsonNode answer;
      try {
        answer = request("POST", url + PhoneRoutes.PAIR, code.cert(), null, body);
      } catch (DesktopException refused) {
        throw refused;
      } catch (IOException unreachable) {
        failure = unreachable;
        continue;
      }
      return new Desktop(
          answer.path("name").asString(code.name()),
          url,
          answer.path("token").asString(),
          code.cert());
    }
    throw failure;
  }

  /**
   * The connection test.
   *
   * @throws DesktopException if the desktop refuses, typically because it forgot this phone (401)
   */
  public Hello hello(Desktop desktop) throws IOException {
    JsonNode answer =
        request("GET", desktop.url() + PhoneRoutes.HELLO, desktop.pin(), desktop.token(), null);
    JsonNode version = answer.path("version");
    return new Hello(
        answer.path("name").asString(desktop.name()),
        version.isString() ? version.asString() : null,
        answer.path("titles").asInt(),
        answer.path("media").asInt());
  }

  /**
   * The titles the desktop offers, each with the files it needs.
   *
   * @throws DesktopException if the desktop refuses, typically because it forgot this phone (401)
   */
  public List<DesktopTitle> titles(Desktop desktop) throws IOException {
    String text =
        text("GET", desktop.url() + PhoneRoutes.TITLES, desktop.pin(), desktop.token(), null);
    try {
      return mapper.readerForListOf(DesktopTitle.class).readValue(text);
    } catch (JacksonException garbled) {
      throw new IOException("The desktop's list of titles was not readable", garbled);
    }
  }

  /**
   * Downloads one of a title's files into {@code folder}, resuming its part with {@code Range} when
   * the desktop allows it. The part is committed only when complete and, for media, when its
   * fingerprint matches; a mismatch deletes it, since the desktop's file has changed.
   *
   * @param progress told each chunk's size as it is written
   * @param cancelled checked between chunks; stopping keeps the part for later
   * @throws CancellationException if {@code cancelled} said to stop
   */
  public void download(
      Desktop desktop,
      TitleFile file,
      TransferFolder folder,
      LongConsumer progress,
      BooleanSupplier cancelled)
      throws IOException {
    var path = file.segments();
    long have = folder.partSize(path);
    if (file.whole() || have > file.bytes()) {
      folder.deletePart(path);
      have = 0;
    }
    have = Math.max(0, have);
    if (have < file.bytes() || file.whole()) {
      String url = desktop.url() + file.source();
      var connection = open("GET", url, desktop.pin(), desktop.token(), null);
      try {
        connection.setRequestProperty("Accept", "*/*");
        if (have > 0) connection.setRequestProperty("Range", "bytes=" + have + "-");
        int status = status(connection);
        if (status == 200 && have > 0) {
          // The desktop sent the whole file instead of the rest: start the part again.
          folder.deletePart(path);
          have = 0;
        } else if (status != 200 && status != 206) {
          throw new DesktopException(status, message(status, read(connection.getErrorStream())));
        }
        try (InputStream in = connection.getInputStream();
            OutputStream out = folder.appendPart(path)) {
          byte[] buffer = new byte[CHUNK];
          for (int n; (n = in.read(buffer)) > 0; ) {
            if (cancelled.getAsBoolean()) throw new CancellationException();
            out.write(buffer, 0, n);
            progress.accept(n);
          }
        }
      } finally {
        connection.disconnect();
      }
    }
    String name = path.get(path.size() - 1);
    if (!file.whole() && folder.partSize(path) != file.bytes())
      throw new IOException("The download of " + name + " stopped early. Try again to resume it.");
    if (file.fingerprint() != null) {
      boolean same;
      try (SeekableByteChannel channel = folder.openPart(path)) {
        same = FileFingerprints.matches(file.fingerprint(), channel);
      }
      if (!same) {
        folder.deletePart(path);
        throw new IOException(
            name + " has changed on the desktop. Refresh the desktop library and try again.");
      }
    }
    folder.commit(path);
  }

  private JsonNode request(String method, String url, String pin, String token, String body)
      throws IOException {
    String text = text(method, url, pin, token, body);
    try {
      return mapper.readTree(text);
    } catch (JacksonException garbled) {
      throw new IOException("The desktop's answer was not JSON", garbled);
    }
  }

  private String text(String method, String url, String pin, String token, String body)
      throws IOException {
    var connection = open(method, url, pin, token, body);
    try {
      int status = status(connection);
      boolean ok = status >= 200 && status < 300;
      String text = read(ok ? connection.getInputStream() : connection.getErrorStream());
      if (!ok) throw new DesktopException(status, message(status, text));
      return text;
    } finally {
      connection.disconnect();
    }
  }

  /**
   * A connection that trusts only the pinned certificate, not yet sent; without a body, headers can
   * still be set before {@link #status} sends it.
   *
   * @throws DesktopException if the desktop's certificate is not the pinned one
   */
  private static HttpURLConnection open(
      String method, String url, String pin, String token, String body) throws IOException {
    var connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    CertificatePins.secure(connection, pin);
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
      } catch (SSLException refused) {
        throw pinRefused(refused);
      }
    }
    return connection;
  }

  /**
   * Sends the request and returns the response's status.
   *
   * @throws DesktopException if the desktop's certificate is not the pinned one
   */
  private static int status(HttpURLConnection connection) throws IOException {
    try {
      return connection.getResponseCode();
    } catch (SSLException refused) {
      throw pinRefused(refused);
    }
  }

  /** A handshake failure as the phone reports it: a pin mismatch is a refusal, not a glitch. */
  private static IOException pinRefused(SSLException refused) {
    if (!CertificatePins.mismatch(refused)) return refused;
    return new DesktopException(
        "This is not the desktop this phone paired with. Pair the phone again.", refused);
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
