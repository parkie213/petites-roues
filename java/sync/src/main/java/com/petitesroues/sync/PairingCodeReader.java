package com.petitesroues.sync;

import com.petitesroues.model.sync.PairingCode;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads what a phone scanned from the desktop's QR code, refusing anything that is not a pairing
 * code this build can use. The messages are for the person holding the phone.
 */
public final class PairingCodeReader {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private PairingCodeReader() {}

  /**
   * The pairing code in {@code text}.
   *
   * @throws IllegalArgumentException if {@code text} is not a petites roues pairing code, is
   *     incomplete, or is from a newer desktop (the message then says to update the app)
   */
  public static PairingCode read(String text) {
    JsonNode json;
    try {
      json = MAPPER.readTree(text);
    } catch (JacksonException notJson) {
      json = null;
    }
    if (json == null || !json.isObject())
      throw new IllegalArgumentException("This QR code is not from petites roues");
    int version = json.path("v").asInt(0);
    if (version > PairingCode.VERSION)
      throw new IllegalArgumentException("Update the app to pair with this desktop");
    if (version >= 1 && version < PairingCode.VERSION)
      throw new IllegalArgumentException("Update petites roues on the desktop to pair with it");
    if (version != PairingCode.VERSION)
      throw new IllegalArgumentException("This QR code is not from petites roues");
    List<String> urls = new ArrayList<>();
    for (JsonNode url : json.path("urls")) {
      if (url.isString() && url.asString().startsWith("https://")) urls.add(url.asString());
    }
    String secret = json.path("pair").asString("");
    String cert = json.path("cert").asString("");
    if (urls.isEmpty() || secret.isEmpty() || !CertificatePins.valid(cert))
      throw new IllegalArgumentException("This pairing code is incomplete");
    return new PairingCode(
        version, json.path("name").asString("Desktop"), List.copyOf(urls), secret, cert);
  }
}
