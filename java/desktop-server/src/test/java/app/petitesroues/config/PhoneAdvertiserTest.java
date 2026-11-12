package app.petitesroues.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.petitesroues.sync.PhoneRoutes;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
  void springWiresTheNamedWorkerAndClosesItAfterTheAdvertiser() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(PhoneAdvertisementConfiguration.class, PhoneAdvertiser.class);
      context.refresh();
      var executor = context.getBean("phoneAdvertisementExecutor", ThreadPoolTaskExecutor.class);
      var advertiser = context.getBean(PhoneAdvertiser.class);
      advertiser.start(List.of(), "desktop", 8878, "pin");
      context.close();
      assertTrue(executor.getThreadPoolExecutor().isTerminated());
      assertThrows(RejectedExecutionException.class, advertiser::stop);
      advertiser.close();
    }
  }

  @Test
  void queuedStartStopAndFinalWithdrawalDrainInOrder() throws Exception {
    var first = mock(JmDNS.class);
    var second = mock(JmDNS.class);
    var release = new CountDownLatch(1);
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(PhoneAdvertisementConfiguration.class);
      context.registerBean(
          "phoneAdvertiser",
          PhoneAdvertiser.class,
          () ->
              new PhoneAdvertiser(
                  context.getBean("phoneAdvertisementExecutor", ThreadPoolTaskExecutor.class),
                  (address, name) -> name.equals("first") ? first : second),
          definition -> definition.setDependsOn("phoneAdvertisementExecutor"));
      context.addApplicationListener(
          event -> {
            if (event instanceof ContextClosedEvent) release.countDown();
          });
      context.refresh();
      var executor = context.getBean("phoneAdvertisementExecutor", ThreadPoolTaskExecutor.class);
      var entered = new CountDownLatch(1);
      executor.execute(
          () -> {
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      var advertiser = context.getBean(PhoneAdvertiser.class);
      var addresses = List.of(InetAddress.getLoopbackAddress());
      advertiser.start(addresses, "first", 8878, "pin");
      advertiser.stop();
      advertiser.start(addresses, "second", 8878, "pin");
      context.close();
      assertTrue(executor.getThreadPoolExecutor().isTerminated());
      var order = inOrder(first, second);
      order.verify(first).registerService(any(ServiceInfo.class));
      order.verify(first).unregisterAllServices();
      order.verify(first).close();
      order.verify(second).registerService(any(ServiceInfo.class));
      order.verify(second).unregisterAllServices();
      order.verify(second).close();
      order.verifyNoMoreInteractions();
      assertThrows(
          RejectedExecutionException.class, () -> advertiser.start(addresses, "late", 8878, "pin"));
    } finally {
      release.countDown();
    }
  }

  @Test
  void withdrawalClosesRespondersEvenWhenUnregisterFails() throws Exception {
    var responder = mock(JmDNS.class);
    doThrow(new IllegalStateException("unregister failed")).when(responder).unregisterAllServices();
    try (var advertiser = new PhoneAdvertiser(Runnable::run, (address, name) -> responder)) {
      advertiser.start(List.of(InetAddress.getLoopbackAddress()), "desktop", 8878, "pin");
    }
    verify(responder).close();
  }
}
