package app.petitesroues.documents;

import static app.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.document.VideoDocument;
import app.petitesroues.model.document.VideoReference;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudyDocumentValidatorTest {
  private static final VideoCue CUE = new VideoCue(Line.of("cue-a", "Bonjour"), 0.0, 1.0, null);

  private static StudyDocument document(VideoDocument video) {
    return new StudyDocument("example", "Example", "fr", "en", "en", null, null, video);
  }

  private static StudyDocument document(VideoCue... cues) {
    return document(new VideoDocument(null, null, null, List.of(cues)));
  }

  private static void invalid(StudyDocument document) {
    assertThrows(IllegalArgumentException.class, () -> StudyDocumentValidator.validate(document));
  }

  @Test
  void aDocumentHasExactlyOneBodyUnderItsKind() {
    var codec = new StudyDocumentCodec();
    String metadata =
        "\"id\": \"x\", \"title\": \"X\", \"sourceLanguage\": \"fr\","
            + " \"translationLanguage\": \"en\", \"notesLanguage\": \"en\"";
    String video = "\"video\": {\"cues\": []}";
    String comic = "\"comic\": {\"pages\": [{\"id\": \"p\", \"entry\": \"001.jpg\"}]}";
    for (String body : new String[] {"", ", " + video + ", " + comic})
      assertEquals(
          List.of("Invalid study document: Exactly one video or comic body is required"),
          codec.problems(("{" + metadata + body + "}").getBytes()));
    for (String body : new String[] {video, comic}) {
      String encoded = codec.encode(codec.decode(("{" + metadata + ", " + body + "}").getBytes()));
      assertTrue(encoded.contains("\"" + body.substring(1, 6) + "\": {"), encoded);
      assertFalse(encoded.contains("\"body\""), encoded);
    }
    assertThrows(IllegalArgumentException.class, () -> document((VideoDocument) null));
  }

  @Test
  void aReferenceHasAV1FingerprintAPositiveSizeAndAPositiveDuration() {
    String hash = "v1:" + "0123456789abcdef".repeat(4);
    for (var reference :
        new VideoReference[] {
          null,
          new VideoReference(null, null, null),
          new VideoReference(hash, 734003200L, 1312.48),
          new VideoReference(null, null, 62.8)
        }) assertDoesNotThrow(() -> StudyDocumentValidator.validate(withReference(reference)));
    for (var reference :
        new VideoReference[] {
          new VideoReference("v1:" + "0123456789ABCDEF".repeat(4), null, null),
          new VideoReference("v2:" + "0123456789abcdef".repeat(4), null, null),
          new VideoReference(hash.substring(1), null, null),
          new VideoReference(hash + "0", null, null),
          new VideoReference(null, 0L, null),
          new VideoReference(null, null, 0.0),
          new VideoReference(null, null, Double.NaN)
        }) invalid(withReference(reference));
  }

  private static StudyDocument withReference(VideoReference reference) {
    return document(new VideoDocument(-1, null, reference, List.of(CUE)));
  }

  @Test
  void invalidTimingAndDuplicateCueIdsAreRejected() {
    invalid(document(CUE.withTiming(0.0, 0.0)));
    invalid(document(CUE, CUE));
  }

  @Test
  void problemsListsEachInvalidCueAndPhrasesBelongToOneLink() {
    var shared =
        CUE.withLine(
            Line.of("cue-a", "Range-le")
                .withTranslation("Put it away")
                .withAlignment(
                    newAlignment(
                        List.of("Range-le"),
                        List.of("Put", "it", "away"),
                        List.of(
                            newLink(List.of(0), List.of(0, 2), "Put away"),
                            newLink(List.of(0), List.of(1, 2), "it")))));
    var late = new VideoCue(Line.of("cue-b", "Oui"), 3.0, 2.0, null);
    var document = document(shared, late);

    assertEquals(
        List.of(
            "cue-a: Source phrase 0 belongs to more than one link",
            "cue-b: Invalid timing for cue cue-b"),
        StudyDocumentValidator.problems(document));
    var failure =
        assertThrows(
            IllegalArgumentException.class, () -> StudyDocumentValidator.validate(document));
    assertEquals(
        "cue-a: Source phrase 0 belongs to more than one link (and 1 more problems)",
        failure.getMessage());
  }

  @Test
  void languageTagsAreRequiredAndSupportIndependentNotesLanguages() {
    var document = document(CUE);
    assertDoesNotThrow(
        () -> StudyDocumentValidator.validate(document.withLanguages("ko", "fr", "en-GB")));
    for (String invalid : new String[] {"", " ", "not_a_tag", "und", "x-private"})
      invalid(document.withLanguages(invalid, "fr", "en"));
    invalid(document.withLanguages("de", null, "en"));
    invalid(document.withLanguages("de", "en", null));
  }
}
