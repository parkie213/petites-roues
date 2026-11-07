package com.petitesroues.documents;

import com.petitesroues.model.document.StudyDocument;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes study documents in their one canonical JSON form: two-space indentation, one
 * array element per line, fields in record order and absent optional fields omitted. Reading
 * rejects unknown fields and duplicate keys; absent or {@code null} values take the format's
 * defaults, so a file that decodes and re-encodes changes only in layout and explicit defaults.
 */
public final class StudyDocumentCodec {
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .enable(
              DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .build();
  private static final ObjectWriter WRITER =
      MAPPER
          .writer()
          .with(
              new DefaultPrettyPrinter(
                      Separators.createDefaultInstance()
                          .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                          .withObjectEmptySeparator("")
                          .withArrayEmptySeparator(""))
                  .withArrayIndenter(new DefaultIndenter("  ", "\n"))
                  .withObjectIndenter(new DefaultIndenter("  ", "\n")));

  /** Parses and validates a document; any problem is an {@link IllegalArgumentException}. */
  public StudyDocument decode(byte[] bytes) {
    var document = parse(bytes);
    StudyDocumentValidator.validate(document);
    return document;
  }

  /**
   * Every reason {@code bytes} would not decode, without throwing: a single parse problem, or each
   * structural problem found by {@link StudyDocumentValidator#problems}. Empty means valid.
   */
  public List<String> problems(byte[] bytes) {
    try {
      return StudyDocumentValidator.problems(parse(bytes));
    } catch (IllegalArgumentException failure) {
      return List.of(failure.getMessage());
    }
  }

  /** Validates, then writes the canonical form with a trailing newline. */
  public String encode(StudyDocument document) {
    StudyDocumentValidator.validate(document);
    return WRITER.writeValueAsString(document) + "\n";
  }

  private StudyDocument parse(byte[] bytes) {
    try {
      return MAPPER.readValue(bytes, StudyDocument.class);
    } catch (JacksonException failure) {
      // A record that rejects its values (such as a missing body) says why in its own words.
      String reason =
          failure.getCause() instanceof IllegalArgumentException rejected
              ? rejected.getMessage()
              : failure.getOriginalMessage();
      throw new IllegalArgumentException("Invalid study document: " + reason, failure);
    }
  }
}
