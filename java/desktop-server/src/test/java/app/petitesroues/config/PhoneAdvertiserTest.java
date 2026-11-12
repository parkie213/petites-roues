package app.petitesroues.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.petitesroues.sync.PhoneRoutes;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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

  @Test
  void replacementClosesTheOldResponderAndSpringClosesTheLastOne() throws Exception {
    var first = mock(JmDNS.class);
    var second = mock(JmDNS.class);
    var address = InetAddress.getLoopbackAddress();
    try (var dns = mockStatic(JmDNS.class);
        var context = new AnnotationConfigApplicationContext(PhoneAdvertiser.class)) {
      dns.when(() -> JmDNS.create(address, "first")).thenReturn(first);
      dns.when(() -> JmDNS.create(address, "second")).thenReturn(second);
      var advertiser = context.getBean(PhoneAdvertiser.class);
      advertiser.start(List.of(address), "first", 8878, "pin");
      advertiser.start(List.of(address), "second", 8878, "pin");
      context.close();
      advertiser.close();
      var order = inOrder(first, second);
      order.verify(first).registerService(any(ServiceInfo.class));
      order.verify(first).close();
      order.verify(second).registerService(any(ServiceInfo.class));
      order.verify(second).close();
      order.verifyNoMoreInteractions();
    }
  }

  @Test
  void aFailedAnnouncementIsReleasedAndDoesNotPreventOtherAddresses() throws Exception {
    var failed = mock(JmDNS.class);
    var working = mock(JmDNS.class);
    var firstAddress = InetAddress.getByName("127.0.0.1");
    var secondAddress = InetAddress.getByName("127.0.0.2");
    doThrow(new IOException("registration failed"))
        .when(failed)
        .registerService(any(ServiceInfo.class));
    doThrow(new IOException("close failed")).when(failed).close();
    try (var dns = mockStatic(JmDNS.class)) {
      dns.when(() -> JmDNS.create(firstAddress, "desktop")).thenReturn(failed);
      dns.when(() -> JmDNS.create(secondAddress, "desktop")).thenReturn(working);
      try (var advertiser = new PhoneAdvertiser()) {
        advertiser.start(List.of(firstAddress, secondAddress), "desktop", 8878, "pin");
        verify(working).registerService(any(ServiceInfo.class));
      }
      verify(failed).close();
      verify(working).close();
    }
  }

  @Test
  void disablingThenReenablingCreatesANewResponder() throws Exception {
    var first = mock(JmDNS.class);
    var second = mock(JmDNS.class);
    var address = InetAddress.getLoopbackAddress();
    try (var dns = mockStatic(JmDNS.class);
        var advertiser = new PhoneAdvertiser()) {
      dns.when(() -> JmDNS.create(address, "desktop")).thenReturn(first, second);
      advertiser.start(List.of(address), "desktop", 8878, "pin");
      advertiser.close();
      advertiser.close();
      advertiser.start(List.of(address), "desktop", 8878, "pin");
      verify(first).close();
      verify(second).registerService(any(ServiceInfo.class));
    }
    verify(second).close();
  }
}
