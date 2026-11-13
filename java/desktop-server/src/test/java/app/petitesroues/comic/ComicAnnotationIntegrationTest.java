package app.petitesroues.comic;

import static app.petitesroues.support.HttpClients.command;
import static app.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.model.document.ComicBox;
import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.support.DocumentFixtures;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Exercises drawn bubble annotations: server-assigned ids and order, placement edits that preserve
 * teaching material, stale-state protection, deletion and review rows.
 */
class ComicAnnotationIntegrationTest extends HttpIntegrationTest {
  /** One bubble on page-001, and {@code onPage2} on page-002. */
  private String register(ComicBox... onPage2) throws Exception {
    var existing =
        newBubble(Line.of("page-001-bubble-01", "Salut !"), List.of(newRegion(.1, .2, .3, .4)));
    var doc =
        DocumentFixtures.comic(
            "comic",
            "Comic",
            ReadingDirection.LTR,
            List.of(
                newPage("page-001", "001.jpg", null, existing),
                newPage("page-002", "002.jpg", null, onPage2)));
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
    return "/api/titles/comic";
  }

  @Test
  void createsBubbleWithServerAssignedIdAndNextOrder() throws Exception {
    String api = register();
    var result =
        command(
                http,
                api,
                "create-comic-box",
                null,
                Map.of(
                    "pageId", "page-002",
                    "source", "MERCI.",
                    "region", Map.of("x", .2, "y", .3, "width", .1, "height", .05)))
            .retrieve()
            .body(StudyDocument.class);
    var page = result.comic().pages().get(1);
    var created = page.boxes().getLast();
    var line = created.lines().getFirst();
    // Box and line get their own fresh UUIDs.
    assertDoesNotThrow(() -> UUID.fromString(created.id()));
    assertDoesNotThrow(() -> UUID.fromString(line.id()));
    assertNotEquals(created.id(), line.id());
    assertEquals(List.of(created), page.boxes());
    assertEquals("MERCI.", line.source());
    var queued = http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class);
    assertTrue(queued.toString().contains("annotate"));
  }

  @Test
  void savesBoxWithoutTextAndAddsTranscriptionLater() throws Exception {
    String api = register();
    var result =
        command(
                http,
                api,
                "create-comic-box",
                null,
                Map.of(
                    "pageId",
                    "page-002",
                    "region",
                    Map.of("x", .2, "y", .3, "width", .1, "height", .05)))
            .retrieve()
            .body(StudyDocument.class);
    var box = result.comic().boxes().get(1);
    assertEquals("", box.lines().getFirst().source());
    var loaded = http.get().uri(api).retrieve().body(StudyDocument.class);
    assertEquals(box, loaded.comic().boxes().get(1));
    var queued = http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class);
    assertTrue(queued.toString().contains(box.lines().getFirst().id()));
    var transcribed =
        command(
                http,
                api,
                "edit-comic-text",
                box.lines().getFirst().id(),
                Map.of("expectedSource", "", "source", "BONJOUR !"))
            .retrieve()
            .body(StudyDocument.class);
    var transcribedBox = transcribed.comic().boxes().get(1);
    assertEquals("BONJOUR !", transcribedBox.lines().getFirst().source());
    assertEquals(box.regions(), transcribedBox.regions());
  }

  @Test
  void rejectsUnknownPageAndOutOfRangeRegion() throws Exception {
    String api = register();
    var unknownPage =
        assertThrows(
            RestClientResponseException.class,
            () ->
                command(
                        http,
                        api,
                        "create-comic-box",
                        null,
                        Map.of(
                            "pageId", "page-999",
                            "source", "X",
                            "region", Map.of("x", .1, "y", .1, "width", .1, "height", .1)))
                    .retrieve()
                    .toBodilessEntity());
    assertEquals(NOT_FOUND.value(), unknownPage.getStatusCode().value());
    var badRegion =
        assertThrows(
            RestClientResponseException.class,
            () ->
                command(
                        http,
                        api,
                        "create-comic-box",
                        null,
                        Map.of(
                            "pageId", "page-001",
                            "source", "X",
                            "region", Map.of("x", .5, "y", .5, "width", .8, "height", .1)))
                    .retrieve()
                    .toBodilessEntity());
    assertEquals(BAD_REQUEST.value(), badRegion.getStatusCode().value());
  }

  @Test
  void movesRegionPreservingTextAndRejectsStaleExpectations() throws Exception {
    String api = register();
    var moved =
        command(
                http,
                api,
                "place-comic-box",
                "box-page-001-bubble-01",
                Map.of(
                    "expectedRegions", List.of(Map.of("x", .1, "y", .2, "width", .3, "height", .4)),
                    "regions", List.of(Map.of("x", .15, "y", .25, "width", .3, "height", .4))))
            .retrieve()
            .body(StudyDocument.class);
    assertEquals(.15, moved.comic().boxes().get(0).regions().get(0).x());
    assertEquals("Salut !", moved.comic().boxes().get(0).lines().getFirst().source());
    assertEquals("box-page-001-bubble-01", moved.comic().pages().get(0).boxes().getFirst().id());
    var stale =
        assertThrows(
            RestClientResponseException.class,
            () ->
                command(
                        http,
                        api,
                        "place-comic-box",
                        "box-page-001-bubble-01",
                        Map.of(
                            "expectedRegions",
                                List.of(Map.of("x", .1, "y", .2, "width", .3, "height", .4)),
                            "regions",
                                List.of(Map.of("x", .2, "y", .2, "width", .3, "height", .4))))
                    .retrieve()
                    .toBodilessEntity());
    assertEquals(BAD_REQUEST.value(), stale.getStatusCode().value());
  }

  @Test
  void deletesBubbleAndKeepsOthers() throws Exception {
    String api = register();
    var result =
        command(http, api, "delete-comic-box", "box-page-001-bubble-01", null)
            .retrieve()
            .body(StudyDocument.class);
    assertEquals(0, result.comic().boxes().size());
    assertThrows(
        RestClientResponseException.class,
        () ->
            command(http, api, "delete-comic-box", "missing", null).retrieve().toBodilessEntity());
  }

  @Test
  void reordersABoxOnItsPage() throws Exception {
    var region = List.of(newRegion(.1, .1, .2, .2));
    String api =
        register(
            newBubble(Line.of("a", "A"), region),
            newBubble(Line.of("b", "B"), region),
            newBubble(Line.of("c", "C"), region));
    var placement = Map.of("x", .1, "y", .1, "width", .2, "height", .2);
    var reordered =
        command(
                http,
                api,
                "place-comic-box",
                "box-c",
                Map.of(
                    "expectedRegions",
                    List.of(placement),
                    "regions",
                    List.of(placement),
                    "order",
                    1))
            .retrieve()
            .body(StudyDocument.class);
    assertEquals(
        List.of("box-c", "box-a", "box-b"),
        reordered.comic().pages().get(1).boxes().stream().map(ComicBox::id).toList());
    var beyond =
        assertThrows(
            RestClientResponseException.class,
            () ->
                command(
                        http,
                        api,
                        "place-comic-box",
                        "box-c",
                        Map.of(
                            "expectedRegions",
                            List.of(placement),
                            "regions",
                            List.of(placement),
                            "order",
                            4))
                    .retrieve()
                    .toBodilessEntity());
    assertEquals(BAD_REQUEST.value(), beyond.getStatusCode().value());
  }

  @Test
  void editsOneLineOfATextBlockAndMovesOrDeletesItsBoxWhole() throws Exception {
    var box = List.of(newRegion(.1, .6, .8, .3));
    String api =
        register(
            new ComicBox(
                "intro",
                box,
                List.of(Line.of("intro-1", "Une phrase."), Line.of("intro-2", "Une autre."))));
    var edited =
        command(
                http,
                api,
                "edit-comic-text",
                "intro-2",
                Map.of("expectedSource", "Une autre.", "source", "Une seconde."))
            .retrieve()
            .body(StudyDocument.class);
    var block = edited.comic().boxes().get(1);
    assertEquals(
        List.of("Une phrase.", "Une seconde."), block.lines().stream().map(Line::source).toList());

    var moved =
        command(
                http,
                api,
                "place-comic-box",
                "intro",
                Map.of(
                    "expectedRegions", List.of(Map.of("x", .1, "y", .6, "width", .8, "height", .3)),
                    "regions", List.of(Map.of("x", .2, "y", .6, "width", .7, "height", .3))))
            .retrieve()
            .body(StudyDocument.class);
    block = moved.comic().boxes().get(1);
    assertEquals(.2, block.regions().get(0).x());
    assertEquals(2, block.lines().size());

    // A line ID is not a box ID: box routes reject it.
    var byLine =
        assertThrows(
            RestClientResponseException.class,
            () ->
                command(http, api, "delete-comic-box", "intro-1", null)
                    .retrieve()
                    .toBodilessEntity());
    assertEquals(NOT_FOUND.value(), byLine.getStatusCode().value());
    var deleted =
        command(http, api, "delete-comic-box", "intro", null).retrieve().body(StudyDocument.class);
    assertEquals(
        List.of("box-page-001-bubble-01"),
        deleted.comic().boxes().stream().map(ComicBox::id).toList());
  }
}
