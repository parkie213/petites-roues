package app.petitesroues.comic;

import app.petitesroues.model.progress.ReadingPosition;
import app.petitesroues.model.progress.ReadingProgress;
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
    var position = saved.pageId() == null ? null : new ReadingPosition(saved.pageId());
    var pageIds = document.comic().pages().stream().map(page -> page.id()).toList();
    return position != null && position.indexIn(pageIds) >= 0
        ? saved
        : new ReadingProgress(null, null);
  }

  /** Stores only positions that exist in the current comic manifest. */
  @Transactional
  public ReadingProgress save(String id, String pageId) throws IOException {
    var position = new ReadingPosition(pageId);
    var document = comics.document(id);
    comics.page(document, pageId);
    var pageIds = document.comic().pages().stream().map(page -> page.id()).toList();
    var saved = new ReadingProgress(pageId, Instant.now().toString());
    store.save(id, saved, position.fractionIn(pageIds));
    return saved;
  }

  /** Forgets a comic's page, so it opens at the first page and shows no progress. */
  @Transactional
  public void clear(String id) throws IOException {
    comics.document(id);
    store.delete(id);
  }
}
