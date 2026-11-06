package com.petitesroues.study;

import com.petitesroues.documents.StudyDocumentValidator;
import com.petitesroues.model.document.ComicCue;
import com.petitesroues.model.document.ComicRegion;
import com.petitesroues.model.document.StudyCue;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoCue;
import com.petitesroues.model.editing.ComicRegionInput;
import com.petitesroues.model.editing.DocumentEdit;
import com.petitesroues.model.editing.DocumentEdit.*;
import com.petitesroues.model.editing.UpdateCueTiming;
import com.petitesroues.model.editing.VideoCueEdit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Applies one {@link DocumentEdit} to a document, with no I/O. The same function accepts a new
 * player edit and replays queued edits over the file, so both always agree. Invalid or no longer
 * applicable edits throw {@link IllegalArgumentException} or {@link NoSuchElementException}; the
 * input is immutable and never changes. Results are validated and video cues kept in playback
 * order.
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
          case CreateComicCue create -> createComicCue(comic(document), create);
          case PlaceComicCue place -> placeComicCue(comic(document), place);
          case DeleteComicCue cue ->
              document.withComic(
                  c -> c.withCues(remove(c.cues(), unblockedIndex(document, cue.cueId()))));
        };
    if (result.hasVideo())
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
    if (document.hasVideo()) index(document, cueId);
    else comicIndex(comic(document), cueId);
    return document;
  }

  private static StudyDocument editCue(StudyDocument document, String cueId, VideoCueEdit edit) {
    int index = index(document, cueId);
    var previous = cues(document).get(index);
    var replacement = replacement(previous, edit);
    var before = previous.study();
    var after = replacement.study();
    boolean sourceChanged = !before.source().equals(after.source());
    boolean changed =
        sourceChanged
            || !Objects.equals(before.translation(), after.translation())
            || before.continuesPrevious() != after.continuesPrevious();
    if (sourceChanged)
      replacement =
          replacement
              .withListeningCheck(previous.listeningCheck())
              .withStudy(after.withTextCheck(before.textCheck()));
    if (changed) replacement = replacement.withStudy(replacement.study().invalidated());
    var updated = replacement;
    return document.withVideo(v -> v.withCue(index, cue -> updated));
  }

  /** New cues carry the queued identity. */
  private static StudyDocument addCue(StudyDocument document, AddVideoCue add) {
    if (cues(document).stream().anyMatch(c -> c.study().id().equals(add.cueId())))
      throw new IllegalArgumentException("Cue already exists: " + add.cueId());
    var empty = new VideoCue(StudyCue.of(add.cueId(), ""), null, null, null);
    var cue = replacement(empty, add.edit());
    var cues = new ArrayList<>(cues(document));
    cues.add(cue);
    return withVideoCues(document, cues);
  }

  /** Splits transcription evidence between two identities without inventing translated material. */
  private static StudyDocument split(StudyDocument document, SplitVideoCue command) {
    var split = command.split();
    int index = index(document, command.cueId());
    if (cues(document).stream().anyMatch(c -> c.study().id().equals(command.secondId())))
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
            .withStudy(original.study().withSource(split.first()).invalidated());
    var second =
        original
            .withTiming(split.at(), end)
            .withStudy(
                original
                    .study()
                    .withId(command.secondId())
                    .withSource(split.second())
                    .withTranslation(null)
                    .withNotes(List.of())
                    .withContinuesPrevious(false)
                    .invalidated());
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
    var selected = cues.stream().filter(c -> ids.contains(c.study().id())).toList();
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
            .map(c -> c.study().textCheck())
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining("\n"));
    var head = selected.getFirst();
    var first =
        head.withTiming(
                head.start(), selected.stream().mapToDouble(VideoCue::end).max().orElseThrow())
            .withListeningCheck(listening.isBlank() ? null : listening)
            .withStudy(
                head.study()
                    .withSource(
                        selected.stream()
                            .map(c -> c.study().source().strip())
                            .collect(Collectors.joining(" ")))
                    .withTextCheck(text.isBlank() ? null : text)
                    .invalidated());
    var result = new ArrayList<>(cues);
    result.subList(index, index + selected.size()).clear();
    result.add(index, first);
    return withVideoCues(document, result);
  }

  /**
   * Rejects stale edits, and clears teaching material tied to changed wording. Geometry, IDs and
   * outstanding written checks are retained.
   */
  private static StudyDocument editComicText(StudyDocument document, EditComicText command) {
    var edit = command.edit();
    if (edit == null
        || edit.source() == null
        || edit.source().isBlank()
        || edit.expectedSource() == null)
      throw new IllegalArgumentException("Current and replacement source text are required");
    int index = comicIndex(document, command.cueId());
    var study = document.comic().cues().get(index).study();
    if (!Objects.equals(study.source(), edit.expectedSource()))
      throw new IllegalArgumentException(
          "This bubble changed since you opened it. Reload before editing.");
    if (study.source().equals(edit.source())) return document;
    var replaced =
        study.withSource(edit.source()).withTranslation(null).withNotes(List.of()).invalidated();
    return document.withComic(c -> c.withCue(index, cue -> cue.withStudy(replaced)));
  }

  /** Adds one bubble with the queued identity at the next free reading position on its page. */
  private static StudyDocument createComicCue(StudyDocument document, CreateComicCue command) {
    var create = command.create();
    if (create == null
        || create.pageId() == null
        || create.pageId().isBlank()
        || create.region() == null)
      throw new IllegalArgumentException("Page and one region are required");
    pagePosition(document, create.pageId());
    var cues = document.comic().cues();
    if (cues.stream().anyMatch(c -> c.study().id().equals(command.cueId())))
      throw new IllegalArgumentException("Bubble already exists: " + command.cueId());
    int order =
        cues.stream()
                .filter(cue -> cue.pageId().equals(create.pageId()))
                .mapToInt(ComicCue::order)
                .max()
                .orElse(0)
            + 1;
    var added =
        new ComicCue(
            StudyCue.of(command.cueId(), create.source() == null ? "" : create.source().trim()),
            create.pageId(),
            order,
            List.of(region(create.region())));
    var updated = new ArrayList<>(cues);
    updated.add(added);
    return document.withComic(c -> c.withCues(updated));
  }

  /**
   * Replaces one bubble's regions and optional reading order. Stale expectations are rejected;
   * text, translations, notes and alignments are preserved.
   */
  private static StudyDocument placeComicCue(StudyDocument document, PlaceComicCue command) {
    var update = command.update();
    if (update == null
        || update.expectedRegions() == null
        || update.regions() == null
        || update.regions().isEmpty())
      throw new IllegalArgumentException("Current and replacement regions are required");
    int index = unblockedIndex(document, command.cueId());
    var cue = document.comic().cues().get(index);
    if (!matches(cue.regions(), update.expectedRegions()))
      throw new IllegalArgumentException(
          "This bubble moved since you opened it. Reload before editing.");
    var placed = cue.withRegions(update.regions().stream().map(DocumentEdits::region).toList());
    if (update.order() != null) placed = placed.withOrder(update.order());
    var result = placed;
    return document.withComic(c -> c.withCue(index, old -> result));
  }

  /**
   * Position of a bubble whose box may be moved or deleted. A text block's sentences share one box,
   * so they are rearranged by an agent in the document rather than one sentence at a time.
   */
  private static int unblockedIndex(StudyDocument document, String cueId) {
    int index = comicIndex(document, cueId);
    if (document.comic().cues().get(index).inBlock())
      throw new IllegalArgumentException(
          "This box holds a text block split into sentences; it can't be moved or deleted here.");
    return index;
  }

  /** One-based manifest position of a page; unknown pages are rejected. */
  public static int pagePosition(StudyDocument document, String pageId) {
    var pages = document.comic().pages();
    for (int i = 0; i < pages.size(); i++) if (pages.get(i).id().equals(pageId)) return i + 1;
    throw new NoSuchElementException("Unknown comic page: " + pageId);
  }

  private static StudyDocument video(StudyDocument document) {
    if (!document.hasVideo())
      throw new IllegalArgumentException("This operation requires a video document");
    return document;
  }

  private static StudyDocument comic(StudyDocument document) {
    if (!document.hasComic())
      throw new IllegalArgumentException("This operation requires a comic document");
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
    for (int i = 0; i < cues.size(); i++) if (cues.get(i).study().id().equals(cueId)) return i;
    throw new NoSuchElementException("Unknown cue: " + cueId);
  }

  private static int comicIndex(StudyDocument document, String cueId) {
    var cues = document.comic().cues();
    for (int i = 0; i < cues.size(); i++) if (cues.get(i).study().id().equals(cueId)) return i;
    throw new NoSuchElementException("Unknown comic cue: " + cueId);
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
    var study =
        previous
            .study()
            .withSource(edit.source())
            .withTranslation(edit.translation())
            .withNotes(edit.notes() == null ? List.of() : edit.notes())
            .withContinuesPrevious(Boolean.TRUE.equals(edit.continuesPrevious()))
            .withTextCheck(edit.textCheck());
    return new VideoCue(study, edit.start(), edit.end(), edit.listeningCheck());
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
