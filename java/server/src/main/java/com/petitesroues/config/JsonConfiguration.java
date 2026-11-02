package com.petitesroues.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;

/** Apply the same JSON parsing rules to HTTP requests and imported documents. */
@Configuration
public class JsonConfiguration {
  /**
   * Makes HTTP requests and imported documents reject duplicate object keys instead of silently
   * accepting ambiguous input.
   *
   * <p>A customizer preserves Boot's mapper configuration rather than replacing the mapper.
   * Duplicate detection is a {@link StreamReadFeature}, which Spring Boot 4.0's Jackson properties
   * do not expose. Jackson 3 already rejects trailing JSON values by default.
   */
  @Bean
  public JsonMapperBuilderCustomizer strictJson() {
    return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
  }
}
