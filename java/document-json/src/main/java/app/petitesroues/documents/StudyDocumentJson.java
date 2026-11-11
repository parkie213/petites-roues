package app.petitesroues.documents;

import app.petitesroues.model.document.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/** JSON adapter configuration and wire mapping; the document model has no Jackson dependency. */
public final class StudyDocumentJson {
  private StudyDocumentJson() {}

  /** Creates the strict document-file mapper, including the model-to-wire mapping. */
  public static JsonMapper newMapper() {
    return JsonMapper.builder()
        .addModule(module())
        .enable(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build();
  }

  /**
   * Supplies the same wire mapping to document files, HTTP and persisted review commands without
   * changing their surrounding mapper policies. All format-specific annotations live here.
   */
  public static SimpleModule module() {
    var module = new SimpleModule("study-document-json");
    for (var type :
        List.of(
            VideoDocument.class,
            VideoCue.class,
            VideoReference.class,
            ComicReference.class,
            ComicChapter.class,
            Line.class)) module.setMixInAnnotation(type, NonNull.class);
    module.setMixInAnnotation(ComicDocument.class, ComicMixin.class);
    module.setMixInAnnotation(ComicPage.class, PageMixin.class);
    module.setMixInAnnotation(PhraseLink.class, LinkMixin.class);
    module.addSerializer(StudyDocument.class, new DocumentSerializer());
    module.addDeserializer(StudyDocument.class, new DocumentDeserializer());
    module.addSerializer(ReadingDirection.class, new DirectionSerializer());
    module.addDeserializer(ReadingDirection.class, new DirectionDeserializer());
    return module;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private abstract static class NonNull {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private abstract static class ComicMixin {
    @JsonIgnore
    public abstract List<ComicBox> boxes();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private abstract static class PageMixin {
    @JsonCreator
    public PageMixin(
        @JsonProperty("id") String id,
        @JsonProperty("entry") String entry,
        @JsonProperty("label") String label,
        @JsonProperty("boxes") List<ComicBox> boxes) {}
  }

  private abstract static class LinkMixin {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public abstract String note();
  }

  /** The wire root uses named bodies; the model instead guarantees exactly one body. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record WireDocument(
      String id,
      String title,
      String sourceLanguage,
      String translationLanguage,
      String notesLanguage,
      String series,
      String shelf,
      String sourceUrl,
      VideoDocument video,
      ComicDocument comic) {
    StudyDocument toModel() {
      if (video != null && comic != null)
        throw new IllegalArgumentException("Exactly one video or comic body is required");
      return new StudyDocument(
          id,
          title,
          sourceLanguage,
          translationLanguage,
          notesLanguage,
          series,
          shelf,
          sourceUrl,
          video != null ? video : comic);
    }

    static WireDocument fromModel(StudyDocument document) {
      return new WireDocument(
          document.id(),
          document.title(),
          document.sourceLanguage(),
          document.translationLanguage(),
          document.notesLanguage(),
          document.series(),
          document.shelf(),
          document.sourceUrl(),
          document.body() instanceof VideoDocument video ? video : null,
          document.body() instanceof ComicDocument comic ? comic : null);
    }
  }

  private static final class DocumentSerializer extends ValueSerializer<StudyDocument> {
    @Override
    public void serialize(
        StudyDocument value, JsonGenerator generator, SerializationContext context) {
      context.writeValue(generator, WireDocument.fromModel(value));
    }
  }

  private static final class DocumentDeserializer extends ValueDeserializer<StudyDocument> {
    @Override
    public StudyDocument deserialize(JsonParser parser, DeserializationContext context) {
      var wire = context.readValue(parser, WireDocument.class);
      try {
        return wire.toModel();
      } catch (IllegalArgumentException failure) {
        throw DatabindException.from(parser, failure.getMessage(), failure);
      }
    }
  }

  private static final class DirectionSerializer extends ValueSerializer<ReadingDirection> {
    @Override
    public void serialize(
        ReadingDirection value, JsonGenerator generator, SerializationContext context) {
      generator.writeString(value == ReadingDirection.LTR ? "ltr" : "rtl");
    }
  }

  private static final class DirectionDeserializer extends ValueDeserializer<ReadingDirection> {
    @Override
    public ReadingDirection deserialize(JsonParser parser, DeserializationContext context) {
      return switch (parser.getString()) {
        case "ltr" -> ReadingDirection.LTR;
        case "rtl" -> ReadingDirection.RTL;
        default -> throw DatabindException.from(parser, "Reading direction must be ltr or rtl");
      };
    }
  }
}
