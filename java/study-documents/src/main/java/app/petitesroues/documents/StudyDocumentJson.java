package app.petitesroues.documents;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Shared document JSON configuration, independent of HTTP settings and DI frameworks. */
public final class StudyDocumentJson {
  private StudyDocumentJson() {}

  /**
   * Creates a dedicated document mapper. Rejects unknown fields, trailing values and duplicate
   * keys, while allowing null primitives to take the document format's defaults. Desktop and
   * Android composition roots use this configuration rather than a general HTTP mapper.
   */
  public static JsonMapper newMapper() {
    return JsonMapper.builder()
        .enable(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build();
  }
}
