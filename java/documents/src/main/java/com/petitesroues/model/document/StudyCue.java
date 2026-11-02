package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;
import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The teaching material shared by video and comic cues. {@code translation}, {@code alignment} and
 * {@code textCheck} are {@code null} when absent; {@code alignmentStale} marks links that must be
 * regenerated after the wording changed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudyCue(
    String id,
    String source,
    String translation,
    List<String> notes,
    PhraseAlignment alignment,
    boolean alignmentStale,
    boolean continuesPrevious,
    String textCheck) {
  public StudyCue {
    id = text(id);
    source = text(source);
    notes = list(notes);
  }

  /** An empty cue with this identity and source text. */
  public static StudyCue of(String id, String source) {
    return new StudyCue(id, source, null, List.of(), null, false, false, null);
  }

  public StudyCue withId(String id) {
    return new StudyCue(
        id, source, translation, notes, alignment, alignmentStale, continuesPrevious, textCheck);
  }

  public StudyCue withSource(String source) {
    return new StudyCue(
        id, source, translation, notes, alignment, alignmentStale, continuesPrevious, textCheck);
  }

  public StudyCue withTranslation(String translation) {
    return new StudyCue(
        id, source, translation, notes, alignment, alignmentStale, continuesPrevious, textCheck);
  }

  public StudyCue withNotes(List<String> notes) {
    return new StudyCue(
        id, source, translation, notes, alignment, alignmentStale, continuesPrevious, textCheck);
  }

  public StudyCue withAlignment(PhraseAlignment alignment) {
    return new StudyCue(
        id, source, translation, notes, alignment, alignmentStale, continuesPrevious, textCheck);
  }

  public StudyCue withAlignmentStale(boolean alignmentStale) {
    return new StudyCue(
        id, source, translation, notes, alignment, alignmentStale, continuesPrevious, textCheck);
  }

  public StudyCue withContinuesPrevious(boolean continuesPrevious) {
    return new StudyCue(
        id, source, translation, notes, alignment, alignmentStale, continuesPrevious, textCheck);
  }

  public StudyCue withTextCheck(String textCheck) {
    return new StudyCue(
        id, source, translation, notes, alignment, alignmentStale, continuesPrevious, textCheck);
  }

  /** The same cue with its phrase links removed and marked for regeneration. */
  public StudyCue invalidated() {
    return withAlignment(null).withAlignmentStale(true);
  }
}
