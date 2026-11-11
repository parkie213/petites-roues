package com.petitesroues.study;

import static com.petitesroues.support.DocumentFixtures.document;
import static com.petitesroues.support.SpecFixtures.*;
import static com.petitesroues.support.TestDocuments.catalogueEntry;
import static com.petitesroues.support.TestDocuments.edit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.documents.StudyDocumentReader;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.model.document.Line;
import com.petitesroues.model.document.PhraseAlignment;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoCue;
import com.petitesroues.model.editing.MergeCues;
import com.petitesroues.model.editing.SplitCue;
import com.petitesroues.model.editing.UpdateCueTiming;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.review.CueChange;
import com.petitesroues.model.review.ReviewCompletion;
import com.petitesroues.support.DatabaseFixtures;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Player edits are queued and replayed over the file; agents write them through batches. */
class DocumentEditingServiceTest {
  @TempDir Path root;
  DocumentEditingService editing;
  ReviewQueueService served;
  CatalogueEntry entry;
  ReviewQueue log;
  StudyDocumentCodec codec;

  @BeforeEach
  void setup() throws Exception {
    var mapper = JsonMapper.builder().build();
    codec = new StudyDocumentCodec();
    var source = new SingleConnectionDataSource("jdbc:sqlite:" + root.resolve("db.sqlite"), true);
    DatabaseFixtures.migrate(source);
    var jdbc = new JdbcTemplate(source);
    entry = catalogueEntry(jdbc, "example", root);
    var titles = new CatalogueStore(jdbc);
    titles.saveTitle(entry);
    write(document(entry.id()));
    log = spy(new ReviewQueue(jdbc, mapper));
    served =
        new ReviewQueueService(
            titles,
            new DocumentStore(new StudyDocumentReader()),
            log,
            new TransactionTemplate(new DataSourceTransactionManager(source)));
    editing = new DocumentEditingService(titles, served, log);
  }

  /** Writes the file as an agent or preparation script would. */
  private void write(StudyDocument document) throws Exception {
    Files.createDirectories(entry.documentFile().getParent());
    Files.write(entry.documentFile(), codec.encode(document).getBytes(StandardCharsets.UTF_8));
  }

  private StudyDocument readVideo() throws Exception {
    return served.document(entry);
  }

  private List<CueChange> history() throws Exception {
    return served.pending(entry.id());
  }

  @Test
  void editsAreQueuedAndNeverWriteTheFile() throws Exception {
    byte[] original = Files.readAllBytes(entry.documentFile());
    var cue = readVideo().video().cues().get(0).withLine(s -> s.withSource("Bonsoir !"));
    editing.editCue(entry.id(), "stable-cue", edit(cue));
    editing.saveCueTiming(entry.id(), List.of(new UpdateCueTiming("stable-cue", .2, .8)));
    editing.suggestCorrection(entry.id(), "stable-cue", "Bonne soirée ?");

    assertArrayEquals(original, Files.readAllBytes(entry.documentFile()));
    var saved = readVideo().video().cues().get(0);
    assertEquals("Bonsoir !", saved.line().source());
    assertEquals(.2, saved.start());
    assertTrue(saved.listeningCheck().contains("Bonne soirée ?"));
    assertEquals(
        List.of("edit", "timing", "suggestion"), history().stream().map(CueChange::kind).toList());
    assertTrue(history().stream().allMatch(c -> "pending".equals(c.state())));
  }

  @Test
  void overlappingTimingEditsAreQueuedAsTimingEdits() throws Exception {
    write(withCue(readVideo(), new VideoCue(Line.of("second", "Bonsoir"), 2.0, 3.0, null)));
    editing.saveCueTiming(entry.id(), List.of(new UpdateCueTiming("stable-cue", 0.0, 2.5)));
    assertEquals(2.5, readVideo().video().cues().getFirst().end());
    assertEquals("timing", history().getFirst().kind());
    assertEquals(List.of("stable-cue"), history().getFirst().cueIds());
  }

  @Test
  void suggestedHearingPreservesDialogueAndQueuesReview() throws Exception {
    var original = readVideo().video().cues().getFirst();
    var result = editing.suggestCorrection(entry.id(), original.line().id(), "Might be Où est…");
    var saved = result.video().cues().get(0);
    assertEquals(original.line().source(), saved.line().source());
    assertEquals(original.start(), saved.start());
    assertEquals(original.end(), saved.end());
    assertEquals(original.line().translation(), saved.line().translation());
    assertEquals(original.line().alignment(), saved.line().alignment());
    assertTrue(saved.listeningCheck().contains("Might be Où est…"));
    assertEquals("suggestion", history().getFirst().kind());
    assertEquals(List.of(saved.line().id()), history().getFirst().cueIds());
    assertEquals("Might be Où est…", history().getFirst().suggestion());
    assertEquals(1, history().size());
  }

  /** The document with {@code cue} appended to its video cues. */
  private static StudyDocument withCue(StudyDocument document, VideoCue cue) {
    var cues = new ArrayList<>(document.video().cues());
    cues.add(cue);
    return document.withVideo(v -> v.withCues(cues));
  }

  private PhraseAlignment alignment(VideoCue cue) {
    return newAlignment(
        List.of(cue.line().source()),
        List.of(cue.line().translation()),
        List.of(newLink(List.of(0), List.of(0), "A complete greeting.")));
  }

  private VideoCue installAlignment() throws Exception {
    write(
        readVideo()
            .withVideo(
                v -> v.withCue(0, cue -> cue.withLine(s -> s.withAlignment(alignment(cue))))));
    return readVideo().video().cues().getFirst();
  }

  @Test
  void directFileAlignmentIsLoadedAndSurvivesTimingUpdates() throws Exception {
    VideoCue cue = installAlignment();
    assertNotNull(cue.line().alignment());
    assertTrue(history().isEmpty());
    var originalAlignment = cue.line().alignment();
    editing.saveCueTiming(entry.id(), List.of(new UpdateCueTiming(cue.line().id(), .2, 1.0)));
    assertEquals(originalAlignment, readVideo().video().cues().getFirst().line().alignment());
  }

  @Test
  void textAndEnglishEditsInvalidateLinksAndQueueTheCueId() throws Exception {
    var cue = installAlignment().withLine(s -> s.withTranslation("Hi!"));
    var edited = editing.editCue(entry.id(), cue.line().id(), edit(cue)).video().cues().getFirst();
    assertFalse(edited.line().alignment() != null);
    assertEquals(List.of(cue.line().id()), history().getFirst().cueIds());
    assertEquals("edit", history().getFirst().kind());
    var resaid = edited.withLine(s -> s.withSource("Salut !"));
    editing.editCue(entry.id(), resaid.line().id(), edit(resaid));
  }

  @Test
  void splitAndMergeInvalidateAllResultingLinksAndPreserveChecks() throws Exception {
    var cue = installAlignment().withListeningCheck("Audio still uncertain");
    editing.editCue(entry.id(), cue.line().id(), edit(cue));
    var split = editing.splitCue(entry.id(), cue.line().id(), new SplitCue(.5, "Bonjour", "!"));
    assertTrue(split.video().cues().stream().allMatch(c -> c.line().alignment() == null));
    assertTrue(
        split.video().cues().stream()
            .allMatch(c -> "Audio still uncertain".equals(c.listeningCheck())));
    var merged =
        editing
            .mergeCues(
                entry.id(),
                new MergeCues(split.video().cues().stream().map(c -> c.line().id()).toList()))
            .video()
            .cues()
            .getFirst();
    assertFalse(merged.line().alignment() != null);
    assertEquals("Audio still uncertain", merged.listeningCheck());
    assertEquals(
        split.video().cues().stream().map(c -> c.line().id()).toList(),
        history().stream()
            .filter(e -> "split".equals(e.kind()))
            .findFirst()
            .orElseThrow()
            .cueIds());
  }

  @Test
  void deletionQueuesOnlyTheRemovedCueId() throws Exception {
    var cue = installAlignment();
    editing.deleteCue(entry.id(), cue.line().id());
    var deletion = history().getFirst();
    assertEquals(List.of(cue.line().id()), deletion.cueIds());
    assertEquals("delete", deletion.kind());
    assertTrue(readVideo().video().cues().isEmpty());
    assertNull(deletion.suggestion());
  }

  @Test
  void mergeCombinesConsecutiveCuesAndQueuesThemInPlaybackOrder() throws Exception {
    var split = editing.splitCue(entry.id(), "stable-cue", new SplitCue(.5, "Bonjour", "George !"));
    var first = split.video().cues().get(0);
    var second = split.video().cues().get(1);

    editing.mergeCues(entry.id(), new MergeCues(List.of(second.line().id(), first.line().id())));

    var saved = readVideo().video().cues();
    assertEquals(1, saved.size());
    assertEquals(first.line().id(), saved.getFirst().line().id());
    assertEquals("Bonjour George !", saved.getFirst().line().source());
    assertEquals(.1, saved.getFirst().start());
    assertEquals(.9, saved.getFirst().end());
    var event = history().getLast();
    assertEquals("merge", event.kind());
    assertEquals(List.of(first.line().id(), second.line().id()), event.cueIds());
  }

  @Test
  void mergeRejectsSelectionsThatSkipACue() throws Exception {
    var split = editing.splitCue(entry.id(), "stable-cue", new SplitCue(.5, "Bonjour", "George !"));
    var added = document("unused").video().cues().getFirst().withTiming(2.0, 3.0);
    var current = editing.addCue(entry.id(), edit(added));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            editing.mergeCues(
                entry.id(),
                new MergeCues(
                    List.of(
                        split.video().cues().getFirst().line().id(),
                        current.video().cues().get(2).line().id()))));

    assertEquals(3, readVideo().video().cues().size());
    assertEquals(2, history().size());
  }

  @Test
  void failedQueueAppendLeavesTheServedDocumentUnchanged() throws Exception {
    doThrow(new IllegalStateException("Disk full")).when(log).append(any(), any());
    VideoCue cue = readVideo().video().cues().getFirst().withLine(s -> s.withSource("Bonsoir"));
    assertThrows(
        IllegalStateException.class, () -> editing.editCue(entry.id(), cue.line().id(), edit(cue)));
    assertEquals("Bonjour !", readVideo().video().cues().getFirst().line().source());
    assertTrue(history().isEmpty());
  }

  @Test
  void splitOutsideTheCueQueuesNothing() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> editing.splitCue(entry.id(), "stable-cue", new SplitCue(5, "First", "Second")));
    assertTrue(history().isEmpty());
  }

  @Test
  void splitKeepsInlineTimingCorrections() throws Exception {
    var split =
        editing.splitCue(entry.id(), "stable-cue", new SplitCue(.5, "Bon", "jour", 0.0, 1.0));
    assertEquals(0, split.video().cues().getFirst().start());
    assertEquals(.5, split.video().cues().getFirst().end());
    assertEquals(.5, split.video().cues().get(1).start());
    assertEquals(1, split.video().cues().get(1).end());
    assertEquals(
        split.video().cues().stream().map(c -> c.line().id()).toList(),
        history().getFirst().cueIds());
    assertEquals(split, readVideo());
  }

  @Test
  void notesOnlyEditsAreQueuedAndKeepLinks() throws Exception {
    installAlignment();
    VideoCue cue =
        readVideo()
            .video()
            .cues()
            .getFirst()
            .withLine(s -> s.withNotes(List.of("Updated teaching point")));

    editing.editCue(entry.id(), cue.line().id(), edit(cue));

    var saved = readVideo().video().cues().getFirst().line();
    assertEquals(List.of("Updated teaching point"), saved.notes());
    assertTrue(saved.alignment() != null);
    assertEquals("edit", history().getFirst().kind());
  }

  @Test
  void unchangedEditsAreNotQueued() throws Exception {
    var cue = readVideo().video().cues().getFirst();
    editing.editCue(entry.id(), cue.line().id(), edit(cue));
    editing.saveCueTiming(
        entry.id(), List.of(new UpdateCueTiming("stable-cue", cue.start(), cue.end())));
    assertTrue(history().isEmpty());
  }

  @Test
  void timingEditsKeepCuesInChronologicalOrder() throws Exception {
    write(withCue(readVideo(), new VideoCue(Line.of("second-cue", "Salut"), 2.0, 3.0, null)));
    editing.saveCueTiming(entry.id(), List.of(new UpdateCueTiming("stable-cue", 4.0, 5.0)));

    assertEquals("second-cue", readVideo().video().cues().getFirst().line().id());
    assertEquals(4.0, readVideo().video().cues().getLast().start());
  }

  @Test
  void splitKeepsTheFirstIdentityAndStartsTheSecondWithoutAnnotations() throws Exception {
    editing.splitCue(entry.id(), "stable-cue", new SplitCue(.5, "Bonjour", "George !"));

    var cues = readVideo().video().cues();
    assertEquals(2, cues.size());
    assertEquals("stable-cue", cues.getFirst().line().id());
    assertNotEquals("stable-cue", cues.get(1).line().id());
    assertEquals("Hello!", cues.getFirst().line().translation());
    assertFalse(cues.get(1).line().translation() != null);
    assertTrue(cues.get(1).line().notes().isEmpty());
    var event = history().getFirst();
    assertEquals("split", event.kind());
    assertEquals(cues.stream().map(c -> c.line().id()).toList(), event.cueIds());
  }

  @Test
  void additionKeepsItsAssignedIdentityAcrossReplays() throws Exception {
    VideoCue cue = document("unused").video().cues().getFirst().withTiming(2.0, 3.0);

    var returned = editing.addCue(entry.id(), edit(cue)).video().cues().get(1);

    var added = readVideo().video().cues().get(1);
    assertEquals(returned, added);
    assertEquals(added, readVideo().video().cues().get(1));
    assertNotEquals("stable-cue", added.line().id());
    var event = history().getFirst();
    assertEquals("add", event.kind());
    assertEquals(List.of(added.line().id()), event.cueIds());
  }

  @Test
  void textCorrectionRetainsTheListeningCheck() throws Exception {
    write(
        document(entry.id())
            .withVideo(v -> v.withCue(0, c -> c.withListeningCheck("Check the greeting."))));
    VideoCue correction =
        readVideo().video().cues().getFirst().withLine(s -> s.withSource("Bonsoir !"));

    editing.editCue(entry.id(), correction.line().id(), edit(correction));

    assertEquals("Check the greeting.", readVideo().video().cues().getFirst().listeningCheck());
  }

  @Test
  void splitRetainsTheListeningCheckOnBothParts() throws Exception {
    write(
        document(entry.id())
            .withVideo(v -> v.withCue(0, c -> c.withListeningCheck("Check the greeting."))));

    editing.splitCue(entry.id(), "stable-cue", new SplitCue(.5, "Bonjour", "George !"));

    assertEquals(
        List.of("Check the greeting.", "Check the greeting."),
        readVideo().video().cues().stream().map(c -> c.listeningCheck()).toList());
  }

  @Test
  void batchEditsStopReplayingOnceTheAgentWritesTheFile() throws Exception {
    var cue = readVideo().video().cues().get(0).withLine(s -> s.withSource("Bonsoir !"));
    editing.editCue(entry.id(), "stable-cue", edit(cue));
    var batch = served.claim(entry.id(), 50).orElseThrow();
    assertEquals(1, batch.changes().size());
    assertEquals(readVideo(), batch.document());

    var agent =
        batch
            .document()
            .withVideo(
                v -> v.withCue(0, c -> c.withLine(st -> st.withTranslation("Good evening!"))));
    write(agent);

    var saved = readVideo().video().cues().get(0).line();
    assertEquals("Bonsoir !", saved.source());
    assertEquals("Good evening!", saved.translation());
    assertEquals("written", history().getFirst().state());
    served.complete(entry.id(), batch.batch(), new ReviewCompletion(null, null));
    assertTrue(history().isEmpty());
    assertEquals("applied", served.history(entry.id()).getFirst().outcome());
    assertEquals(agent, readVideo());
  }

  @Test
  void editsAfterTheClaimReplayOverTheAgentsFile() throws Exception {
    editing.saveCueTiming(entry.id(), List.of(new UpdateCueTiming("stable-cue", .2, .8)));
    var batch = served.claim(entry.id(), 50).orElseThrow();
    editing.suggestCorrection(entry.id(), "stable-cue", "Bonne nuit ?");
    assertFalse(batch.document().video().cues().get(0).listeningCheck() != null);
    assertEquals(batch, served.claim(entry.id(), 50).orElseThrow());

    write(batch.document());
    served.complete(entry.id(), batch.batch(), null);

    var saved = readVideo().video().cues().get(0);
    assertEquals(.2, saved.start());
    assertTrue(saved.listeningCheck().contains("Bonne nuit ?"));
    assertEquals(List.of("suggestion"), history().stream().map(CueChange::kind).toList());
  }

  @Test
  void completingUnwrittenEditsIsRejectedUnlessNotAdopted() throws Exception {
    editing.saveCueTiming(entry.id(), List.of(new UpdateCueTiming("stable-cue", .2, .8)));
    var batch = served.claim(entry.id(), 50).orElseThrow();
    assertThrows(
        IllegalArgumentException.class,
        () -> served.complete(entry.id(), batch.batch(), new ReviewCompletion("applied", null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            served.complete(entry.id(), batch.batch(), new ReviewCompletion("invented", Map.of())));
    long sequence = batch.changes().getFirst().sequence();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            served.complete(
                entry.id(),
                batch.batch(),
                new ReviewCompletion(null, Map.of(sequence + 1, "applied"))));
    assertEquals(1, history().size());

    served.complete(
        entry.id(), batch.batch(), new ReviewCompletion(null, Map.of(sequence, "not_adopted")));
    assertTrue(history().isEmpty());
    assertEquals(0.1, readVideo().video().cues().get(0).start());
    assertThrows(
        java.util.NoSuchElementException.class,
        () -> served.complete(entry.id(), batch.batch(), null));
  }

  @Test
  void editsThatNoLongerApplyAreSkippedAsConflicts() throws Exception {
    editing.deleteCue(entry.id(), "stable-cue");
    write(
        document(entry.id())
            .withVideo(v -> v.withCue(0, c -> c.withLine(s -> s.withId("renamed")))));

    assertEquals("conflict", history().getFirst().state());
    assertEquals("renamed", readVideo().video().cues().get(0).line().id());
  }

  @Test
  void claimingReturnsNothingWhenNothingIsPendingAndDiscardRevertsEdits() throws Exception {
    assertTrue(served.claim(entry.id(), 50).isEmpty());
    editing.deleteCue(entry.id(), "stable-cue");
    assertTrue(readVideo().video().cues().isEmpty());
    served.discard(entry.id(), history().stream().map(CueChange::sequence).toList());
    assertEquals(1, readVideo().video().cues().size());
    assertTrue(served.claim(entry.id(), 50).isEmpty());
  }

  @Test
  void commentsAreQueuedWithoutChangingTheDocumentOrFile() throws Exception {
    byte[] original = Files.readAllBytes(entry.documentFile());
    var before = readVideo();
    var served = editing.comment(entry.id(), "stable-cue", "  'isn't fun' → 'it's not funny'?  ");
    assertEquals(before, served);
    assertEquals(before, readVideo());
    assertArrayEquals(original, Files.readAllBytes(entry.documentFile()));
    var comment = history().getFirst();
    assertEquals("comment", comment.kind());
    assertEquals(List.of("stable-cue"), comment.cueIds());
    assertEquals("'isn't fun' → 'it's not funny'?", comment.suggestion());
    assertEquals("pending", comment.state());
    assertThrows(
        IllegalArgumentException.class, () -> editing.comment(entry.id(), "stable-cue", " "));
    assertThrows(
        java.util.NoSuchElementException.class,
        () -> editing.comment(entry.id(), "missing", "Text"));
    assertEquals(1, history().size());
  }

  @Test
  void aBatchOfCommentsCompletesWithoutRewritingTheFile() throws Exception {
    editing.comment(entry.id(), "stable-cue", "Looks wrong?");
    var batch = served.claim(entry.id(), 50).orElseThrow();
    served.complete(entry.id(), batch.batch(), new ReviewCompletion("already_correct", null));
    assertTrue(history().isEmpty());
    assertEquals("already_correct", served.history(entry.id()).getFirst().outcome());
  }
}
