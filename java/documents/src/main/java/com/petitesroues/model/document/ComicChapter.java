package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A chapter of a comic volume. A chapter starts on {@code pageId} and runs until the next chapter's
 * first page, or the end of the volume.
 *
 * @param number optional positive printed chapter number; absent for extras and bonus sections
 * @param title the chapter title in the source language, as printed
 * @param pageId the manifest ID of the chapter's first page
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComicChapter(Integer number, String title, String pageId) {
  public ComicChapter {
    title = text(title);
    pageId = text(pageId);
  }
}
