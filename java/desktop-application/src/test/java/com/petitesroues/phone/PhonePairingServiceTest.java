package com.petitesroues.phone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.petitesroues.model.phone.PairedPhone;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PhonePairingServiceTest {
  private final PhoneStore store = mock(PhoneStore.class);
  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-11-08T12:00:00Z"));
  private final Clock clock =
      new Clock() {
        public java.time.ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        public Clock withZone(java.time.ZoneId zone) {
          return this;
        }

        public Instant instant() {
          return now.get();
        }
      };
  private final PhoneCertificateService certificates =
      new PhoneCertificateService(PhoneCertificateServiceTest.settings(new HashMap<>()));
  private final PhonePairingService pairing = new PhonePairingService(store, certificates, clock);

  @Test
  void aSecretPairsOncePhoneKeepsOnlyTheTokensHash() {
    var started = pairing.start("desk", List.of("https://192.168.1.5:8878"));
    assertEquals(2, started.code().v());
    assertEquals(List.of("https://192.168.1.5:8878"), started.code().urls());
    assertEquals(
        certificates.certificate().pin(), started.code().cert(), "the code carries the pin");
    assertEquals(now.get().plus(PhonePairingService.SECRET_LIFETIME), started.expiresAt());

    var token = pairing.pair(started.code().pair(), " Pixel ", "desk");
    assertEquals("desk", token.name());
    verify(store)
        .insert(
            argThat(phone -> phone.name().equals("Pixel")),
            eq(PhonePairingService.sha256(token.token())));
    assertThrows(
        IllegalArgumentException.class,
        () -> pairing.pair(started.code().pair(), "Pixel", "desk"),
        "a secret is spent once used");
  }

  @Test
  void anExpiredOrUnknownSecretIsRefused() {
    var started = pairing.start("desk", List.of());
    now.set(now.get().plus(PhonePairingService.SECRET_LIFETIME).plus(Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class, () -> pairing.pair(started.code().pair(), "P", "desk"));
    assertThrows(IllegalArgumentException.class, () -> pairing.pair("guess", "P", "desk"));
    assertThrows(IllegalArgumentException.class, () -> pairing.pair(null, "P", "desk"));
    verify(store, never()).insert(any(), any());
  }

  @Test
  void aKnownTokenAuthenticatesAndIsSeen() {
    var phone = new PairedPhone("p1", "Pixel", "2026-11-08T11:00:00Z", null);
    when(store.withToken(PhonePairingService.sha256("good"))).thenReturn(Optional.of(phone));
    assertEquals(Optional.of(phone), pairing.authenticate("good"));
    verify(store).seen("p1", now.get().toString());
    assertTrue(pairing.authenticate("bad").isEmpty());
    assertTrue(pairing.authenticate(null).isEmpty());
  }
}
