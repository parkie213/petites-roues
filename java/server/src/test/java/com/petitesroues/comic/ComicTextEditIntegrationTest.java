package com.petitesroues.comic;

import static com.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.model.document.Line;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.support.DocumentFixtures;
import com.petitesroues.support.HttpIntegrationTest;
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
            "page",
            1,
            List.of(newRegion(.1, .2, .3, .4)));
    var other =
        newBubble(
            Line.of("other", "Merci !").withTranslation("Thanks!"),
            "page",
            2,
            List.of(newRegion(.5, .5, .2, .2)));
    var doc =
        DocumentFixtures.comic(
            "comic",
            "Comic",
            ReadingDirection.LTR,
            List.of(newPage("page", "001.jpg", null)),
            cue,
            other);
    var folder = root.resolve("comic");
    Files.createDirectories(folder);
    Files.write(
        folder.resolve("document.json"),
        new StudyDocumentCodec().encode(doc).getBytes(StandardCharsets.UTF_8));
    http.post()
        .uri("/api/repositories")
        .body(Map.of("localPath", folder.toString()))
        .retrieve()
        .toBodilessEntity();
    String api = "/api/titles/comic";
    var result =
        http.put()
            .uri(api + "/comic/lines/bubble")
            .body(Map.of("expectedSource", "Salut !", "source", "Bonjour !"))
            .retrieve()
            .body(StudyDocument.class);
    var edited = result.comic().boxes().get(0);
    assertEquals("Bonjour !", edited.lines().getFirst().source());
    assertEquals(cue.regions(), edited.regions());
    assertEquals(cue.lines().getFirst().id(), edited.lines().getFirst().id());
    assertEquals(cue.order(), edited.order());
    assertFalse(edited.lines().getFirst().translation() != null);
    assertTrue(edited.lines().getFirst().notes().isEmpty());
    assertFalse(edited.lines().getFirst().alignment() != null);
    assertEquals(other, result.comic().boxes().getLast());
    var json = mapper.readTree(new StudyDocumentCodec().encode(result));
    assertFalse(json.path("comic").path("boxes").get(0).has("start"));
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
    var queue = http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class);
    assertEquals(1, queue.size());
    assertEquals("bubble", queue.get(0).path("cueIds").get(0).asText());
    assertEquals(
        400,
        http.put()
            .uri(api + "/comic/lines/bubble")
            .body(Map.of("expectedSource", "Salut !", "source", "Stale edit"))
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    assertEquals(
        404,
        http.put()
            .uri(api + "/comic/lines/missing")
            .body(Map.of("expectedSource", "x", "source", "y"))
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    assertEquals(
        400,
        http.put()
            .uri(api + "/comic/lines/bubble")
            .body(Map.of("expectedSource", "Bonjour !", "source", " "))
            .<Integer>exchange((req, res) -> res.getStatusCode().value())
            .intValue());
    http.put()
        .uri(api + "/comic/lines/bubble")
        .body(Map.of("expectedSource", "Bonjour !", "source", "Bonjour !"))
        .retrieve()
        .toBodilessEntity();
    assertEquals(1, http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class).size());

    // A comment on a bubble is queued for review and leaves the comic unchanged.
    var unchanged = http.get().uri(api).retrieve().body(StudyDocument.class);
    assertEquals(
        unchanged,
        http.post()
            .uri(api + "/cues/other/comment")
            .body(Map.of("text", "Merci → Thank you?"))
            .retrieve()
            .body(StudyDocument.class));
    var comments = http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class);
    assertEquals("comment", comments.get(1).get("kind").asString());
    assertEquals("Merci → Thank you?", comments.get(1).get("suggestion").asString());
  }
}
