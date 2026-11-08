package com.petitesroues.phone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.petitesroues.settings.SettingsStore;
import com.petitesroues.sync.CertificatePins;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PhoneCertificateServiceTest {
  /** A settings store over {@code values}. */
  static SettingsStore settings(Map<String, String> values) {
    var store = mock(SettingsStore.class);
    when(store.get(anyString()))
        .thenAnswer(call -> Optional.ofNullable(values.get(call.<String>getArgument(0))));
    doAnswer(call -> values.put(call.getArgument(0), call.getArgument(1)))
        .when(store)
        .put(anyString(), anyString());
    return store;
  }

  @Test
  void theCertificateIsMadeOnceAndKeptWithTheLibrary() throws Exception {
    Map<String, String> values = new HashMap<>();
    var first = new PhoneCertificateService(settings(values)).certificate();
    assertTrue(CertificatePins.valid(first.pin()));
    assertTrue(values.containsKey(PhoneCertificateService.KEY));
    assertTrue(values.containsKey(PhoneCertificateService.CERTIFICATE));
    first.certificate().verify(first.certificate().getPublicKey());

    var reloaded = new PhoneCertificateService(settings(values)).certificate();
    assertEquals(first.pin(), reloaded.pin(), "a restart keeps the pin phones trust");
    assertEquals(first.key(), reloaded.key());
    assertNotNull(reloaded.keyStore("phone", "x".toCharArray()).getKey("phone", "x".toCharArray()));

    var fresh = new PhoneCertificateService(settings(new HashMap<>())).certificate();
    assertNotEquals(first.pin(), fresh.pin(), "each library has its own");
  }
}
