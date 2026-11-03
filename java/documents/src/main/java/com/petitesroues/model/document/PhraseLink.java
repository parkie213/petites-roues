package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;
import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Zero-based source and translation chunk indices that mean the same thing, with an optional
 * learner note. A link without a note is a reviewed breakdown awaiting the notes stage; its empty
 * note is left out of the written file.
 */
public record PhraseLink(
    List<Integer> source,
    List<Integer> translation,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) String note) {
  public PhraseLink {
    source = list(source);
    translation = list(translation);
    note = text(note);
  }
}
