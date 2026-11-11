package app.petitesroues;

import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.HttpClients.client;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.UpdateCueTiming;
import app.petitesroues.study.DocumentStore;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Concurrent HTTP edits against real JSON files and the production SQLite configuration. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationConcurrencyTest {
  static final Path ROOT;

  static {
    try {
      ROOT = Files.createTempDirectory("subtitle-concurrency-");
    } catch (Exception error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + ROOT.resolve("library.sqlite"));
  }

  @Autowired ObjectMapper mapper;
  @MockitoSpyBean DocumentStore documents;
  @LocalServerPort int port;
  @Autowired CatalogueStore catalogue;

  @AfterAll
  static void cleanup(@Autowired DataSource source) throws Exception {
    ((HikariDataSource) source).close();
    FileSystemUtils.deleteRecursively(ROOT);
  }

  @Test
  void simultaneousEditsToDifferentCuesBothSurvive() throws Exception {
    var title = document("two-edits");
    var first = title.video().cues().getFirst();
    var second = first.withTiming(2.0, 3.0).withLine(s -> s.withId("second-cue"));
    registerTitle(title.withVideo(v -> v.withCues(List.of(first, second))));

    overlap(
        () -> edit(title.id(), "stable-cue", .2, .7),
        () -> edit(title.id(), "second-cue", 2.3, 2.8));

    var saved = load(title.id());
    assertEquals(.2, saved.video().cues().getFirst().start());
    assertEquals(.7, saved.video().cues().getFirst().end());
    assertEquals(2.3, saved.video().cues().get(1).start());
    assertEquals(2.8, saved.video().cues().get(1).end());
    for (var cue : saved.video().cues()) {
      assertEquals("Bonjour !", cue.line().source());
      assertEquals("Hello!", cue.line().translation());
      assertEquals(List.of("A greeting."), cue.line().notes());
    }
  }

  @Test
  void repositoryRemovalWaitsForAnEditThenRefusesToLoseIt() throws Exception {
    var title = document("edit-and-remove");
    registerTitle(title);
    var entry = catalogue.find(title.id()).orElseThrow();
    byte[] original = Files.readAllBytes(entry.documentFile());
    var removal = new java.util.concurrent.atomic.AtomicReference<Exception>();
    overlap(
        () -> edit(title.id(), "stable-cue", .2, .7),
        () -> {
          try {
            http()
                .delete()
                .uri("/api/repositories/" + entry.location().repositoryId())
                .retrieve()
                .toBodilessEntity();
          } catch (HttpClientErrorException refused) {
            removal.set(refused);
          }
        });
    assertInstanceOf(HttpClientErrorException.BadRequest.class, removal.get());
    assertTrue(catalogue.find(title.id()).isPresent());
    assertArrayEquals(original, Files.readAllBytes(entry.documentFile()));
    var saved = load(title.id());
    assertEquals(.2, saved.video().cues().getFirst().start());
    assertEquals(.7, saved.video().cues().getFirst().end());
  }

  @FunctionalInterface
  interface Request {
    void run() throws Exception;
  }

  private void overlap(Request first, Request second) throws Exception {
    var read = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var pauseOnce = new AtomicBoolean(true);
    // Pause after a real read, exposing the read/write gap without mocking storage behaviour.
    doAnswer(
            call -> {
              Object result = call.callRealMethod();
              if (pauseOnce.getAndSet(false)) {
                read.countDown();
                if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("Read timed out");
              }
              return result;
            })
        .when(documents)
        .read(any());
    try (var threads = Executors.newFixedThreadPool(2)) {
      try {
        var firstResult =
            threads.submit(
                () -> {
                  first.run();
                  return null;
                });
        assertTrue(read.await(5, TimeUnit.SECONDS));
        var secondResult =
            threads.submit(
                () -> {
                  second.run();
                  return null;
                });
        try {
          secondResult.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expected) {
          /* A serialized request waits for the first edit. */
        } finally {
          resume.countDown();
        }
        firstResult.get(5, TimeUnit.SECONDS);
        secondResult.get(5, TimeUnit.SECONDS);
      } finally {
        resume.countDown();
      }
    }
  }

  private RestClient http() {
    return client("http://127.0.0.1:" + port);
  }

  private StudyDocument load(String id) {
    return http().get().uri("/api/titles/" + id).retrieve().body(StudyDocument.class);
  }

  private void edit(String id, String cue, double start, double end) {
    http()
        .post()
        .uri("/api/titles/" + id + "/timings")
        .body(List.of(new UpdateCueTiming(cue, start, end)))
        .retrieve()
        .toBodilessEntity();
  }

  private void registerTitle(StudyDocument title) throws Exception {
    Path file = Files.createTempDirectory(ROOT, "registered-").resolve("document.json");
    Files.write(file, new StudyDocumentCodec().encode(title).getBytes(StandardCharsets.UTF_8));
    http()
        .post()
        .uri("/api/repositories")
        .body(java.util.Map.of("localPath", file.getParent().toString()))
        .retrieve()
        .toBodilessEntity();
  }
}
