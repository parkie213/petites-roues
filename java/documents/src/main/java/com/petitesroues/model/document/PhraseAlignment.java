package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;
import static com.petitesroues.model.document.Defaults.text;

import java.util.List;

/**
 * A phrase breakdown bound to the exact source and translation it was made for. Chunks joined with
 * single spaces reproduce each sentence; links pair chunk indices and explain them.
 */
public record PhraseAlignment(
    String sourceText,
    String translationText,
    List<String> source,
    List<String> translation,
    List<PhraseLink> links) {
  public PhraseAlignment {
    sourceText = text(sourceText);
    translationText = text(translationText);
    source = list(source);
    translation = list(translation);
    links = list(links);
  }
}
