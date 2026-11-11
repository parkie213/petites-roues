package app.petitesroues.documents;

import app.petitesroues.model.document.StudyDocument;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads and writes study documents in their one canonical JSON form: two-space indentation, one
 * array element per line, fields in record order and absent optional fields omitted. Reading
 * rejects unknown fields and duplicate keys; absent or {@code null} values take the format's
 * defaults. Array order is preserved; playback ordering belongs to {@link StudyDocumentReader}. A
 * file that decodes and re-encodes changes only in layout and explicit defaults.
 */
public final class StudyDocumentCodec {
  private final JsonMapper mapper;
  private final ObjectReader recognition;
  private final ObjectWriter writer;

  /**
   * Uses a dedicated mapper configured by {@link StudyDocumentJson#newMapper}. Derives recognition
   * and canonical writing from that same mapper; callers cannot supply an unrelated writer.
   */
  public StudyDocumentCodec(JsonMapper mapper) {
    this.mapper = mapper;
    // Recognition checks shape, not validity: duplicate keys must not hide a claimed document.
    this.recognition = mapper.reader().without(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    this.writer =
        mapper
            .writer()
            .with(
                new DefaultPrettyPrinter(
                        Separators.createDefaultInstance()
                            .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                            .withObjectEmptySeparator("")
                            .withArrayEmptySeparator(""))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n"))
                    .withObjectIndenter(new DefaultIndenter("  ", "\n")));
  }

  /**
   * Whether JSON claims a source language and a video or comic body, regardless of validity.
   * Malformed JSON mentioning sourceLanguage is also recognised, so discovery reports broken
   * documents rather than silently skipping them. This does not validate or load the document.
   */
  public boolean isStudyDocument(byte[] bytes) {
    JsonNode root;
    try {
      root = recognition.readTree(bytes);
    } catch (JacksonException notJson) {
      return new String(bytes, StandardCharsets.UTF_8).contains("\"sourceLanguage\"");
    }
    return root != null
        && root.isObject()
        && root.has("sourceLanguage")
        && (root.has("video") || root.has("comic"));
  }

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
    return inspect(bytes).problems();
  }

  /**
   * A single parse and validation result. Exactly one of a valid document or a nonempty list of
   * problems is present. The document retains input order, and the problem list is immutable.
   */
  public record Inspection(Optional<StudyDocument> document, List<String> problems) {
    public Inspection {
      problems = List.copyOf(problems);
      if (document.isPresent() != problems.isEmpty())
        throw new IllegalArgumentException("Inspection requires a valid document or problems");
    }
  }

  /** Parses once and reports all structural problems, retaining the document only when valid. */
  public Inspection inspect(byte[] bytes) {
    StudyDocument document;
    try {
      document = parse(bytes);
    } catch (IllegalArgumentException failure) {
      return new Inspection(Optional.empty(), List.of(failure.getMessage()));
    }
    var problems = StudyDocumentValidator.problems(document);
    return new Inspection(problems.isEmpty() ? Optional.of(document) : Optional.empty(), problems);
  }

  /** Validates, then writes the canonical form with a trailing newline. */
  public String encode(StudyDocument document) {
    StudyDocumentValidator.validate(document);
    return writer.writeValueAsString(document) + "\n";
  }

  private StudyDocument parse(byte[] bytes) {
    try {
      return mapper.readValue(bytes, StudyDocument.class);
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
