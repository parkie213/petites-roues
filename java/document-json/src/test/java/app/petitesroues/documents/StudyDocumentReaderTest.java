package app.petitesroues.documents;

import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.document.ComicDocument;
import app.petitesroues.model.document.StudyDocument;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class StudyDocumentReaderTest {
  private final JsonMapper mapper = StudyDocumentJson.newMapper();
  private final StudyDocumentCodec codec = new StudyDocumentCodec();
  private final StudyDocumentReader reader = new StudyDocumentReader(codec);

  private StudyDocument roundTrip(StudyDocument document) {
    return reader.decode(codec.encode(document).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void exampleDocumentsLoadAndRoundTripWithoutLocalMedia() throws Exception {
    for (String type : new String[] {"video", "comic"}) {
      try (var input = getClass().getResourceAsStream("/documents/" + type + ".json")) {
        var loaded = reader.decode(input.readAllBytes());
        assertEquals(loaded, roundTrip(loaded));
        assertEquals(type.equals("comic"), loaded.body() instanceof ComicDocument);
      }
    }
  }

  @Test
  void writesOneStableCanonicalLayout() {
    var json = codec.encode(document("example"));
    assertEquals(json, codec.encode(codec.decode(json.getBytes(StandardCharsets.UTF_8))));
    assertTrue(json.startsWith("{\n  \"id\": \"example\",\n  \"title\": "));
    assertTrue(json.contains("\"notes\": [\n            \"A greeting.\"\n          ]"));
    assertTrue(json.endsWith("}\n"));
    assertFalse(json.contains("\"series\""), "absent optional fields are omitted");
    assertTrue(json.contains("\"continuesPrevious\": false"), "booleans are always written");
  }

  @Test
  void anOptionalShelfIsTrimmedWrittenAfterTheSeriesAndBlankMeansNone() {
    var base = document("example");
    var shelved =
        new StudyDocument(
            base.id(),
            base.title(),
            base.sourceLanguage(),
            base.translationLanguage(),
            base.notesLanguage(),
            "Demon Slayer",
            " Manga ",
            null,
            base.body());
    assertEquals("Manga", shelved.shelf());
    assertEquals(shelved, roundTrip(shelved));
    String json = codec.encode(shelved);
    assertTrue(json.indexOf("\"series\"") < json.indexOf("\"shelf\""));
    assertEquals("Manga", shelved.withTitle("Tome 1").shelf(), "edits keep the shelf");
    assertNull(
        new StudyDocument(base.id(), base.title(), "fr", "en", "en", null, "  ", null, base.body())
            .shelf());
  }

  @Test
  void preservesOptionalTranslationPresenceAndExplicitZeroStart() {
    for (String translation : new String[] {null, "", "Hello!"}) {
      var doc =
          document("example")
              .withVideo(
                  v ->
                      v.withCue(
                          0,
                          c ->
                              c.withTiming(0.0, .9).withLine(s -> s.withTranslation(translation))));
      var loaded = roundTrip(doc);
      assertEquals(translation, loaded.video().cues().getFirst().line().translation());
      assertEquals(0, loaded.video().cues().getFirst().start());
    }
  }

  @Test
  void rejectsMissingTimingLineBodyUnknownFieldsAndLegacyDocuments() {
    var json = mapper.readTree(codec.encode(document("example")));
    for (String field : new String[] {"start", "end", "line"}) {
      var changed = json.deepCopy();
      ((ObjectNode) changed.path("video").path("cues").get(0)).remove(field);
      assertThrows(
          IllegalArgumentException.class, () -> reader.decode(mapper.writeValueAsBytes(changed)));
    }
    assertThrows(IllegalArgumentException.class, () -> reader.decode("{}".getBytes()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            reader.decode(
                "{\"format\":\"sacre-bleu-study\",\"version\":3}"
                    .getBytes(StandardCharsets.UTF_8)));
    var changed = json.deepCopy();
    ((ObjectNode) changed).put("unknown", true);
    assertThrows(
        IllegalArgumentException.class, () -> reader.decode(mapper.writeValueAsBytes(changed)));
    assertThrows(
        IllegalArgumentException.class,
        () -> reader.decode("{\"id\":\"a\",\"id\":\"b\"}".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void multilingualAlignmentsAndChecksSurviveRoundTrip() {
    for (var pair :
        List.of(
            List.of("de", "en", "Ich stehe auf.", "I get up."),
            List.of("ko", "fr", "저는 사과를 먹어요.", "Je mange une pomme."))) {
      var alignment =
          newAlignment(
              List.of(pair.get(2)),
              List.of(pair.get(3)),
              List.of(newLink(List.of(0), List.of(0), "Meaning")));
      var doc =
          document("an edition: 1")
              .withLanguages(pair.get(0), pair.get(1), pair.get(1))
              .withVideo(
                  v ->
                      v.withCue(
                          0,
                          c ->
                              c.withListeningCheck("Keep unresolved evidence")
                                  .withLine(
                                      s ->
                                          s.withSource(pair.get(2))
                                              .withTranslation(pair.get(3))
                                              .withAlignment(alignment))));
      var json = codec.encode(doc);
      assertEquals(doc, reader.decode(json.getBytes(StandardCharsets.UTF_8)));
      assertFalse(mapper.readTree(json).has("format"));
      var reworded = doc.withVideo(v -> v.withCue(0, c -> c.withLine(s -> s.withSource("Autre"))));
      assertThrows(IllegalArgumentException.class, () -> codec.encode(reworded));
    }
  }

  @Test
  void sortsVideoCuesWithoutDetachingAnnotations() {
    var original = document("example").video().cues().getFirst();
    var early = original.withLine(s -> s.withId("early"));
    var doc =
        document("example")
            .withVideo(v -> v.withCues(List.of(original.withTiming(4.0, 5.0), early)));
    var bytes = codec.encode(doc).getBytes(StandardCharsets.UTF_8);
    assertEquals(doc, codec.decode(bytes), "the codec preserves document order");
    assertEquals(doc, codec.inspect(bytes).document().orElseThrow());
    var loaded = reader.decode(bytes);
    assertEquals(loaded, reader.inspect(bytes).document().orElseThrow());
    assertEquals("early", loaded.video().cues().getFirst().line().id());
    assertEquals(original.line().notes(), loaded.video().cues().getFirst().line().notes());
  }

  @Test
  void anyJsonClaimingADocumentBodyIsAStudyDocumentWhateverItsName(@TempDir Path folder)
      throws Exception {
    var reader = new StudyDocumentReader(new StudyDocumentCodec());
    Path lunch =
        Files.writeString(
            folder.resolve("lunch.json"), "{\"sourceLanguage\": \"zh\", \"video\": {}}");
    Path comic =
        Files.writeString(
            folder.resolve("tome.json"), "{\"sourceLanguage\": \"fr\", \"comic\": 1}");
    assertTrue(reader.isStudyDocument(lunch), "invalid documents still claim to be documents");
    assertTrue(reader.isStudyDocument(comic));
    Path broken =
        Files.writeString(
            folder.resolve("broken.json"), "{\"sourceLanguage\": \"fr\", \"video\": {,");
    assertTrue(reader.isStudyDocument(broken), "a document broken mid-edit must still be read");
    for (String other :
        List.of(
            "{\"name\": \"package\", \"version\": \"1.0.0\"}",
            "{\"sourceLanguage\": \"fr\"}",
            "[{\"video\": {}}]",
            "not JSON at all",
            ""))
      assertFalse(reader.isStudyDocument(Files.writeString(folder.resolve("other.json"), other)));
  }
}
