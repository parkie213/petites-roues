package app.petitesroues.study;

import app.petitesroues.comic.ComicService;
import app.petitesroues.comic.ReadingProgressStore;
import app.petitesroues.library.LibraryUpdateService;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.document.ComicDocument;
import app.petitesroues.model.editing.StudyState;
import app.petitesroues.model.progress.ReadingProgress;
import app.petitesroues.model.progress.SavedProgress;
import app.petitesroues.playback.PlaybackProgressStore;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Captures the player's document, pending review and progress together. The update monitor is
 * acquired before a database transaction; archive validation occurs after that transaction. Live
 * document capture remains until the explicit-publication storage migration.
 */
@Service
public class StudyStateService {
  private final LibraryUpdateService monitor;
  private final CatalogueStore titles;
  private final ReviewQueueService served;
  private final DocumentCommandService commands;
  private final PlaybackProgressStore playback;
  private final ReadingProgressStore reading;
  private final ComicService comics;
  private final TransactionTemplate transactions;
  private final DocumentStore documents;

  public StudyStateService(
      LibraryUpdateService monitor,
      CatalogueStore titles,
      ReviewQueueService served,
      DocumentCommandService commands,
      PlaybackProgressStore playback,
      ReadingProgressStore reading,
      ComicService comics,
      TransactionTemplate transactions,
      DocumentStore documents) {
    this.monitor = monitor;
    this.titles = titles;
    this.served = served;
    this.commands = commands;
    this.playback = playback;
    this.reading = reading;
    this.comics = comics;
    this.transactions = transactions;
    this.documents = documents;
  }

  public StudyState read(String id) throws IOException {
    StudyState state;
    synchronized (monitor) {
      var title =
          titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
      var source = documents.read(title);
      state =
          transactions.execute(
              status -> {
                var effective = served.effective(title, source);
                var description = commands.describe(effective);
                SavedProgress progress;
                if (effective.document().body() instanceof ComicDocument comic) {
                  var saved = reading.find(id);
                  boolean present =
                      comic.pages().stream().anyMatch(page -> page.id().equals(saved.pageId()));
                  progress = present ? saved : new ReadingProgress(null, null);
                } else progress = playback.find(id);
                return new StudyState(
                    effective.document(), description.version(), effective.pending(), progress);
              });
    }
    if (state.document().body() instanceof ComicDocument)
      comics.validateArchive(id, state.document());
    return state;
  }
}
