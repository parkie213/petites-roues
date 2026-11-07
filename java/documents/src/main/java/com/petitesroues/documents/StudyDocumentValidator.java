package com.petitesroues.documents;

import com.petitesroues.model.PhraseAlignmentValidator;
import com.petitesroues.model.document.ComicBox;
import com.petitesroues.model.document.ComicDocument;
import com.petitesroues.model.document.Line;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoCue;
import com.petitesroues.model.document.VideoDocument;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks imported or loaded documents for the assumptions required by the player and editor.
 * Structural validity says nothing about the quality of translations or notes.
 */
public final class StudyDocumentValidator {
  private static final Pattern FINGERPRINT = Pattern.compile("v1:[0-9a-f]{64}");

  private StudyDocumentValidator() {}

  /**
   * Rejects a document that breaks any structural rule.
   *
   * @throws IllegalArgumentException with the first problem and the count of any others
   */
  public static void validate(StudyDocument document) {
    var problems = problems(document);
    if (problems.isEmpty()) return;
    throw new IllegalArgumentException(
        problems.size() == 1
            ? problems.get(0)
            : problems.get(0) + " (and " + (problems.size() - 1) + " more problems)");
  }

  /**
   * Lists every structural problem without throwing. Document-level problems stop the check; each
   * cue, box and line is checked independently and its problems are prefixed with its ID.
   */
  public static List<String> problems(StudyDocument document) {
    List<String> problems = new ArrayList<>();
    try {
      validateDocument(document, problems);
    } catch (IllegalArgumentException failure) {
      problems.add(failure.getMessage());
    }
    return problems;
  }

  private static void validateDocument(StudyDocument document, List<String> problems) {
    if (document == null)
      throw new IllegalArgumentException("Exactly one video or comic body is required");
    if (document.id().isBlank()) {
      throw new IllegalArgumentException("A nonblank document id is required");
    }
    if (document.title().isBlank()) {
      throw new IllegalArgumentException("A title is required");
    }
    validateLanguage(document.sourceLanguage(), "Source");
    validateLanguage(document.translationLanguage(), "Translation");
    validateLanguage(document.notesLanguage(), "Notes");
    if (document.body() instanceof ComicDocument comic) validateComic(comic, problems);
    else validateVideo(document.video(), problems);
  }

  private static String lineId(Line line) {
    return line == null ? "" : line.id();
  }

  /** Runs one item's rules, recording a failure under its ID instead of stopping. */
  private static void check(List<String> problems, String id, Runnable rules) {
    try {
      rules.run();
    } catch (IllegalArgumentException failure) {
      problems.add((id.isBlank() ? "" : id + ": ") + failure.getMessage());
    }
  }

  private static void validateComic(ComicDocument document, List<String> problems) {
    if (document.volume() != null && document.volume() < 1)
      throw new IllegalArgumentException("A comic volume must be a positive number");
    if (document.reference() != null)
      validateReference(document.reference().fingerprint(), document.reference().sizeBytes());
    if (document.pages().isEmpty() || document.pages().size() > 10000)
      throw new IllegalArgumentException("A comic requires 1–10000 pages");
    Set<String> ids = new HashSet<>();
    Set<String> entries = new HashSet<>();
    for (var page : document.pages()) {
      if (!page.id().matches("[A-Za-z0-9_-]+") || !ids.add(page.id()))
        throw new IllegalArgumentException("Comic page IDs must be safe and unique");
      if (page.entry().startsWith("/")
          || page.entry().contains("\\")
          || Arrays.asList(page.entry().split("/", -1)).stream()
              .anyMatch(part -> part.isEmpty() || part.equals("..") || part.equals("."))
          || !page.entry().toLowerCase(Locale.ROOT).matches(".+\\.(jpg|jpeg|png|webp)")
          || !entries.add(page.entry()))
        throw new IllegalArgumentException("Comic pages require distinct relative image entries");
    }
    validateChapters(document);
    Set<String> boxIds = new HashSet<>();
    Set<String> lineIds = new HashSet<>();
    for (var box : document.boxes()) {
      check(problems, box.id(), () -> validateComicBox(box, boxIds));
      // Text problems are reported under the line's own ID, which is what review and preparation
      // address.
      for (var line : box.lines())
        check(
            problems,
            line.id(),
            () -> {
              if (line.id().isBlank() || !lineIds.add(line.id()))
                throw new IllegalArgumentException("Line IDs must be unique and nonempty");
              validateLine(line);
            });
    }
  }

  /** Chapters must start on known pages, in strictly increasing manifest order. */
  private static void validateChapters(ComicDocument document) {
    Map<String, Integer> positions = new HashMap<>();
    for (int i = 0; i < document.pages().size(); i++)
      positions.put(document.pages().get(i).id(), i);
    int previous = -1;
    for (var chapter : document.chapters()) {
      if (chapter.title().isBlank())
        throw new IllegalArgumentException("Comic chapters require a title");
      if (chapter.number() != null && chapter.number() < 1)
        throw new IllegalArgumentException("A comic chapter number must be a positive number");
      Integer position = positions.get(chapter.pageId());
      if (position == null || position <= previous)
        throw new IllegalArgumentException(
            "Comic chapters must start on known pages in reading order");
      previous = position;
    }
  }

  /** A box: its own ID, unique across the comic, at least one line, and valid regions. */
  private static void validateComicBox(ComicBox box, Set<String> boxIds) {
    if (box.id().isBlank() || !boxIds.add(box.id()))
      throw new IllegalArgumentException("Comic box IDs must be unique and nonempty");
    if (box.lines().isEmpty())
      throw new IllegalArgumentException("A comic box needs at least one line");
    if (box.regions().isEmpty())
      throw new IllegalArgumentException("Comic boxes require at least one region");
    for (var region : box.regions()) {
      if (!Double.isFinite(region.x())
          || !Double.isFinite(region.y())
          || !Double.isFinite(region.width())
          || !Double.isFinite(region.height())
          || region.x() < 0
          || region.y() < 0
          || region.width() <= 0
          || region.height() <= 0
          || region.x() + region.width() > 1
          || region.y() + region.height() > 1)
        throw new IllegalArgumentException("Comic regions must fit inside normalized page bounds");
    }
  }

  private static void validateVideo(VideoDocument document, List<String> problems) {
    var reference = document.reference();
    if (reference != null) {
      validateReference(reference.fingerprint(), reference.sizeBytes());
      if (reference.durationSeconds() != null
          && (!Double.isFinite(reference.durationSeconds()) || reference.durationSeconds() <= 0))
        throw new IllegalArgumentException("Reference duration must be positive and finite");
    }
    Set<String> cueIds = new HashSet<>();
    for (VideoCue cue : document.cues()) {
      check(
          problems,
          lineId(cue.line()),
          () -> {
            if (!cue.hasLine() || cue.line().id().isBlank() || !cueIds.add(cue.line().id()))
              throw new IllegalArgumentException("Cue IDs must be unique and nonempty");
            validateCue(cue);
          });
    }
  }

  /** A reference's optional fingerprint is a v1 hash and its optional size is positive. */
  private static void validateReference(String fingerprint, Long sizeBytes) {
    if (fingerprint != null && !FINGERPRINT.matcher(fingerprint).matches())
      throw new IllegalArgumentException(
          "Reference fingerprint must be v1: and 64 lowercase hex digits");
    if (sizeBytes != null && sizeBytes <= 0)
      throw new IllegalArgumentException("Reference size must be positive");
  }

  /** Requires an explicit, well-formed BCP 47 tag with a language subtag. */
  private static void validateLanguage(String tag, String role) {
    try {
      if (tag == null
          || tag.isBlank()
          || new Locale.Builder().setLanguageTag(tag).build().getLanguage().isEmpty()) {
        throw new IllegalArgumentException(role + " language is required");
      }
    } catch (IllformedLocaleException failure) {
      throw new IllegalArgumentException(role + " language must be a BCP 47 tag", failure);
    }
  }

  private static void validateCue(VideoCue cue) {
    validateLine(cue.line());
    if (cue.start() == null
        || cue.end() == null
        || !Double.isFinite(cue.start())
        || !Double.isFinite(cue.end())
        || cue.start() < 0
        || cue.end() <= cue.start()) {
      throw new IllegalArgumentException("Invalid timing for cue " + cue.line().id());
    }
  }

  private static void validateLine(Line line) {
    if (line.alignment() != null)
      PhraseAlignmentValidator.validateFor(line.alignment(), line.source(), line.translation());
  }
}
