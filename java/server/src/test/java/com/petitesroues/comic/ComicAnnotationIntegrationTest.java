package com.petitesroues.comic;

import static com.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.*;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.model.document.ComicCue;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.document.StudyCue;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.support.DocumentFixtures;
import com.petitesroues.support.HttpIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Exercises drawn bubble annotations: server-assigned ids and order, placement edits that preserve
 * teaching material, stale-state protection, deletion and review rows.
 */
class ComicAnnotationIntegrationTest extends HttpIntegrationTest {
  private String register(ComicCue... others) throws Exception {
    var existing =
        new ComicCue(
            StudyCue.of("page-001-bubble-01", "Salut !"),
            "page-001",
            1,
            List.of(newRegion(.1, .2, .3, .4)));
    var doc =
        DocumentFixtures.comic(
            "comic",
            "Comic",
            ReadingDirection.UNSPECIFIED,
            List.of(newPage("page-001", "001.jpg", null), newPage("page-002", "002.jpg", null)),
            Stream.concat(Stream.of(existing), Stream.of(others)).toArray(ComicCue[]::new));
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
        http.post()
            .uri(api + "/comic/cues")
            .body(
                Map.of(
                    "pageId", "page-002",
                    "source", "MERCI.",
                    "region", Map.of("x", .2, "y", .3, "width", .1, "height", .05)))
            .retrieve()
            .body(StudyDocument.class);
    var created = result.comic().cues().getLast();
    assertEquals("page-002-bubble-01", created.study().id());
    assertEquals("page-002", created.pageId());
    assertEquals(1, created.order());
    assertEquals("MERCI.", created.study().source());
    var queued = http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class);
    assertTrue(queued.toString().contains("annotate"));
  }

  @Test
  void savesBoxWithoutTextAndAddsTranscriptionLater() throws Exception {
    String api = register();
    var result =
        http.post()
            .uri(api + "/comic/cues")
            .body(
                Map.of(
                    "pageId",
                    "page-002",
                    "region",
                    Map.of("x", .2, "y", .3, "width", .1, "height", .05)))
            .retrieve()
            .body(StudyDocument.class);
    var box = result.comic().cues().get(1);
    assertEquals("", box.study().source());
    var loaded = http.get().uri(api).retrieve().body(StudyDocument.class);
    assertEquals(box, loaded.comic().cues().get(1));
    var queued = http.get().uri(api + "/review-queue").retrieve().body(JsonNode.class);
    assertTrue(queued.toString().contains(box.study().id()));
    var transcribed =
        http.put()
            .uri(api + "/comic/cues/" + box.study().id())
            .body(Map.of("expectedSource", "", "source", "BONJOUR !"))
            .retrieve()
            .body(StudyDocument.class);
    var cue = transcribed.comic().cues().get(1);
    assertEquals("BONJOUR !", cue.study().source());
    assertEquals(box.regions(), cue.regions());
  }

  @Test
  void rejectsUnknownPageAndOutOfRangeRegion() throws Exception {
    String api = register();
    var unknownPage =
        assertThrows(
            RestClientResponseException.class,
            () ->
                http.post()
                    .uri(api + "/comic/cues")
                    .body(
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
                http.post()
                    .uri(api + "/comic/cues")
                    .body(
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
        http.put()
            .uri(api + "/comic/cues/page-001-bubble-01/placement")
            .body(
                Map.of(
                    "expectedRegions", List.of(Map.of("x", .1, "y", .2, "width", .3, "height", .4)),
                    "regions", List.of(Map.of("x", .15, "y", .25, "width", .3, "height", .4))))
            .retrieve()
            .body(StudyDocument.class);
    assertEquals(.15, moved.comic().cues().get(0).regions().get(0).x());
    assertEquals("Salut !", moved.comic().cues().get(0).study().source());
    assertEquals(1, moved.comic().cues().get(0).order());
    var stale =
        assertThrows(
            RestClientResponseException.class,
            () ->
                http.put()
                    .uri(api + "/comic/cues/page-001-bubble-01/placement")
                    .body(
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
        http.delete()
            .uri(api + "/comic/cues/page-001-bubble-01")
            .retrieve()
            .body(StudyDocument.class);
    assertEquals(0, result.comic().cues().size());
    assertThrows(
        RestClientResponseException.class,
        () -> http.delete().uri(api + "/comic/cues/missing").retrieve().toBodilessEntity());
  }

  @Test
  void keepsTextBlockBoxesInPlace() throws Exception {
    var box = List.of(newRegion(.1, .6, .8, .3));
    String api =
        register(
            new ComicCue(StudyCue.of("intro-1", "Une phrase."), "page-002", 1, box, "intro"),
            new ComicCue(StudyCue.of("intro-2", "Une autre."), "page-002", 2, box, "intro"));
    var move =
        assertThrows(
            RestClientResponseException.class,
            () ->
                http.put()
                    .uri(api + "/comic/cues/intro-1/placement")
                    .body(
                        Map.of(
                            "expectedRegions",
                                List.of(Map.of("x", .1, "y", .6, "width", .8, "height", .3)),
                            "regions",
                                List.of(Map.of("x", .2, "y", .6, "width", .7, "height", .3))))
                    .retrieve()
                    .toBodilessEntity());
    assertEquals(BAD_REQUEST.value(), move.getStatusCode().value());
    var delete =
        assertThrows(
            RestClientResponseException.class,
            () -> http.delete().uri(api + "/comic/cues/intro-2").retrieve().toBodilessEntity());
    assertEquals(BAD_REQUEST.value(), delete.getStatusCode().value());
  }
}
