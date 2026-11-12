package app.petitesroues.config;

import app.petitesroues.library.LibraryJobService;
import app.petitesroues.library.LibraryUpdateService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares job execution resources; Spring wires them and destroys dependants before executors. */
@Configuration(proxyBeanMethods = false)
public class LibraryJobConfiguration {
  /** Job admission is serialized by the service, independently of this executor's thread model. */
  @Bean(destroyMethod = "shutdownNow")
  public ExecutorService libraryJobExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  /** Dedicated delivery tasks keep blocked followers from holding up indexing. */
  @Bean(destroyMethod = "shutdownNow")
  public ExecutorService libraryNotificationExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  /**
   * Spring stops job admission and gives work a bounded grace period before interrupting either
   * executor. Notification delivery may be interrupted during application shutdown.
   */
  @Bean(destroyMethod = "stop")
  public LibraryJobService libraryJobService(
      LibraryUpdateService updates,
      @Qualifier("libraryJobExecutor") ExecutorService worker,
      @Qualifier("libraryNotificationExecutor") ExecutorService notifications) {
    return new LibraryJobService(updates, worker, notifications);
  }
}
