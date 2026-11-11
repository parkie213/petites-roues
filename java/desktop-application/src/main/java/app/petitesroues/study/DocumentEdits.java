package app.petitesroues.study;

import app.petitesroues.model.ChunkSeparator;
import app.petitesroues.model.document.ComicBox;
import app.petitesroues.model.document.ComicDocument;
import app.petitesroues.model.document.ComicRegion;
import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.StudyDocumentValidator;
import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.document.VideoDocument;
import app.petitesroues.model.editing.ComicRegionInput;
import app.petitesroues.model.editing.DocumentEdit;
import app.petitesroues.model.editing.DocumentEdit.*;
import app.petitesroues.model.editing.UpdateCueTiming;
import app.petitesroues.model.editing.VideoCueEdit;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Applies one {@link DocumentEdit} to a document, with no I/O. The same function accepts a new
 * player edit and replays queued edits over the file, so both always agree. Invalid or no longer
 * applicable edits throw {@link IllegalArgumentException} or {@link NoSuchElementException}; the
 * input is immutable and never changes. Changed results are validated and video cues kept in
 * playback order; an edit that changes nothing (a comment, a review request) returns the input as
 * it is, which is valid because documents are validated when decoded.
 *
 * <p>Edits keep stable identities: wording changes clear phrase links, timing and placement edits
 * keep annotations.
 */
public final class DocumentEdits {
  private DocumentEdits() {}

  public static StudyDocument apply(StudyDocument document, DocumentEdit edit) {
    var result =
        switch (edit) {
          case Timing timing -> timing(video(document), timing.edits());
          case Suggestion suggestion -> suggest(video(document), suggestion);
          case Comment comment ->
              requireNote(document, comment.cueId(), comment.text(), "a comment");
          case ReviewRequest request ->
              requireNote(document, request.cueId(), request.text(), "what to review");
          case EditVideoCue cue -> editCue(video(document), cue.cueId(), cue.edit());
          case AddVideoCue cue -> addCue(video(document), cue);
          case SplitVideoCue split -> split(video(document), split);
          case MergeVideoCues merge -> merge(video(document), merge.ids());
          case DeleteVideoCue cue ->
              withVideoCues(document, remove(cues(video(document)), index(document, cue.cueId())));
          case EditComicText text -> editComicText(comic(document), text);
          case CreateComicBox create -> createComicBox(comic(document), create);
          case PlaceComicBox place -> placeComicBox(comic(document), place);
          case DeleteComicBox box -> deleteComicBox(comic(document), box);
        };
    // Comments and review requests change nothing. The input was validated when it was decoded or
    // last edited, so checking it again would only repeat that work, once per queued request.
    if (result == document) return document;
    if (result.body() instanceof VideoDocument)
      result =
          withVideoCues(
              result,
              result.video().cues().stream()
                  .sorted(Comparator.comparingDouble(VideoCue::start))
                  .toList());
    StudyDocumentValidator.validate(result);
    return result;
  }

  /** Validates the entire timing batch before applying it; annotation evidence is retained. */
  private static StudyDocument timing(StudyDocument document, List<UpdateCueTiming> edits) {
    if (edits == null) throw new IllegalArgumentException("Expected timing edit array");
    Set<String> ids = new HashSet<>();
    for (var edit : edits) {
      if (edit == null || !ids.add(edit.id()))
        throw new IllegalArgumentException("Duplicate or null timing edit");
      index(document, edit.id());
      validateTiming(edit.start(), edit.end());
    }
    var cues = new ArrayList<>(cues(document));
    for (var edit : edits) {
      int index = index(document, edit.id());
      cues.set(index, cues.get(index).withTiming(edit.start(), edit.end()));
    }
    return withVideoCues(document, cues);
  }

  private static StudyDocument suggest(StudyDocument document, Suggestion suggestion) {
    String text = suggestion.text();
    if (text == null || text.isBlank() || text.length() > 2000)
      throw new IllegalArgumentException(
          "Describe the possible correction (up to 2000 characters)");
    int index = index(document, suggestion.cueId());
    String check = "Suggested correction (unverified): " + text.trim();
    return document.withVideo(
        v ->
            v.withCue(
                index,
                cue ->
                    cue.withListeningCheck(
                        cue.listeningCheck() == null || cue.listeningCheck().isBlank()
                            ? check
                            : cue.listeningCheck() + "\n" + check)));
  }

  /**
   * Comments and review requests need text and a cue that still exists; the document itself is
   * unchanged.
   */
  private static StudyDocument requireNote(
      StudyDocument document, String cueId, String text, String what) {
    if (text == null || text.isBlank() || text.length() > 2000)
      throw new IllegalArgumentException("Write " + what + " (up to 2000 characters)");
    switch (document.body()) {
      case VideoDocument video -> index(document, cueId);
      case ComicDocument comic -> lineBox(document, cueId);
    }
    return document;
  }

  private static StudyDocument editCue(StudyDocument document, String cueId, VideoCueEdit edit) {
    int index = index(document, cueId);
    var previous = cues(document).get(index);
    var replacement = replacement(previous, edit);
    var before = previous.line();
    var after = replacement.line();
    boolean sourceChanged = !before.source().equals(after.source());
    boolean changed =
        sourceChanged
            || !Objects.equals(before.translation(), after.translation())
            || before.continuesPrevious() != after.continuesPrevious();
    if (sourceChanged)
      replacement =
          replacement
              .withListeningCheck(previous.listeningCheck())
              .withLine(after.withTextCheck(before.textCheck()));
    if (changed) replacement = replacement.withLine(replacement.line().withAlignment(null));
    var updated = replacement;
    return document.withVideo(v -> v.withCue(index, cue -> updated));
  }

  /** New cues carry the queued identity. */
  private static StudyDocument addCue(StudyDocument document, AddVideoCue add) {
    if (cues(document).stream().anyMatch(c -> c.line().id().equals(add.cueId())))
      throw new IllegalArgumentException("Cue already exists: " + add.cueId());
    var empty = new VideoCue(Line.of(add.cueId(), ""), null, null, null);
    var cue = replacement(empty, add.edit());
    var cues = new ArrayList<>(cues(document));
    cues.add(cue);
    return withVideoCues(document, cues);
  }

  /** Splits transcription evidence between two identities without inventing translated material. */
  private static StudyDocument split(StudyDocument document, SplitVideoCue command) {
    var split = command.split();
    int index = index(document, command.cueId());
    if (cues(document).stream().anyMatch(c -> c.line().id().equals(command.secondId())))
      throw new IllegalArgumentException("Cue already exists: " + command.secondId());
    var original = cues(document).get(index);
    double start = split != null && split.start() != null ? split.start() : original.start();
    double end = split != null && split.end() != null ? split.end() : original.end();
    if (split == null
        || !Double.isFinite(split.at())
        || !Double.isFinite(start)
        || !Double.isFinite(end)
        || start < 0
        || split.at() <= start
        || split.at() >= end
        || split.first() == null
        || split.first().isBlank()
        || split.second() == null
        || split.second().isBlank())
      throw new IllegalArgumentException(
          "Choose a split time inside the cue and text for both parts");
    var first =
        original
            .withTiming(start, split.at())
            .withLine(original.line().withSource(split.first()).withAlignment(null));
    var second =
        original
            .withTiming(split.at(), end)
            .withLine(
                original
                    .line()
                    .withId(command.secondId())
                    .withSource(split.second())
                    .withTranslation(null)
                    .withNotes(List.of())
                    .withContinuesPrevious(false)
                    .withAlignment(null));
    var cues = new ArrayList<>(cues(document));
    cues.set(index, first);
    cues.add(index + 1, second);
    return withVideoCues(document, cues);
  }

  /** Combines consecutive cues in playback order; the earliest identity and annotations survive. */
  private static StudyDocument merge(StudyDocument document, List<String> mergeIds) {
    if (mergeIds == null
        || mergeIds.size() < 2
        || mergeIds.stream().anyMatch(Objects::isNull)
        || new HashSet<>(mergeIds).size() != mergeIds.size())
      throw new IllegalArgumentException("Select at least two distinct consecutive cues");
    var ids = new HashSet<>(mergeIds);
    var cues = cues(document);
    var selected = cues.stream().filter(c -> ids.contains(c.line().id())).toList();
    if (selected.size() != ids.size()) throw new IllegalArgumentException("Unknown merge cue");
    int index = cues.indexOf(selected.getFirst());
    if (!cues.subList(index, index + selected.size()).equals(selected))
      throw new IllegalArgumentException("Selected cues must be consecutive");
    String listening =
        selected.stream()
            .map(VideoCue::listeningCheck)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining("\n"));
    String text =
        selected.stream()
            .map(c -> c.line().textCheck())
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining("\n"));
    var head = selected.getFirst();
    var first =
        head.withTiming(
                head.start(), selected.stream().mapToDouble(VideoCue::end).max().orElseThrow())
            .withListeningCheck(listening.isBlank() ? null : listening)
            .withLine(
                head.line()
                    .withSource(
                        selected.stream()
                            .map(c -> c.line().source().strip())
                            .collect(
                                Collectors.joining(ChunkSeparator.of(document.sourceLanguage()))))
                    .withTextCheck(text.isBlank() ? null : text)
                    .withAlignment(null));
    var result = new ArrayList<>(cues);
    result.subList(index, index + selected.size()).clear();
    result.add(index, first);
    return withVideoCues(document, result);
  }

  /**
   * Rejects stale edits, and clears teaching material tied to changed wording. The line may be a
   * bubble's or one sentence of a text block. Geometry, IDs and outstanding written checks are
   * retained.
   */
  private static StudyDocument editComicText(StudyDocument document, EditComicText command) {
    var edit = command.edit();
    if (edit == null
        || edit.source() == null
        || edit.source().isBlank()
        || edit.expectedSource() == null)
      throw new IllegalArgumentException("Current and replacement source text are required");
    var at = lineBox(document, command.lineId());
    var line =
        at.box(document).lines().stream()
            .filter(l -> l.id().equals(command.lineId()))
            .findFirst()
            .orElseThrow();
    if (!Objects.equals(line.source(), edit.expectedSource()))
      throw new IllegalArgumentException(
          "This bubble changed since you opened it. Reload before editing.");
    if (line.source().equals(edit.source())) return document;
    var replaced =
        line.withSource(edit.source())
            .withTranslation(null)
            .withNotes(List.of())
            .withAlignment(null);
    return at.change(document, box -> box.withLine(command.lineId(), old -> replaced));
  }

  /** Adds one box, with the queued box and line identities, after the last box on its page. */
  private static StudyDocument createComicBox(StudyDocument document, CreateComicBox command) {
    var create = command.create();
    if (create == null
        || create.pageId() == null
        || create.pageId().isBlank()
        || create.region() == null)
      throw new IllegalArgumentException("Page and one region are required");
    int page = pagePosition(document, create.pageId()) - 1;
    var boxes = document.comic().boxes();
    if (boxes.stream().anyMatch(b -> b.id().equals(command.boxId())))
      throw new IllegalArgumentException("Box already exists: " + command.boxId());
    if (boxes.stream().anyMatch(b -> b.holds(command.lineId())))
      throw new IllegalArgumentException("Line already exists: " + command.lineId());
    var added =
        new ComicBox(
            command.boxId(),
            List.of(region(create.region())),
            List.of(
                Line.of(command.lineId(), create.source() == null ? "" : create.source().trim())));
    return document.withComic(
        c ->
            c.withPage(
                page,
                p -> {
                  var updated = new ArrayList<>(p.boxes());
                  updated.add(added);
                  return p.withBoxes(updated);
                }));
  }

  /**
   * Replaces one box's regions and, when {@code order} is given, moves it to that one-based
   * position on its page, with all its lines. Stale expectations are rejected; text, translations,
   * notes and alignments are preserved.
   */
  private static StudyDocument placeComicBox(StudyDocument document, PlaceComicBox command) {
    var update = command.update();
    if (update == null
        || update.expectedRegions() == null
        || update.regions() == null
        || update.regions().isEmpty())
      throw new IllegalArgumentException("Current and replacement regions are required");
    var at = box(document, command.boxId());
    var box = at.box(document);
    if (!matches(box.regions(), update.expectedRegions()))
      throw new IllegalArgumentException(
          "This bubble moved since you opened it. Reload before editing.");
    var placed = box.withRegions(update.regions().stream().map(DocumentEdits::region).toList());
    var boxes = new ArrayList<>(document.comic().pages().get(at.page()).boxes());
    boxes.remove(at.box());
    int position = update.order() == null ? at.box() : update.order() - 1;
    if (position < 0 || position > boxes.size())
      throw new IllegalArgumentException("Reading order must be a position on the page");
    boxes.add(position, placed);
    return document.withComic(c -> c.withPage(at.page(), p -> p.withBoxes(boxes)));
  }

  /** Removes one box, with all its lines. */
  private static StudyDocument deleteComicBox(StudyDocument document, DeleteComicBox command) {
    var at = box(document, command.boxId());
    return document.withComic(
        c -> c.withPage(at.page(), p -> p.withBoxes(remove(p.boxes(), at.box()))));
  }

  /** One-based manifest position of a page; unknown pages are rejected. */
  public static int pagePosition(StudyDocument document, String pageId) {
    var pages = document.comic().pages();
    for (int i = 0; i < pages.size(); i++) if (pages.get(i).id().equals(pageId)) return i + 1;
    throw new NoSuchElementException("Unknown comic page: " + pageId);
  }

  /** The document, once it is known to be a video; see {@link StudyDocument#video()}. */
  private static StudyDocument video(StudyDocument document) {
    document.video();
    return document;
  }

  /** The document, once it is known to be a comic; see {@link StudyDocument#comic()}. */
  private static StudyDocument comic(StudyDocument document) {
    document.comic();
    return document;
  }

  private static List<VideoCue> cues(StudyDocument document) {
    return document.video().cues();
  }

  private static StudyDocument withVideoCues(StudyDocument document, List<VideoCue> cues) {
    return document.withVideo(v -> v.withCues(cues));
  }

  private static <T> List<T> remove(List<T> values, int index) {
    var result = new ArrayList<>(values);
    result.remove(index);
    return result;
  }

  private static int index(StudyDocument document, String cueId) {
    var cues = cues(document);
    for (int i = 0; i < cues.size(); i++) if (cues.get(i).line().id().equals(cueId)) return i;
    throw new NoSuchElementException("Unknown cue: " + cueId);
  }

  /** Where a box sits: the index of its page, and its index among that page's boxes. */
  private record BoxAt(int page, int box) {
    ComicBox box(StudyDocument document) {
      return document.comic().pages().get(page).boxes().get(box);
    }

    StudyDocument change(StudyDocument document, UnaryOperator<ComicBox> change) {
      return document.withComic(c -> c.withPage(page, p -> p.withBox(box, change)));
    }
  }

  /** The first box matching {@code test}; {@code what} names it when there is none. */
  private static BoxAt find(StudyDocument document, Predicate<ComicBox> test, String what) {
    var pages = document.comic().pages();
    for (int p = 0; p < pages.size(); p++)
      for (int b = 0; b < pages.get(p).boxes().size(); b++)
        if (test.test(pages.get(p).boxes().get(b))) return new BoxAt(p, b);
    throw new NoSuchElementException("Unknown comic " + what);
  }

  /** The comic box {@code boxId}. */
  private static BoxAt box(StudyDocument document, String boxId) {
    return find(document, box -> box.id().equals(boxId), "box: " + boxId);
  }

  /** The comic box holding the line {@code lineId}. */
  private static BoxAt lineBox(StudyDocument document, String lineId) {
    return find(document, box -> box.holds(lineId), "line: " + lineId);
  }

  private static void validateTiming(Double start, Double end) {
    if (start == null
        || end == null
        || !Double.isFinite(start)
        || !Double.isFinite(end)
        || start < 0
        || end <= start) throw new IllegalArgumentException("Invalid cue timing");
  }

  /** The player's full replacement of a cue's text, timing and checks; identity is kept. */
  private static VideoCue replacement(VideoCue previous, VideoCueEdit edit) {
    if (edit == null || edit.source() == null || edit.source().isBlank())
      throw new IllegalArgumentException("Subtitle text is required");
    validateTiming(edit.start(), edit.end());
    var line =
        previous
            .line()
            .withSource(edit.source())
            .withTranslation(edit.translation())
            .withNotes(edit.notes() == null ? List.of() : edit.notes())
            .withContinuesPrevious(Boolean.TRUE.equals(edit.continuesPrevious()))
            .withTextCheck(edit.textCheck());
    return new VideoCue(line, edit.start(), edit.end(), edit.listeningCheck());
  }

  private static boolean matches(List<ComicRegion> current, List<ComicRegionInput> expected) {
    if (current.size() != expected.size()) return false;
    for (int i = 0; i < current.size(); i++) {
      ComicRegionInput region = expected.get(i);
      if (region == null
          || current.get(i).x() != region.x()
          || current.get(i).y() != region.y()
          || current.get(i).width() != region.width()
          || current.get(i).height() != region.height()) return false;
    }
    return true;
  }

  private static ComicRegion region(ComicRegionInput input) {
    return new ComicRegion(input.x(), input.y(), input.width(), input.height());
  }
}
