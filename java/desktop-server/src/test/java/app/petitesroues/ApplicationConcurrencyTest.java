package app.petitesroues;

import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.HttpClients.client;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.library.discovery.ContentRepositoryScanner;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.UpdateCueTiming;
import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.study.DocumentStore;
import app.petitesroues.study.ReviewQueue;
import app.petitesroues.study.ReviewQueueService;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Concurrent HTTP requests against real JSON files and the production SQLite configuration (one
 * pooled connection). Each test pauses one request at a boundary with a latch, inside a transaction
 * or inside discovery, and shows what other requests do meanwhile. Nothing waits on a clock: a
 * request that must queue behind a transaction is observed waiting for the pool's one connection.
 */
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

  @MockitoSpyBean ReviewQueue queue;
  @MockitoSpyBean ContentRepositoryScanner scanner;
  @LocalServerPort int port;
  @Autowired CatalogueStore catalogue;
  @Autowired ReviewQueueService review;
  @Autowired AcceptedDocumentStore accepted;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource source;

  @AfterAll
  static void cleanup(@Autowired DataSource source) throws Exception {
    ((HikariDataSource) source).close();
    FileSystemUtils.deleteRecursively(ROOT);
  }

  @Test
  void theProductionPoolHasOneConnection() {
    assertEquals(1, ((HikariDataSource) source).getMaximumPoolSize());
  }

  @Test
  void simultaneousEditsToDifferentCuesBothSurvive() throws Exception {
    var title = twoCueTitle("two-edits");
    registerTitle(title);

    var gate = pauseAfter(queue);
    var failures =
        overlap(
            gate,
            () -> edit(title.id(), "stable-cue", .2, .7),
            () -> edit(title.id(), "second-cue", 2.3, 2.8));

    assertEquals(List.of(), failures);
    assertEquals(2, review.pending(title.id()).size());
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
  void anEditInvalidAfterAnEarlierEditIsRejectedNotQueued() throws Exception {
    var title = twoCueTitle("edit-after-delete");
    registerTitle(title);

    var gate = pauseAfter(queue);
    var failures =
        overlap(
            gate,
            () -> delete(title.id(), "second-cue"),
            () -> edit(title.id(), "second-cue", 2.3, 2.8));

    assertEquals(1, failures.size());
    assertInstanceOf(HttpClientErrorException.class, failures.getFirst());
    assertEquals(1, review.pending(title.id()).size());
    assertEquals(1, load(title.id()).video().cues().size());
  }

  @Test
  void anEditDuringRefreshDiscoveryDoesNotWaitAndSurvivesTheCommit() throws Exception {
    var title = document("edit-during-refresh");
    registerTitle(title);
    String repository = repositoryOf(title.id());

    var gate = new Gate();
    doAnswer(pausing(gate)).when(scanner).scan(any());
    try (var threads = Executors.newSingleThreadExecutor()) {
      var refresh = threads.submit(() -> refresh(repository));
      assertTrue(gate.entered.await(10, TimeUnit.SECONDS));

      edit(title.id(), "stable-cue", .2, .7); // returns while discovery is paused

      assertEquals(1, review.pending(title.id()).size());
      gate.release.countDown();
      refresh.get(10, TimeUnit.SECONDS);
    } finally {
      gate.release.countDown();
    }

    var pending = review.pending(title.id());
    assertEquals(1, pending.size());
    assertEquals("pending", pending.getFirst().state());
    assertEquals(.2, load(title.id()).video().cues().getFirst().start());
  }

  @Test
  void aRefreshWhoseRepositoryIsRemovedDuringDiscoveryCommitsNothing() throws Exception {
    var title = document("removed-during-refresh");
    registerTitle(title);
    String repository = repositoryOf(title.id());

    var gate = new Gate();
    doAnswer(pausing(gate)).when(scanner).scan(any());
    try (var threads = Executors.newSingleThreadExecutor()) {
      var refresh = threads.submit(() -> failureOf(() -> refresh(repository)));
      assertTrue(gate.entered.await(10, TimeUnit.SECONDS));
      http().delete().uri("/api/repositories/" + repository).retrieve().toBodilessEntity();
      gate.release.countDown();

      var failure = refresh.get(10, TimeUnit.SECONDS);
      var refused = assertInstanceOf(HttpClientErrorException.BadRequest.class, failure);
      assertTrue(refused.getResponseBodyAsString().contains("was removed"));
    } finally {
      gate.release.countDown();
    }

    assertTrue(catalogue.find(title.id()).isEmpty());
    assertTrue(repositories().stream().noneMatch(r -> r.id().equals(repository)));
    assertEquals(0, count("titles WHERE repository_id = '" + repository + "'"));
    assertEquals(0, count("media WHERE repository_id = '" + repository + "'"));
  }

  @Test
  void removalWaitsForAnEditThenRefusesToLoseIt() throws Exception {
    var title = document("edit-then-remove");
    registerTitle(title);
    var entry = catalogue.find(title.id()).orElseThrow();
    byte[] original = Files.readAllBytes(entry.documentFile());

    var gate = pauseAfter(queue);
    var failures =
        overlap(
            gate,
            () -> edit(title.id(), "stable-cue", .2, .7),
            () -> remove(entry.location().repositoryId()));

    assertEquals(1, failures.size());
    assertInstanceOf(HttpClientErrorException.BadRequest.class, failures.getFirst());
    assertTrue(catalogue.find(title.id()).isPresent());
    assertArrayEquals(original, Files.readAllBytes(entry.documentFile()));
    assertEquals(1, review.pending(title.id()).size());
    assertEquals(.2, load(title.id()).video().cues().getFirst().start());
  }

  @Test
  void anEditAfterAWinningRemovalFailsAndLeavesNoQueueRow() throws Exception {
    var title = document("remove-then-edit");
    registerTitle(title);
    String repository = repositoryOf(title.id());

    var gate = new Gate();
    doAnswer(
            call -> {
              Object unwritten = call.callRealMethod();
              gate.pass();
              return unwritten;
            })
        .when(queue)
        .unwrittenEdits(any());
    var failures =
        overlap(gate, () -> remove(repository), () -> edit(title.id(), "stable-cue", .2, .7));

    assertEquals(1, failures.size());
    assertInstanceOf(HttpClientErrorException.NotFound.class, failures.getFirst());
    assertTrue(repositories().stream().noneMatch(r -> r.id().equals(repository)));
    assertEquals(0, count("review_queue WHERE title_id = '" + title.id() + "'"));
  }

  @Test
  void anEditDuringBatchCompletionSurvivesAndTheBatchCloses() throws Exception {
    var title = twoCueTitle("edit-during-completion");
    registerTitle(title);
    var entry = catalogue.find(title.id()).orElseThrow();
    edit(title.id(), "stable-cue", .2, .7);
    var batch = review.claim(title.id(), 50).orElseThrow();
    // The agent writes the batch document into the registered file.
    byte[] written =
        new StudyDocumentCodec().encode(batch.document()).getBytes(StandardCharsets.UTF_8);
    Files.write(entry.documentFile(), written);

    var gate = pauseAfter(queue);
    var failures =
        overlap(
            gate,
            () ->
                http()
                    .post()
                    .uri(
                        "/api/titles/"
                            + title.id()
                            + "/review/batches/"
                            + batch.batch()
                            + "/complete")
                    .retrieve()
                    .toBodilessEntity(),
            () -> edit(title.id(), "second-cue", 2.3, 2.8));

    assertEquals(List.of(), failures);
    assertEquals(DocumentStore.sha256(written), accepted.findHash(title.id()).orElseThrow());
    assertTrue(queue.openBatch(entry).isEmpty());
    var pending = review.pending(title.id());
    assertEquals(1, pending.size());
    assertEquals("pending", pending.getFirst().state());
    assertEquals(1, review.history(title.id()).size());
    var saved = load(title.id());
    assertEquals(.2, saved.video().cues().getFirst().start());
    assertEquals(2.3, saved.video().cues().get(1).start());
  }

  @Test
  void concurrentAddsOfTheSameFolderLeaveOneRepository() throws Exception {
    var folder = Files.createTempDirectory(ROOT, "same-folder-");
    Files.write(
        folder.resolve("document.json"),
        new StudyDocumentCodec().encode(document("added-twice")).getBytes(StandardCharsets.UTF_8));
    var bothDiscovering = new CyclicBarrier(2);
    doAnswer(
            call -> {
              bothDiscovering.await(10, TimeUnit.SECONDS);
              return call.callRealMethod();
            })
        .when(scanner)
        .scan(any());

    var failures = runTogether(() -> add(folder), () -> add(folder));

    assertEquals(1, failures.size());
    var refused = assertInstanceOf(HttpClientErrorException.BadRequest.class, failures.getFirst());
    assertTrue(refused.getResponseBodyAsString().contains("already registered"));
    String path = real(folder);
    assertEquals(1, repositories().stream().filter(r -> r.localPath().equals(path)).count());
    assertEquals(1, count("titles WHERE document_id = 'added-twice'"));
  }

  @Test
  void anOlderOverlappingRefreshCommitsNothing() throws Exception {
    var title = document("refreshed-twice");
    registerTitle(title);
    String repository = repositoryOf(title.id());
    var bothDiscovering = new CyclicBarrier(2);
    doAnswer(
            call -> {
              bothDiscovering.await(10, TimeUnit.SECONDS);
              return call.callRealMethod();
            })
        .when(scanner)
        .scan(any());

    var failures = runTogether(() -> refresh(repository), () -> refresh(repository));

    assertEquals(1, failures.size());
    var refused = assertInstanceOf(HttpClientErrorException.BadRequest.class, failures.getFirst());
    assertTrue(refused.getResponseBodyAsString().contains("another operation"));
    assertEquals(1, repositories().stream().filter(r -> r.id().equals(repository)).count());
  }

  @FunctionalInterface
  interface Request {
    void run() throws Exception;
  }

  /** A pause point a request reaches once; the test decides when it may continue. */
  static final class Gate {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    private final AtomicBoolean armed = new AtomicBoolean(true);

    void pass() throws InterruptedException {
      if (!armed.getAndSet(false)) return;
      entered.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Never released");
    }
  }

  /** Pauses the first queue read, which every edit and completion makes inside its transaction. */
  private Gate pauseAfter(ReviewQueue spy) throws Exception {
    var gate = new Gate();
    doAnswer(
            call -> {
              Object result = call.callRealMethod();
              gate.pass();
              return result;
            })
        .when(spy)
        .read(any());
    return gate;
  }

  /** Runs the real method, then waits at the gate. */
  private static Answer<Object> pausing(Gate gate) {
    return call -> {
      Object result = call.callRealMethod();
      gate.pass();
      return result;
    };
  }

  /**
   * Runs {@code first} until it reaches the gate, starts {@code second}, waits until {@code second}
   * is queued for the pool's only connection, then lets {@code first} continue. Returns the
   * failures of both requests (none for a request that succeeded).
   */
  private List<Throwable> overlap(Gate gate, Request first, Request second) throws Exception {
    var pool = ((HikariDataSource) source).getHikariPoolMXBean();
    try (var threads = Executors.newFixedThreadPool(2)) {
      try {
        var firstResult = threads.submit(() -> failureOf(first));
        assertTrue(gate.entered.await(10, TimeUnit.SECONDS));
        var secondResult = threads.submit(() -> failureOf(second));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (pool.getThreadsAwaitingConnection() == 0) {
          if (secondResult.isDone()) break; // it never needed the connection to finish
          assertTrue(System.nanoTime() < deadline, "Second request never queued for the pool");
          Thread.onSpinWait();
        }
        gate.release.countDown();
        return failures(firstResult, secondResult);
      } finally {
        gate.release.countDown();
      }
    }
  }

  /** Runs both requests at once; the test's own barriers decide how they interleave. */
  private List<Throwable> runTogether(Request first, Request second) throws Exception {
    try (var threads = Executors.newFixedThreadPool(2)) {
      var firstResult = threads.submit(() -> failureOf(first));
      var secondResult = threads.submit(() -> failureOf(second));
      return failures(firstResult, secondResult);
    }
  }

  @SafeVarargs
  private static List<Throwable> failures(Future<Throwable>... results) throws Exception {
    var failures = new ArrayList<Throwable>();
    for (var result : results) {
      Throwable failure = result.get(15, TimeUnit.SECONDS);
      if (failure != null) failures.add(failure);
    }
    return failures;
  }

  private static Throwable failureOf(Request request) {
    try {
      request.run();
      return null;
    } catch (Throwable failure) {
      return failure;
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

  private void delete(String id, String cue) {
    http().delete().uri("/api/titles/" + id + "/cues/" + cue).retrieve().toBodilessEntity();
  }

  private void refresh(String repository) {
    http().post().uri("/api/repositories/" + repository + "/refresh").retrieve().toBodilessEntity();
  }

  private void remove(String repository) {
    http().delete().uri("/api/repositories/" + repository).retrieve().toBodilessEntity();
  }

  private void add(Path folder) {
    http()
        .post()
        .uri("/api/repositories")
        .body(Map.of("localPath", folder.toString()))
        .retrieve()
        .toBodilessEntity();
  }

  private List<ContentRepository> repositories() {
    return http()
        .get()
        .uri("/api/repositories")
        .retrieve()
        .body(new ParameterizedTypeReference<List<ContentRepository>>() {});
  }

  private String repositoryOf(String titleId) {
    return catalogue.find(titleId).orElseThrow().location().repositoryId();
  }

  private int count(String tableAndFilter) {
    return jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Integer.class);
  }

  private static String real(Path path) throws Exception {
    return path.toRealPath().toString();
  }

  private static StudyDocument twoCueTitle(String id) {
    var title = document(id);
    var first = title.video().cues().getFirst();
    var second = first.withTiming(2.0, 3.0).withLine(s -> s.withId("second-cue"));
    return title.withVideo(v -> v.withCues(List.of(first, second)));
  }

  private void registerTitle(StudyDocument title) throws Exception {
    Path folder = Files.createTempDirectory(ROOT, "registered-");
    Files.write(
        folder.resolve("document.json"),
        new StudyDocumentCodec().encode(title).getBytes(StandardCharsets.UTF_8));
    add(folder);
  }
}
