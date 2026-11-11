package app.petitesroues.library.linking;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.library.discovery.MediaKind;
import app.petitesroues.model.document.*;
import app.petitesroues.model.library.documents.DocumentEntry;
import app.petitesroues.model.library.documents.DocumentMetadata;
import app.petitesroues.model.library.media.MediaEntry;
import app.petitesroues.model.library.repository.Repository;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentEntriesTest {
  @Test
  void videoMetadataPreservesReferenceAndEpisodeWithoutLoadingCues() {
    var document =
        document(new VideoDocument(2, 3, new VideoReference("v1:video", 10L, 45.0), List.of()));
    var metadata = DocumentMetadata.from(document);
    assertEquals(MediaKind.VIDEO, metadata.kind());
    assertEquals(2, metadata.season());
    assertEquals(3, metadata.episode());
    assertNull(metadata.volume());
    assertEquals(45.0, metadata.durationSeconds());
    assertEquals("v1:video", metadata.referenceFingerprint());
    assertEquals("portable", metadata.documentId());
  }

  @Test
  void comicVolumeAndAbsentReferencesStayDistinctFromVideoMetadata() {
    var comic =
        DocumentMetadata.from(document(new ComicDocument(4, null, null, List.of(), List.of())));
    assertEquals(MediaKind.COMIC, comic.kind());
    assertEquals(4, comic.volume());
    assertNull(comic.season());
    assertNull(comic.durationSeconds());
    assertNull(comic.referenceFingerprint());
    var video = DocumentMetadata.from(document(new VideoDocument(null, null, null, List.of())));
    assertNull(video.referenceFingerprint());
    assertNull(video.durationSeconds());
  }

  @Test
  void repositoryDocumentLinksAcrossRepositoriesAndLocalManualKeyRemainsSeparate() {
    var metadata =
        DocumentMetadata.from(
            document(
                new VideoDocument(
                    null, null, new VideoReference("v1:video", null, null), List.of())));
    var entry =
        new DocumentEntry(new Repository("documents", "Study"), "film/document.json", metadata);
    var media =
        List.of(
            new MediaLinks.Candidate<>(
                "copy", new MediaEntry("media", "film.mp4", MediaKind.VIDEO, "v1:video")),
            new MediaLinks.Candidate<>(
                "manual",
                new MediaEntry("other", "cut.mp4", MediaKind.VIDEO, "v1:other"),
                "local-registration"));
    assertEquals("copy", MediaLinks.choose(null, entry, media).orElseThrow());
    assertEquals("manual", MediaLinks.choose("local-registration", entry, media).orElseThrow());
    assertEquals("copy", MediaLinks.choose("portable", entry, media).orElseThrow());
  }

  private static StudyDocument document(StudyBody body) {
    return new StudyDocument(
        "portable", "A title", "fr", "en", "en", "Series", "Films", null, body);
  }
}
