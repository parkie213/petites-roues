package app.petitesroues.config;

import app.petitesroues.documents.StudyDocumentReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the framework-independent document reader to application services. */
@Configuration(proxyBeanMethods = false)
public class DocumentConfiguration {
  @Bean
  public StudyDocumentReader studyDocumentReader() {
    return new StudyDocumentReader();
  }
}
