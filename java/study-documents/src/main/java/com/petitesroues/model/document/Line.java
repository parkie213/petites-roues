package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;
import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One line of source text with its teaching material: a subtitle line, the text of a speech bubble
 * or one sentence of a printed text block. Video cues and comic boxes both hold lines. {@code
 * translation}, {@code alignment} and {@code textCheck} are {@code null} when absent; a line
 * without an alignment needs a phrase breakdown.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Line(
    String id,
    String source,
    String translation,
    List<String> notes,
    PhraseAlignment alignment,
    boolean continuesPrevious,
    String textCheck) {
  public Line {
    id = text(id);
    source = text(source);
    notes = list(notes);
  }

  /** An empty line with this identity and source text. */
  public static Line of(String id, String source) {
    return new Line(id, source, null, List.of(), null, false, null);
  }

  public Line withId(String id) {
    return new Line(id, source, translation, notes, alignment, continuesPrevious, textCheck);
  }

  public Line withSource(String source) {
    return new Line(id, source, translation, notes, alignment, continuesPrevious, textCheck);
  }

  public Line withTranslation(String translation) {
    return new Line(id, source, translation, notes, alignment, continuesPrevious, textCheck);
  }

  public Line withNotes(List<String> notes) {
    return new Line(id, source, translation, notes, alignment, continuesPrevious, textCheck);
  }

  public Line withAlignment(PhraseAlignment alignment) {
    return new Line(id, source, translation, notes, alignment, continuesPrevious, textCheck);
  }

  public Line withContinuesPrevious(boolean continuesPrevious) {
    return new Line(id, source, translation, notes, alignment, continuesPrevious, textCheck);
  }

  public Line withTextCheck(String textCheck) {
    return new Line(id, source, translation, notes, alignment, continuesPrevious, textCheck);
  }
}
