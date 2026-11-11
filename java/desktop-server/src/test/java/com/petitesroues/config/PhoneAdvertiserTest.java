package com.petitesroues.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.petitesroues.sync.PhoneRoutes;
import org.junit.jupiter.api.Test;

class PhoneAdvertiserTest {
  @Test
  void announcesTheListenersPortAndPinUnderTheSharedServiceType() {
    String pin = "sha256:" + "ab".repeat(32);
    var service = PhoneAdvertiser.service("study-desktop", 8878, pin);

    assertEquals(PhoneRoutes.SERVICE_TYPE + ".local.", service.getType());
    assertEquals("study-desktop", service.getName());
    assertEquals(8878, service.getPort());
    assertEquals(pin, service.getPropertyString(PhoneRoutes.SERVICE_PIN));
  }
}
