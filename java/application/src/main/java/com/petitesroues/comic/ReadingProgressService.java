package com.petitesroues.comic;

import com.petitesroues.model.progress.ReadingProgress;
import java.io.IOException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates comic positions against the current page identities before storing local progress. */
@Service
public class ReadingProgressService {
  private final ComicService comics;
  private final ReadingProgressStore store;

  public ReadingProgressService(ComicService comics, ReadingProgressStore store) {
    this.comics = comics;
    this.store = store;
  }

  /** Restores valid saved page identities; removed pages reset to the first page in the viewer. */
  @Transactional(readOnly = true)
  public ReadingProgress load(String id) throws IOException {
    var document = comics.document(id);
    var saved = store.find(id);
    return document.comic().pages().stream().anyMatch(page -> page.id().equals(saved.pageId()))
        ? saved
        : new ReadingProgress(null, null);
  }

  /** Stores only positions that exist in the current comic manifest. */
  @Transactional
  public ReadingProgress save(String id, String pageId) throws IOException {
    if (pageId == null || pageId.isBlank())
      throw new IllegalArgumentException("Page ID is required");
    var document = comics.document(id);
    comics.page(document, pageId);
    var pages = document.comic().pages();
    int index = 0;
    while (!pages.get(index).id().equals(pageId)) index++;
    var saved = new ReadingProgress(pageId, Instant.now().toString());
    store.save(id, saved, (index + 1) / (double) pages.size());
    return saved;
  }

  /** Forgets a comic's page, so it opens at the first page and shows no progress. */
  @Transactional
  public void clear(String id) throws IOException {
    comics.document(id);
    store.delete(id);
  }
}
