package app.petitesroues.config;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.library.LibraryJobService;
import app.petitesroues.library.LibraryUpdateService;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class LibraryJobConfigurationTest {
  /** Real SQLite resource behind the named workflow dependencies, without starting HTTP. */
  private AnnotationConfigApplicationContext context() {
    var context = new AnnotationConfigApplicationContext();
    context.registerBean(
        "dataSource",
        SingleConnectionDataSource.class,
        () -> new SingleConnectionDataSource("jdbc:sqlite::memory:", true),
        definition -> definition.setDestroyMethodName("destroy"));
    context.registerBean(
        "contentRepositoryService",
        Object.class,
        Object::new,
        definition -> definition.setDependsOn("dataSource"));
    context.registerBean(
        "libraryUpdateService",
        LibraryUpdateService.class,
        () -> new LibraryUpdateService(null),
        definition -> definition.setDependsOn("dataSource"));
    context.register(LibraryJobConfiguration.class);
    context.refresh();
    return context;
  }

  @Test
  void springAllowsWorkToFinishBeforeDestroyingItsDatabase() throws Exception {
    try (var context = context()) {
      var source = context.getBean(SingleConnectionDataSource.class);
      var connection = source.getConnection();
      var executor = context.getBean("libraryJobExecutor", SimpleAsyncTaskExecutor.class);
      var jobs = context.getBean(LibraryJobService.class);
      var entered = new CountDownLatch(1);
      var result = new AtomicInteger();
      var virtual = new AtomicBoolean();
      jobs.start(
          "refresh",
          progress -> {
            virtual.set(Thread.currentThread().isVirtual());
            entered.countDown();
            // The SQL runs during executor.close(), after shutdown admission has stopped.
            await().atMost(Duration.ofSeconds(3)).until(() -> !executor.isActive());
            try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT 1")) {
              rows.next();
              result.set(rows.getInt(1));
            }
          });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      context.close();
      assertTrue(virtual.get());
      assertEquals(1, result.get());
      assertEquals("succeeded", jobs.latest().orElseThrow().state());
      assertTrue(connection.isClosed());
      assertThrows(RejectedExecutionException.class, () -> jobs.start("refresh", progress -> {}));
    }
  }

  @Test
  void springInterruptsWorkAfterItsGracePeriodAndRejectsNewSubmissions() throws Exception {
    var interrupted = new CountDownLatch(1);
    try (var context = context()) {
      var executor = context.getBean("libraryJobExecutor", SimpleAsyncTaskExecutor.class);
      executor.setTaskTerminationTimeout(50);
      var jobs = context.getBean(LibraryJobService.class);
      var entered = new CountDownLatch(1);
      jobs.start(
          "refresh",
          progress -> {
            entered.countDown();
            try {
              new CountDownLatch(1).await();
            } catch (InterruptedException failure) {
              interrupted.countDown();
              throw failure;
            }
          });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      context.close();
      assertFalse(executor.isActive());
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      await().atMost(Duration.ofSeconds(5)).until(() -> jobs.latest().orElseThrow().finished());
      assertEquals("failed", jobs.latest().orElseThrow().state());
      assertEquals("Server is shutting down", jobs.latest().orElseThrow().error());
      assertThrows(RejectedExecutionException.class, () -> jobs.start("refresh", progress -> {}));
    }
  }
}
