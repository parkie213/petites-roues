package com.petitesroues.documents;

import static com.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.model.document.Line;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoCue;
import com.petitesroues.model.document.VideoDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudyDocumentValidatorTest {
  private static final VideoCue CUE = new VideoCue(Line.of("cue-a", "Bonjour"), 0.0, 1.0, null);

  private static StudyDocument document(VideoDocument video) {
    return new StudyDocument("example", "Example", "fr", "en", "en", null, null, null, video, null);
  }

  private static StudyDocument document(VideoCue... cues) {
    return document(new VideoDocument(null, null, null, null, List.of(cues)));
  }

  private static void invalid(StudyDocument document) {
    assertThrows(IllegalArgumentException.class, () -> StudyDocumentValidator.validate(document));
  }

  @Test
  void descriptiveMetadataDoesNotNeedARestrictedVocabulary() {
    var video =
        new VideoDocument(-1, null, "Whisper large; not independently checked", null, List.of(CUE));
    assertDoesNotThrow(() -> StudyDocumentValidator.validate(document(video)));
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
