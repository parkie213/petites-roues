package app.petitesroues.config;

import app.petitesroues.library.LibraryJobService;
import app.petitesroues.media.ProcessRunner;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** Owns application executors and wires the operations that use them. */
@Configuration(proxyBeanMethods = false)
public class ExecutorConfiguration {
  /** Drains subprocess output independently of the jobs waiting for those processes. */
  @Bean(destroyMethod = "close")
  public ExecutorService processExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  /** Metadata probes and cover resizing must finish within thirty seconds. */
  @Bean
  public ProcessRunner processRunner(@Qualifier("processExecutor") ExecutorService executor) {
    return new ProcessRunner(executor, Duration.ofSeconds(30));
  }

  /**
   * Waits up to five seconds for virtual-thread tasks, then interrupts remaining work. Depending on
   * the application services keeps their database dependencies alive during that grace period.
   */
  @Bean
  @DependsOn("contentRepositoryService")
  public SimpleAsyncTaskExecutor libraryJobExecutor() {
    var executor = new SimpleAsyncTaskExecutor("library-job-");
    executor.setVirtualThreads(true);
    executor.setTaskTerminationTimeout(5_000);
    return executor;
  }

  @Bean
  public LibraryJobService libraryJobService(
      @Qualifier("libraryJobExecutor") SimpleAsyncTaskExecutor executor) {
    return new LibraryJobService(executor);
  }
}
