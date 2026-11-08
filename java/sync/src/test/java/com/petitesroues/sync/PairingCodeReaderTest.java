package com.petitesroues.sync;

import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.model.sync.PairingCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PairingCodeReaderTest {
  @Test
  void readsTheDesktopsCode() {
    var code =
        PairingCodeReader.read(
            """
            {"v":1,"name":"desk","urls":["http://192.168.1.5:8878","http://10.0.0.2:8878"],"pair":"s3cret"}""");
    assertEquals(
        new PairingCode(
            1, "desk", List.of("http://192.168.1.5:8878", "http://10.0.0.2:8878"), "s3cret"),
        code);
  }

  @Test
  void refusesOtherCodesAndNewerVersions() {
    assertThrows(
        IllegalArgumentException.class, () -> PairingCodeReader.read("https://example.com"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PairingCodeReader.read("{\"v\":1,\"urls\":[],\"pair\":\"x\"}"));
    var newer =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PairingCodeReader.read(
                    """
                    {"v":2,"name":"d","urls":["https://a"],"pair":"x","cert":"sha256:00"}"""));
    assertEquals("Update the app to pair with this desktop", newer.getMessage());
  }
}
