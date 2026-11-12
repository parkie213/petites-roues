package app.petitesroues.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Owns the ordered worker used to create and withdraw phone announcements. */
@Configuration(proxyBeanMethods = false)
public class PhoneAdvertisementConfiguration {
  /**
   * A single worker preserves Start/Stop order without blocking HTTP callers. Spring drains queued
   * work, including the advertiser's final withdrawal, and waits up to five seconds at shutdown. A
   * daemon worker does not keep process exit waiting beyond that grace period.
   */
  @Bean
  public ThreadPoolTaskExecutor phoneAdvertisementExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("phone-advertiser-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setDaemon(true);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(5);
    return executor;
  }
}
