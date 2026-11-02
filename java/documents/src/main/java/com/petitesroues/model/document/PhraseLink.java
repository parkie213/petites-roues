package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;
import static com.petitesroues.model.document.Defaults.text;

import java.util.List;

/** Zero-based source and translation chunk indices that mean the same thing, with a note. */
public record PhraseLink(List<Integer> source, List<Integer> translation, String note) {
  public PhraseLink {
    source = list(source);
    translation = list(translation);
    note = text(note);
  }
}
