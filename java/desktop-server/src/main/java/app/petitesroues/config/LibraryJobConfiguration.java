package app.petitesroues.config;

import app.petitesroues.library.LibraryJobService;
import app.petitesroues.library.LibraryUpdateService;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates job execution resources and stops jobs before closing work and notification executors.
 */
@Configuration(proxyBeanMethods = false)
public class LibraryJobConfiguration implements DisposableBean {
  private static final System.Logger LOG =
      System.getLogger(LibraryJobConfiguration.class.getName());
  private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
  private final ExecutorService notifications = Executors.newVirtualThreadPerTaskExecutor();
  private final LibraryJobService jobs;

  public LibraryJobConfiguration(LibraryUpdateService updates) {
    jobs = new LibraryJobService(updates, worker, notifications);
  }

  /** The application service borrows this configuration's execution resources. */
  @Bean
  public LibraryJobService libraryJobService() {
    return jobs;
  }

  /**
   * Stops admission and work first, then allows terminal notifications to drain before
   * interruption.
   */
  @Override
  public void destroy() {
    jobs.stop(Duration.ofSeconds(5));
    shutdown(worker);
    shutdown(notifications);
  }

  private static void shutdown(ExecutorService executor) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS))
          LOG.log(
              System.Logger.Level.WARNING, "Library job executor did not stop after interruption");
      }
    } catch (InterruptedException interrupted) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
