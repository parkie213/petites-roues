package app.petitesroues.study;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.support.DocumentFixtures;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class DocumentEditsTest {
  private final StudyDocument document = DocumentFixtures.document("film");

  @Test
  void notesForTheReviewerLeaveTheDocumentAsItIs() {
    assertSame(
        document,
        DocumentEdits.apply(document, new DocumentEdit.ReviewRequest("stable-cue", "Check it")),
        "replaying thousands of queued review requests must not validate the document each time");
    assertSame(
        document, DocumentEdits.apply(document, new DocumentEdit.Comment("stable-cue", "Nice")));
  }

  @Test
  void notesStillNeedTextAndACueThatExists() {
    assertThrows(
        IllegalArgumentException.class,
        () -> DocumentEdits.apply(document, new DocumentEdit.Comment("stable-cue", " ")));
    assertThrows(
        NoSuchElementException.class,
        () -> DocumentEdits.apply(document, new DocumentEdit.ReviewRequest("gone", "Check it")));
  }
}
