package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;

import java.util.List;

/**
 * A phrase breakdown of one line. Chunks joined with their language's {@link
 * com.petitesroues.model.ChunkSeparator} reproduce the line's source and translation exactly, which
 * binds the breakdown to the wording it was made for; links pair chunk indices and explain them.
 */
public record PhraseAlignment(
    List<String> source, List<String> translation, List<PhraseLink> links) {
  public PhraseAlignment {
    source = list(source);
    translation = list(translation);
    links = list(links);
  }
}
