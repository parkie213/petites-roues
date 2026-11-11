package app.petitesroues.config;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.documents.StudyDocumentReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assembles the shared, framework-independent document format components for desktop services. */
@Configuration(proxyBeanMethods = false)
public class DocumentConfiguration {
  @Bean
  public StudyDocumentCodec studyDocumentCodec() {
    return new StudyDocumentCodec(StudyDocumentJson.newMapper());
  }

  @Bean
  public StudyDocumentReader studyDocumentReader(StudyDocumentCodec codec) {
    return new StudyDocumentReader(codec);
  }
}
