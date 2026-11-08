package com.petitesroues.sync;

import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.model.sync.PairingCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PairingCodeReaderTest {
  private static final String PIN = "sha256:" + "ab".repeat(32);

  @Test
  void readsTheDesktopsCode() {
    var code =
        PairingCodeReader.read(
            """
            {"v":2,"name":"desk","urls":["https://192.168.1.5:8878","http://10.0.0.2:8878"],"pair":"s3cret","cert":"%s"}"""
                .formatted(PIN));
    assertEquals(
        new PairingCode(2, "desk", List.of("https://192.168.1.5:8878"), "s3cret", PIN),
        code,
        "plain HTTP addresses are dropped");
  }

  @Test
  void refusesOtherCodesAndNewerVersions() {
    assertThrows(
        IllegalArgumentException.class, () -> PairingCodeReader.read("https://example.com"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PairingCodeReader.read("{\"v\":2,\"urls\":[],\"pair\":\"x\"}"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PairingCodeReader.read(
                "{\"v\":2,\"urls\":[\"https://a\"],\"pair\":\"x\",\"cert\":\"sha256:00\"}"),
        "a malformed pin");
    var older =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PairingCodeReader.read(
                    "{\"v\":1,\"name\":\"d\",\"urls\":[\"http://a\"],\"pair\":\"x\"}"));
    assertEquals("Update petites roues on the desktop to pair with it", older.getMessage());
    var newer =
        assertThrows(
            IllegalArgumentException.class,
            () -> PairingCodeReader.read("{\"v\":3,\"urls\":[\"https://a\"],\"pair\":\"x\"}"));
    assertEquals("Update the app to pair with this desktop", newer.getMessage());
  }
}
