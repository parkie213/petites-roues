package app.petitesroues.config;

import app.petitesroues.library.LibraryJobService;
import app.petitesroues.library.LibraryUpdateService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring closes the service before interrupting its borrowed virtual-thread executor. */
@Configuration(proxyBeanMethods = false)
public class LibraryJobConfiguration {
  @Bean(destroyMethod = "shutdownNow")
  public ExecutorService libraryJobExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  public LibraryJobService libraryJobService(
      LibraryUpdateService updates, @Qualifier("libraryJobExecutor") ExecutorService executor) {
    return new LibraryJobService(updates, executor);
  }
}
