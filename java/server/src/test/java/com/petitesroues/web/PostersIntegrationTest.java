package com.petitesroues.web;

import static com.petitesroues.support.SpecFixtures.newPage;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.media.MediaFingerprint;
import com.petitesroues.model.document.ComicReference;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.library.ContentRepository;
import com.petitesroues.support.DocumentFixtures;
import com.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The Posters view as the server groups it, from real folders of media and documents. */
class PostersIntegrationTest extends HttpIntegrationTest {
  /** A one-page CBZ; {@code marker} makes each file's content, and so its fingerprint, distinct. */
  private static Path cbz(Path file, String marker) throws Exception {
    Files.createDirectories(file.getParent());
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry("page-001.png"));
      zip.write(marker.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return file;
  }

  /** A comic document referencing {@code media}, in {@code language}, with optional series. */
  private static void document(
      Path file, String id, String title, String language, String series, Path media)
      throws Exception {
    StudyDocument comic =
        DocumentFixtures.comic(
            id, title, ReadingDirection.RTL, List.of(newPage("p1", "page-001.png", "1")));
    var body = comic.comic().withReference(new ComicReference(MediaFingerprint.of(media), null));
    comic =
        new StudyDocument(
            comic.id(),
            comic.title(),
            language,
            comic.translationLanguage(),
            comic.notesLanguage(),
            series,
            comic.shelf(),
            comic.sourceUrl(),
            body);
    Files.createDirectories(file.getParent());
    Files.writeString(file, new StudyDocumentCodec().encode(comic), StandardCharsets.UTF_8);
  }

  private String add(Path folder) {
    return http.post()
        .uri("/api/repositories")
        .body(Map.of("localPath", folder.toString()))
        .retrieve()
        .body(ContentRepository.class)
        .id();
  }

  private JsonNode posters(String query) {
    return mapper.readTree(
        http.get().uri("/api/library/posters" + query).retrieve().body(String.class));
  }

  private static List<String> names(JsonNode posters) {
    return posters
        .path("shelves")
        .get(0)
        .path("groups")
        .valueStream()
        .map(group -> group.path("name").asString())
        .toList();
  }

  @Test
  void cardsGroupMediaAndDocumentsAcrossFoldersByTheSharedRules() throws Exception {
    Path media = root.resolve("media"), docs = root.resolve("docs");
    Path naruto1 = cbz(media.resolve("comics/naruto/Naruto-T01.cbz"), "naruto 1");
    cbz(media.resolve("comics/naruto/Naruto-T02.cbz"), "naruto 2");
    Path tintin = cbz(media.resolve("comics/tintin/Tintin-T01.cbz"), "tintin 1");
    // Naruto's document names no series: it joins the series its media's folder names.
    document(
        docs.resolve("naruto-t01/document.json"), "naruto-t01", "Naruto T01", "fr", null, naruto1);
    document(docs.resolve("tim/document.json"), "tim", "Tim und Struppi", "de", "Tintin", tintin);
    String mediaId = add(media), docsId = add(docs);

    var all = posters("");
    assertEquals(
        List.of(mediaId),
        all.path("folders").valueStream().map(JsonNode::asString).toList(),
        "only the folder holding media has cards");
    assertTrue(all.path("multilingual").asBoolean());
    var comics = all.path("shelves").get(0);
    assertEquals("Comics", comics.path("label").asString());
    assertEquals(List.of("Naruto", "Tintin"), names(all));

    var naruto = comics.path("groups").get(0);
    assertEquals("fr", naruto.path("language").asString(), "media without a document joins fr");
    assertEquals("2 tomes", naruto.path("count").asString());
    var slots = naruto.path("slots");
    assertEquals("Naruto T01", slots.get(0).path("label").asString());
    assertEquals("Naruto T02", slots.get(1).path("label").asString());
    var documented = slots.get(0).path("entry");
    assertEquals(
        "/api/titles/" + documented.path("id").asString() + "/card",
        naruto.path("art").path("url").asString(),
        "a comic's first page is its card");
    var bare = slots.get(1).path("entry");
    assertFalse(bare.path("documentAvailable").asBoolean());
    assertEquals("No study document", bare.path("unavailable").path("media-0").asString());
    assertTrue(bare.path("fingerprint").asString().startsWith("v1:"));
    assertFalse(documented.has("kind"), "only the record's own fields are sent");

    var french = posters("?language=fr");
    assertEquals(List.of("Naruto"), names(french));
    assertEquals(
        1,
        french.path("shelves").get(0).path("groups").get(0).path("slots").size(),
        "a chosen language hides media without a document");
    assertFalse(french.path("multilingual").asBoolean());

    assertEquals(
        List.of("Naruto", "Tintin"),
        names(posters("?repository=" + docsId)),
        "a folder without cards shows them all");
    assertEquals(List.of("Naruto", "Tintin"), names(posters("?repository=" + mediaId)));
  }
}
