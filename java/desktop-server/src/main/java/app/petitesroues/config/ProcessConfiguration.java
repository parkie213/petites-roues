package app.petitesroues.config;

import app.petitesroues.media.ProcessRunner;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Owns the executor used to drain local tools' output until application shutdown. */
@Configuration(proxyBeanMethods = false)
public class ProcessConfiguration {
  @Bean(destroyMethod = "close")
  public ExecutorService processExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  /** Metadata probes and cover resizing must finish within thirty seconds. */
  @Bean
  public ProcessRunner processRunner(@Qualifier("processExecutor") ExecutorService executor) {
    return new ProcessRunner(executor, Duration.ofSeconds(30));
  }
}
