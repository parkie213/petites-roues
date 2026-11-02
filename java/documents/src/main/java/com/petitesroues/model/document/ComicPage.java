package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One page image inside the title's CBZ, with an optional printed page label. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComicPage(String id, String entry, String label) {
  public ComicPage {
    id = text(id);
    entry = text(entry);
  }
}
