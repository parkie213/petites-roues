package app.petitesroues.config;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.library.LibraryJobService;
import app.petitesroues.library.LibraryUpdateService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

class LibraryJobConfigurationTest {
  @Test
  void springWiresNamedExecutorsAndStopsTheServiceBeforeDestroyingThem() throws Exception {
    var release = new CountDownLatch(1);
    var entered = new CountDownLatch(1);
    var completedBeforeExecutorShutdown = new AtomicBoolean();
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(LibraryUpdateService.class, () -> new LibraryUpdateService(null));
      context.register(LibraryJobConfiguration.class);
      context.addApplicationListener(
          event -> {
            if (event instanceof ContextClosedEvent) release.countDown();
          });
      context.refresh();
      var worker = context.getBean("libraryJobExecutor", ExecutorService.class);
      var notifications = context.getBean("libraryNotificationExecutor", ExecutorService.class);
      var jobs = context.getBean(LibraryJobService.class);
      assertNotSame(worker, notifications);
      jobs.start(
          "refresh",
          progress -> {
            entered.countDown();
            release.await();
            completedBeforeExecutorShutdown.set(
                !worker.isShutdown() && !notifications.isShutdown());
          });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      context.close();
      assertTrue(completedBeforeExecutorShutdown.get());
      assertEquals("succeeded", jobs.latest().orElseThrow().state());
      assertThrows(IllegalStateException.class, () -> jobs.start("refresh", progress -> {}));
      assertTrue(worker.isShutdown());
      assertTrue(notifications.isShutdown());
      assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
      assertTrue(notifications.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      release.countDown();
    }
  }
}
