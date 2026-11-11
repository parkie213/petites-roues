package app.petitesroues.study;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentJson;
import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.model.library.CatalogueEntry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentStoreTest {
  private final StudyDocumentCodec codec = new StudyDocumentCodec();
  private final DocumentStore store = new DocumentStore(new StudyDocumentReader(codec));

  @Test
  void candidateMustBeValidAndKeepTheRegisteredDocumentIdentity() {
    var entry = mock(CatalogueEntry.class);
    when(entry.documentId()).thenReturn("registered");
    var candidate = document("registered");
    assertTrue(
        store.problems(entry, codec.encode(candidate).getBytes(StandardCharsets.UTF_8)).isEmpty());
    assertEquals(
        List.of("Document ID must stay registered, not different"),
        store.problems(
            entry, codec.encode(candidate.withId("different")).getBytes(StandardCharsets.UTF_8)));
    var invalid =
        StudyDocumentJson.newMapper()
            .writeValueAsBytes(candidate.withId("different").withTitle(""));
    assertEquals(List.of("A title is required"), store.problems(entry, invalid));
    assertFalse(store.problems(entry, "{broken".getBytes(StandardCharsets.UTF_8)).isEmpty());
  }
}
