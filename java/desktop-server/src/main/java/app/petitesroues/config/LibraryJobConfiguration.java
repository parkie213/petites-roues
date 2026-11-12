package app.petitesroues.config;

import app.petitesroues.library.LibraryJobService;
import app.petitesroues.library.LibraryUpdateService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** Spring owns job execution, its grace period and interruption of work that outlives it. */
@Configuration(proxyBeanMethods = false)
public class LibraryJobConfiguration {
  /**
   * Waits up to five seconds for virtual-thread tasks, then interrupts remaining work. Depending on
   * the application services keeps their database dependencies alive during that grace period.
   */
  @Bean
  @DependsOn({"contentRepositoryService", "libraryUpdateService"})
  public SimpleAsyncTaskExecutor libraryJobExecutor() {
    var executor = new SimpleAsyncTaskExecutor("library-job-");
    executor.setVirtualThreads(true);
    executor.setTaskTerminationTimeout(5_000);
    return executor;
  }

  @Bean
  public LibraryJobService libraryJobService(
      LibraryUpdateService updates,
      @Qualifier("libraryJobExecutor") SimpleAsyncTaskExecutor executor) {
    return new LibraryJobService(updates, executor);
  }
}
