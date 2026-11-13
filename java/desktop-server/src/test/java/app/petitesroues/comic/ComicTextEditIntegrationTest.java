package app.petitesroues.comic;

import static app.petitesroues.support.HttpClients.command;
import static app.petitesroues.support.HttpClients.discover;
import static app.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.support.DocumentFixtures;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Exercises persisted source corrections, stale-editor protection and annotation review queueing.
 */
class ComicTextEditIntegrationTest extends HttpIntegrationTest {
  @Test
  void editsOnlySelectedBubbleAndInvalidatesItsTeachingMaterial() throws Exception {
    var cue =
        newBubble(
            Line.of("bubble", "Salut !")
                .withTranslation("Hi!")
                .withNotes(List.of("A greeting."))
                .withAlignment(
                    newAlignment(
                        List.of("Salut !"),
                        List.of("Hi!"),
                        List.of(newLink(List.of(0), List.of(0), "A greeting.")))),
            List.of(newRegion(.1, .2, .3, .4)));
    var other =
        newBubble(
            Line.of("other", "Merci !").withTranslation("Thanks!"),
            List.of(newRegion(.5, .5, .2, .2)));
    var doc =
        DocumentFixtures.comic(
            "comic",
            "Comic",
            ReadingDirection.LTR,
            List.of(newPage("page", "001.jpg", null, cue, other)));
    var folder = root.resolve("comic");
    Files.createDirectories(folder);
    Files.write(
        folder.resolve("document.json"),
        new StudyDocumentCodec().encode(doc).getBytes(StandardCharsets.UTF_8));
    discover(http, "add", null, Map.of("localPath", folder.toString()), ContentRepository.class);
    String api = "/api/titles/comic";
    var result =
        command(
                http,
                api,
                "edit-comic-text",
                "bubble",
                Map.of("expectedSource", "Salut !", "source", "Bonjour !"))
            .retrieve()
            .body(StudyDocument.class);
    var edited = result.comic().boxes().get(0);
    assertEquals("Bonjour !", edited.lines().getFirst().source());
    assertEquals(cue.regions(), edited.regions());
    assertEquals(cue.lines().getFirst().id(), edited.lines().getFirst().id());
    assertFalse(edited.lines().getFirst().translation() != null);
    assertTrue(edited.lines().getFirst().notes().isEmpty());
    assertFalse(edited.lines().getFirst().alignment() != null);
    assertEquals(other, result.comic().boxes().getLast());
    var json = mapper.readTree(new StudyDocumentCodec().encode(result));
    assertFalse(json.path("comic").has("boxes"), "boxes live in their pages");
    var written = json.path("comic").path("pages").get(0).path("boxes").get(0);
    assertFalse(written.has("pageId") || written.has("order") || written.has("start"));
    assertEquals(
        "Bonjour !",
        http.get()
            .uri(api)
            .retrieve()
            .body(StudyDocument.class)
            .comic()
            .boxes()
            .getFirst()
            .lines()
            .getFirst()
            .source());
    var queue = http.get().uri(api + "/review").retrieve().body(JsonNode.class);
    assertEquals(1, queue.size());
    assertEquals("bubble", queue.get(0).path("cueIds").get(0).asText());
    assertEquals(
        400,
        command(
                http,
                api,
                "edit-comic-text",
                "bubble",
                Map.of("expectedSource", "Salut !", "source", "Stale edit"))
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    assertEquals(
        404,
        command(
                http,
                api,
                "edit-comic-text",
                "missing",
                Map.of("expectedSource", "x", "source", "y"))
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    assertEquals(
        400,
        command(
                http,
                api,
                "edit-comic-text",
                "bubble",
                Map.of("expectedSource", "Bonjour !", "source", " "))
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    command(
            http,
            api,
            "edit-comic-text",
            "bubble",
            Map.of("expectedSource", "Bonjour !", "source", "Bonjour !"))
        .retrieve()
        .toBodilessEntity();
    assertEquals(1, http.get().uri(api + "/review").retrieve().body(JsonNode.class).size());

    // A comment on a bubble is queued for review and leaves the comic unchanged.
    var unchanged = http.get().uri(api).retrieve().body(StudyDocument.class);
    assertEquals(
        unchanged,
        command(http, api, "comment", "other", Map.of("text", "Merci → Thank you?"))
            .retrieve()
            .body(StudyDocument.class));
    var comments = http.get().uri(api + "/review").retrieve().body(JsonNode.class);
    assertEquals("comment", comments.get(1).get("kind").asString());
    assertEquals("Merci → Thank you?", comments.get(1).get("suggestion").asString());
  }
}
