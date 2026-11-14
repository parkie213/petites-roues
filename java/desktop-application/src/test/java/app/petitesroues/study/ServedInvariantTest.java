package app.petitesroues.study;

import static app.petitesroues.support.DocumentFixtures.comic;
import static app.petitesroues.support.DocumentFixtures.document;
import static app.petitesroues.support.SpecFixtures.newBubble;
import static app.petitesroues.support.SpecFixtures.newPage;
import static app.petitesroues.support.SpecFixtures.newRegion;
import static app.petitesroues.support.TestDocuments.catalogueEntry;
import static app.petitesroues.support.TestDocuments.edit;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.library.DocumentConflictException;
import app.petitesroues.library.DocumentImportService;
import app.petitesroues.library.persistence.AcceptedDocumentStore;
import app.petitesroues.model.document.ComicBox;
import app.petitesroues.model.document.ComicPage;
import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.editing.ComicBoxCreate;
import app.petitesroues.model.editing.ComicBoxUpdate;
import app.petitesroues.model.editing.ComicRegionInput;
import app.petitesroues.model.editing.ComicTextEdit;
import app.petitesroues.model.editing.MergeCues;
import app.petitesroues.model.editing.SplitCue;
import app.petitesroues.model.editing.UpdateCueTiming;
import app.petitesroues.model.editing.VideoCueEdit;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.review.CueChange;
import app.petitesroues.model.review.CueReviewRequest;
import app.petitesroues.model.review.ReviewCompletion;
import app.petitesroues.support.StudyTestServices;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The served copy's invariant on real SQLite: after every operation, the stored served bytes equal
 * a fresh replay of the pending rows over the accepted copy, every stored conflict flag equals that
 * replay's verdict for its row, and the stamp equals the accepted hash. Seeded random sequences of
 * every write path run over one video and one comic, and the invariant is checked after each step,
 * including steps that fail. The check reads the stored rows directly and never through {@link
 * ServedDocuments#current}, which would repair a violation.
 *
 * <p>Because every step also compares the stored bytes' decoding with the replayed document, this
 * covers {@code decode(encode(d)) == d} for every document the sequences produce.
 */
class ServedInvariantTest {
  private static final int STEPS = 120;

  @TempDir Path root;
  StudyTestServices services;
  JdbcTemplate jdbc;
  StudyDocumentCodec codec;
  DocumentStore store;
  AcceptedDocumentStore accepted;
  ReviewQueue queue;
  ServedDocuments servedDocuments;
  DocumentImportService imports;
  ReviewQueueService review;
  DocumentEditingService editing;
  CatalogueEntry entry;
  Random random;
  int step;
  final TreeMap<String, Integer> coverage = new TreeMap<>();

  @BeforeEach
  void setup() {
    services = StudyTestServices.create(root);
    jdbc = services.jdbc;
    codec = services.codec;
    store = services.documents;
    accepted = services.accepted;
    queue = services.queue;
    servedDocuments = services.served;
    imports = services.imports;
    review = services.review;
    editing = services.editing;
    entry = catalogueEntry(jdbc, "title", root);
    services.titles.saveTitle(entry);
  }

  @AfterEach
  void close() {
    services.close();
  }

  // The fixtures

  private StudyDocument video() {
    var cues = new ArrayList<VideoCue>();
    for (int i = 0; i < 4; i++)
      cues.add(
          new VideoCue(
              Line.of("cue-" + i, "Phrase " + i).withTranslation("Sentence " + i),
              i * 2.0,
              i * 2.0 + 1.5,
              null));
    return document("title").withVideo(v -> v.withCues(cues));
  }

  private StudyDocument comicBook() {
    var pages = new ArrayList<ComicPage>();
    for (int p = 0; p < 2; p++) {
      var boxes = new ArrayList<ComicBox>();
      for (int b = 0; b < 3; b++)
        boxes.add(
            newBubble(
                Line.of("line-" + p + "-" + b, "Bulle " + p + b).withTranslation("Bubble"),
                List.of(newRegion(.1 * b, .1 * p, .2, .2))));
      pages.add(newPage("page-" + p, "00" + p + ".jpg", null, boxes.toArray(ComicBox[]::new)));
    }
    return comic("title", "A comic", ReadingDirection.LTR, pages);
  }

  private void start(StudyDocument fixture, long seed) throws Exception {
    random = new Random(seed);
    step = 0;
    write(fixture);
    assertEquals(DocumentImportService.Result.IMPORTED, imports.importFile(entry.id()));
    assertInvariant("import of the fixture");
  }

  private void write(StudyDocument document) throws Exception {
    Files.createDirectories(entry.documentFile().getParent());
    Files.write(entry.documentFile(), codec.encode(document).getBytes(StandardCharsets.UTF_8));
  }

  // The invariant

  /** An independent replay: no use of ServedDocuments.replay, which the service shares. */
  private void assertInvariant(String after) {
    String context = "step " + step + " after " + after;
    var copy = accepted.find(entry.id()).orElseThrow();
    byte[] served =
        accepted
            .findServed(entry.id(), copy.sha256())
            .orElseThrow(() -> new AssertionError("no current served row, " + context));
    var rows = queue.read(entry);
    var expected = store.decode(copy.json());
    var verdicts = new ArrayList<String>();
    for (var row : rows) {
      if (row.command() == null) verdicts.add("review");
      else
        try {
          expected = DocumentEdits.apply(expected, row.command());
          verdicts.add("pending");
        } catch (IllegalArgumentException | NoSuchElementException conflicts) {
          verdicts.add("conflict");
        }
    }
    assertArrayEquals(
        codec.encode(expected).getBytes(StandardCharsets.UTF_8),
        served,
        "served bytes differ from a fresh replay, " + context);
    assertEquals(expected, store.decode(served), "decode(encode(d)) != d, " + context);
    assertEquals(
        verdicts, rows.stream().map(CueChange::state).toList(), "stored flags, " + context);
    assertEquals(
        copy.sha256(),
        jdbc.queryForObject(
            "SELECT accepted_sha256 FROM served_documents WHERE title_id=?",
            String.class,
            entry.id()),
        "stamp, " + context);
    verdicts.stream().filter("conflict"::equals).forEach(v -> count("conflict rows seen"));
  }

  private void count(String what) {
    coverage.merge(what, 1, Integer::sum);
  }

  // The sequences

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 3, 4, 5, 6})
  void everyStepOfARandomVideoSequenceKeepsTheServedCopyEqualToAReplay(long seed) throws Exception {
    start(video(), seed);
    tour(true);
    for (int i = 0; i < STEPS; i++) randomStep(true);
    assertCoverage();
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 3, 4, 5, 6})
  void everyStepOfARandomComicSequenceKeepsTheServedCopyEqualToAReplay(long seed) throws Exception {
    start(comicBook(), seed);
    tour(false);
    for (int i = 0; i < STEPS; i++) randomStep(false);
    assertCoverage();
  }

  /** Every operation once, in an order that makes each one meaningful, before the random steps. */
  private void tour(boolean video) throws Exception {
    check(anEdit(video));
    check(anEdit(video));
    check(noOp(video));
    check(invalid(video));
    check(comment(video));
    check(requestReview(video));
    check(claim());
    check(anEdit(video));
    check(complete(video, true));
    check(anEdit(video));
    check(discard());
    check(anEdit(video));
    check(complete(video, false));
    check(importChanged(video));
    check(loseServedCopy());
  }

  private void assertCoverage() {
    // A sequence that never reached a kind of step would pass without testing it.
    for (var required :
        List.of(
            "edit",
            "no-op",
            "invalid",
            "comment",
            "review request",
            "discard",
            "claim",
            "complete applied",
            "complete not_adopted",
            "import changed",
            "lost served copy"))
      assertTrue(coverage.getOrDefault(required, 0) > 0, "never ran: " + required + " " + coverage);
  }

  private void randomStep(boolean video) throws Exception {
    int choice = random.nextInt(100);
    String what;
    if (choice < 40) what = anEdit(video);
    else if (choice < 48) what = noOp(video);
    else if (choice < 54) what = invalid(video);
    else if (choice < 60) what = comment(video);
    else if (choice < 65) what = requestReview(video);
    else if (choice < 70) what = discard();
    else if (choice < 78) what = claim();
    else if (choice < 86) what = complete(video, random.nextInt(3) != 0);
    else if (choice < 92) what = importChanged(video);
    else what = loseServedCopy();
    check(what);
  }

  /** Counts the step and checks the invariant after it. */
  private void check(String what) {
    step++;
    count(what);
    assertInvariant(what);
  }

  private String anEdit(boolean video) throws Exception {
    return video ? videoEdit() : comicEdit();
  }

  // Edits

  private StudyDocument served() {
    return review.document(entry);
  }

  private String videoEdit() throws Exception {
    var cues = served().video().cues();
    var cue = cues.isEmpty() ? null : cues.get(random.nextInt(cues.size()));
    int kind = cue == null ? 3 : random.nextInt(8);
    switch (kind) {
      case 0, 1 -> {
        var change =
            new VideoCueEdit(
                cue.start(),
                cue.end(),
                cue.line().source() + " " + step,
                "Translation " + step,
                List.of("Note " + step),
                false,
                null,
                null);
        editing.editCue(entry.id(), cue.line().id(), change);
      }
      case 2 -> {
        double start = random.nextInt(200) / 10.0;
        editing.saveCueTiming(
            entry.id(), List.of(new UpdateCueTiming(cue.line().id(), start, start + 1.25)));
      }
      case 3 -> {
        double start = random.nextInt(200) / 10.0;
        editing.addCue(
            entry.id(),
            new VideoCueEdit(
                start, start + 1, "Added " + step, null, List.of(), false, null, null));
      }
      case 4 ->
          editing.splitCue(
              entry.id(),
              cue.line().id(),
              new SplitCue((cue.start() + cue.end()) / 2, "First " + step, "Second " + step));
      case 5 -> {
        int at = cues.indexOf(cue);
        if (at + 1 < cues.size())
          editing.mergeCues(
              entry.id(), new MergeCues(List.of(cue.line().id(), cues.get(at + 1).line().id())));
      }
      case 6 -> editing.deleteCue(entry.id(), cue.line().id());
      default -> editing.suggestCorrection(entry.id(), cue.line().id(), "Heard " + step);
    }
    return "edit";
  }

  private String comicEdit() throws Exception {
    var boxes = served().comic().boxes();
    var box = boxes.isEmpty() ? null : boxes.get(random.nextInt(boxes.size()));
    int kind = box == null ? 2 : random.nextInt(5);
    switch (kind) {
      case 0, 1 -> {
        var line = box.lines().getFirst();
        editing.editComicText(
            entry.id(), line.id(), new ComicTextEdit(line.source(), line.source() + step));
      }
      case 2 ->
          editing.createComicBox(
              entry.id(),
              new ComicBoxCreate(
                  "page-" + random.nextInt(2),
                  "New " + step,
                  new ComicRegionInput(random.nextInt(50) / 100.0, .5, .3, .3)));
      case 3 -> {
        var from = box.regions().stream().map(r -> input(r.x(), r.y(), r.width(), r.height()));
        editing.placeComicBox(
            entry.id(),
            box.id(),
            new ComicBoxUpdate(
                from.toList(), List.of(input(.05 + step % 20 / 100.0, .6, .25, .25)), null));
      }
      default -> editing.deleteComicBox(entry.id(), box.id());
    }
    return "edit";
  }

  private static ComicRegionInput input(double x, double y, double width, double height) {
    return new ComicRegionInput(x, y, width, height);
  }

  /**
   * An edit that changes nothing: its encoded result equals the stored bytes, so it is not queued.
   */
  private String noOp(boolean video) throws Exception {
    int before = queue.read(entry).size();
    byte[] bytes = review.servedBytes(entry);
    if (video) {
      var cues = served().video().cues();
      if (cues.isEmpty()) return "no-op skipped";
      var cue = cues.get(random.nextInt(cues.size()));
      editing.editCue(entry.id(), cue.line().id(), edit(cue));
    } else {
      var boxes = served().comic().boxes();
      if (boxes.isEmpty()) return "no-op skipped";
      var line = boxes.get(random.nextInt(boxes.size())).lines().getFirst();
      editing.editComicText(entry.id(), line.id(), new ComicTextEdit(line.source(), line.source()));
    }
    assertEquals(before, queue.read(entry).size(), "a no-op edit was queued");
    assertArrayEquals(bytes, review.servedBytes(entry));
    return "no-op";
  }

  private String invalid(boolean video) {
    int before = queue.read(entry).size();
    byte[] bytes = review.servedBytes(entry);
    if (video) {
      assertThrows(
          RuntimeException.class,
          () ->
              editing.editCue(
                  entry.id(),
                  "no-such-cue",
                  edit(new VideoCue(Line.of("x", "x"), 0.0, 1.0, null))));
      assertThrows(
          RuntimeException.class,
          () ->
              editing.saveCueTiming(
                  entry.id(), List.of(new UpdateCueTiming("no-such-cue", 1.0, 2.0))));
    } else {
      assertThrows(
          RuntimeException.class,
          () -> editing.editComicText(entry.id(), "no-such-line", new ComicTextEdit("a", "b")));
      assertThrows(
          RuntimeException.class,
          () ->
              editing.createComicBox(
                  entry.id(), new ComicBoxCreate("no-page", "x", input(.1, .1, .1, .1))));
    }
    assertEquals(before, queue.read(entry).size());
    assertArrayEquals(bytes, review.servedBytes(entry));
    return "invalid";
  }

  private String comment(boolean video) throws Exception {
    String target = anyLineId(video);
    if (target == null) return "comment skipped";
    byte[] bytes = review.servedBytes(entry);
    editing.comment(entry.id(), target, "Comment " + step);
    assertArrayEquals(bytes, review.servedBytes(entry), "a comment changed the served copy");
    return "comment";
  }

  private String requestReview(boolean video) {
    String target = anyLineId(video);
    if (target == null) return "review request skipped";
    byte[] bytes = review.servedBytes(entry);
    review.requestReview(entry.id(), List.of(new CueReviewRequest(target, "Check " + step)));
    assertArrayEquals(bytes, review.servedBytes(entry));
    return "review request";
  }

  private String anyLineId(boolean video) {
    var doc = served();
    var ids =
        video
            ? doc.video().cues().stream().map(c -> c.line().id()).toList()
            : doc.comic().boxes().stream().flatMap(b -> b.lines().stream()).map(Line::id).toList();
    return ids.isEmpty() ? null : ids.get(random.nextInt(ids.size()));
  }

  // Queue operations

  private String discard() {
    var sequences = queue.read(entry).stream().map(CueChange::sequence).toList();
    if (!sequences.isEmpty() && random.nextBoolean())
      assertThrows(
          IllegalArgumentException.class,
          () -> review.discard(entry.id(), sequences.subList(1, sequences.size())),
          "a stale queue must be refused");
    review.discard(entry.id(), sequences);
    assertTrue(queue.read(entry).isEmpty());
    assertTrue(queue.openBatch(entry).isEmpty(), "discard closes the open batch");
    return "discard";
  }

  /** Claims a small batch, so later edits and other batches are left out of its document. */
  private String claim() throws Exception {
    var batch = review.claim(entry.id(), 1 + random.nextInt(3));
    if (batch.isEmpty()) return "claim skipped";
    var view = batch.get();
    // The batch document is the accepted copy plus exactly the batch's edits.
    var expected = store.decode(accepted.find(entry.id()).orElseThrow().json());
    for (var change : view.changes())
      if (change.command() != null && !"conflict".equals(change.state()))
        expected = DocumentEdits.apply(expected, change.command());
    assertEquals(expected, view.document(), "batch document");
    return "claim";
  }

  /** Completes the open batch (claiming one if needed), in one of the ways an agent can. */
  private String complete(boolean video, boolean adopt) throws Exception {
    var open = queue.openBatch(entry);
    if (open.isEmpty()) {
      if (queue.read(entry).isEmpty()) return "complete skipped";
      review.claim(entry.id(), 1 + random.nextInt(4));
      open = queue.openBatch(entry);
    }
    long batchId = open.orElseThrow().id();
    var batch = review.claim(entry.id(), 50).orElseThrow();
    String kind;
    if (!adopt) {
      // The file is unchanged: only not_adopted may complete document edits.
      kind = "complete not_adopted";
      boolean writes =
          batch.changes().stream()
              .anyMatch(c -> c.command() != null && c.command().changesDocument());
      if (writes)
        assertThrows(
            IllegalArgumentException.class,
            () -> review.complete(entry.id(), batchId, new ReviewCompletion("applied", null)));
      review.complete(entry.id(), batchId, new ReviewCompletion("not_adopted", null));
    } else {
      kind = "complete applied";
      var written = batch.document().withTitle("Written at step " + step);
      if (random.nextBoolean()) written = break_(written, video);
      write(written);
      review.complete(entry.id(), batchId, new ReviewCompletion("applied", null));
      assertArrayEquals(
          Files.readAllBytes(entry.documentFile()),
          accepted.find(entry.id()).orElseThrow().json(),
          "the written file is the accepted copy");
    }
    // A repeated completion of the closed batch succeeds while the file is the accepted copy.
    if (random.nextBoolean())
      review.complete(entry.id(), batchId, new ReviewCompletion("applied", null));
    assertTrue(queue.openBatch(entry).isEmpty());
    return kind;
  }

  /** The file as an agent could leave it: the first cue or box renamed away from queued edits. */
  private StudyDocument break_(StudyDocument document, boolean video) {
    if (video)
      return document.video().cues().isEmpty()
          ? document
          : document.withVideo(
              v -> v.withCue(0, c -> c.withLine(l -> l.withId("renamed-" + step))));
    return document.comic().boxes().isEmpty()
        ? document
        : document.withComic(
            c ->
                c.withPage(
                    0,
                    p ->
                        p.withBoxes(
                            p.boxes().subList(Math.min(1, p.boxes().size()), p.boxes().size()))));
  }

  /** Refresh or the import endpoint accepting a changed file, or refusing it during a batch. */
  private String importChanged(boolean video) throws Exception {
    var current = store.decode(accepted.find(entry.id()).orElseThrow().json());
    var changed = current.withTitle("Imported at step " + step);
    if (random.nextBoolean()) changed = break_(changed, video);
    write(changed);
    var open = queue.openBatch(entry);
    if (open.isPresent()) {
      assertThrows(DocumentConflictException.class, () -> review.importFile(entry.id()));
      Files.write(entry.documentFile(), accepted.find(entry.id()).orElseThrow().json());
      return "import refused";
    }
    assertEquals(DocumentImportService.Result.IMPORTED, review.importFile(entry.id()));
    assertEquals(DocumentImportService.Result.UNCHANGED, review.importFile(entry.id()));
    return "import changed";
  }

  /** A lost or stale served row is rebuilt by the next read, or by the startup rebuild. */
  private String loseServedCopy() {
    switch (random.nextInt(3)) {
      case 0 -> jdbc.update("DELETE FROM served_documents");
      case 1 -> jdbc.update("UPDATE served_documents SET accepted_sha256='stale'");
      default -> {
        jdbc.update("DELETE FROM served_documents");
        review.rebuildServedCopies();
        return "lost served copy";
      }
    }
    review.document(entry);
    return "lost served copy";
  }

  // Scenarios the random sequences reach only by chance

  @Test
  void aConflictFollowedByAnImportThatResolvesItClearsTheFlag() throws Exception {
    var original = video();
    start(original, 7);
    step = 1;
    editing.editCue(
        entry.id(),
        "cue-1",
        edit(original.video().cues().get(1).withLine(l -> l.withSource("Edited"))));
    assertEquals(List.of("pending"), states());

    write(original.withVideo(v -> v.withCue(1, c -> c.withLine(l -> l.withId("renamed")))));
    review.importFile(entry.id());
    step = 2;
    assertInvariant("a rename that orphans the edit");
    assertEquals(List.of("conflict"), states());
    assertEquals("Phrase 1", served().video().cues().get(1).line().source());

    write(original.withTitle("Back again"));
    review.importFile(entry.id());
    step = 3;
    assertInvariant("an import that restores the cue");
    assertEquals(List.of("pending"), states());
    assertEquals("Edited", served().video().cues().get(1).line().source());
  }

  @Test
  void completingWithAnUnchangedFileClosesTheRowsAndLeavesTheOthersServed() throws Exception {
    start(video(), 8);
    step = 1;
    editing.deleteCue(entry.id(), "cue-0");
    var batch = review.claim(entry.id(), 1).orElseThrow();
    editing.deleteCue(entry.id(), "cue-1");
    step = 2;
    assertInvariant("a later edit");
    review.complete(entry.id(), batch.batch(), new ReviewCompletion("not_adopted", null));
    step = 3;
    assertInvariant("completion with an unchanged file");
    assertEquals(
        List.of("cue-0", "cue-2", "cue-3"),
        served().video().cues().stream().map(c -> c.line().id()).toList());
    assertEquals(1, queue.read(entry).size());
  }

  private List<String> states() {
    return queue.read(entry).stream().map(CueChange::state).toList();
  }
}
