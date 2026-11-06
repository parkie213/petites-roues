package com.petitesroues.documents;

import com.petitesroues.model.PhraseAlignmentValidator;
import com.petitesroues.model.document.ComicCue;
import com.petitesroues.model.document.ComicDocument;
import com.petitesroues.model.document.StudyCue;
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

/**
 * Checks imported or loaded documents for the assumptions required by the player and editor.
 * Structural validity says nothing about the quality of translations or notes.
 */
public final class StudyDocumentValidator {
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
   * cue is checked independently and its problems are prefixed with its ID.
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
    if (document == null || (!document.hasVideo() && !document.hasComic()))
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
    if (document.hasComic()) {
      validateComic(document.comic(), problems);
      return;
    }
    validateVideo(document.video(), problems);
  }

  private static String cueId(StudyCue study) {
    return study == null ? "" : study.id();
  }

  /** Runs one cue's rules, recording a failure under the cue's ID instead of stopping. */
  private static void checkCue(List<String> problems, String cueId, Runnable rules) {
    try {
      rules.run();
    } catch (IllegalArgumentException failure) {
      problems.add((cueId.isBlank() ? "" : cueId + ": ") + failure.getMessage());
    }
  }

  private static void validateComic(ComicDocument document, List<String> problems) {
    if (document.volume() != null && document.volume() < 1)
      throw new IllegalArgumentException("A comic volume must be a positive number");
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
    Set<String> studyIds = new HashSet<>();
    Set<String> orders = new HashSet<>();
    for (var cue : document.cues()) {
      checkCue(problems, cue.id(), () -> validateComicBox(cue, ids, boxIds, orders));
      // Text problems are reported under the study's own ID, which is what review and
      // preparation address.
      for (var study : cue.studies())
        checkCue(
            problems,
            study.id(),
            () -> {
              if (study.id().isBlank() || !studyIds.add(study.id()))
                throw new IllegalArgumentException("Cue IDs must be unique and nonempty");
              validateStudyCue(study);
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

  /** A box: its own unique ID, at least one study, a known page and order, and valid regions. */
  private static void validateComicBox(
      ComicCue cue, Set<String> pageIds, Set<String> boxIds, Set<String> orders) {
    if (cue.id().isBlank() || !boxIds.add(cue.id()))
      throw new IllegalArgumentException("Comic box IDs must be unique and nonempty");
    if (cue.studies().isEmpty())
      throw new IllegalArgumentException("A comic box needs at least one study");
    if (!pageIds.contains(cue.pageId())
        || cue.order() <= 0
        || !orders.add(cue.pageId() + ":" + cue.order()))
      throw new IllegalArgumentException(
          "Comic cues need a known page and unique positive page order");
    if (cue.regions().isEmpty())
      throw new IllegalArgumentException("Comic cues require at least one region");
    for (var region : cue.regions()) {
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
    if (document.durationSeconds() != null
        && (!Double.isFinite(document.durationSeconds()) || document.durationSeconds() <= 0))
      throw new IllegalArgumentException("Reference duration must be positive and finite");
    Set<String> cueIds = new HashSet<>();
    for (VideoCue cue : document.cues()) {
      checkCue(
          problems,
          cueId(cue.study()),
          () -> {
            if (!cue.hasStudy() || cue.study().id().isBlank() || !cueIds.add(cue.study().id()))
              throw new IllegalArgumentException("Cue IDs must be unique and nonempty");
            validateCue(cue);
          });
    }
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
    validateStudyCue(cue.study());
    if (cue.start() == null
        || cue.end() == null
        || !Double.isFinite(cue.start())
        || !Double.isFinite(cue.end())
        || cue.start() < 0
        || cue.end() <= cue.start()) {
      throw new IllegalArgumentException("Invalid timing for cue " + cue.study().id());
    }
  }

  private static void validateStudyCue(StudyCue cue) {
    if (cue.alignment() != null) {
      if (cue.alignmentStale())
        throw new IllegalArgumentException(
            "Stale alignment must be regenerated for cue " + cue.id());
      PhraseAlignmentValidator.validateFor(cue.alignment(), cue.source(), cue.translation());
    }
  }
}
