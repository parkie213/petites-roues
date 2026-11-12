package app.petitesroues.quiz;

import app.petitesroues.comic.ComicService;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Builds chapter briefs from the current served document, including queued learner edits. */
@Service
public class ChapterQuizService {
  private final ComicService comics;

  public ChapterQuizService(ComicService comics) {
    this.comics = comics;
  }

  /** Resolves the chapter by its stable first-page ID; performs no writes or chat/model calls. */
  public ChapterQuizBuilder.Brief build(
      String titleId, String chapterPageId, String rules, ChapterQuizBuilder.Type type)
      throws IOException {
    if (chapterPageId == null || rules == null)
      throw new IllegalArgumentException("Chapter page and quiz rules are required");
    var document = comics.document(titleId);
    var chapters = document.comic().chapters();
    for (int index = 0; index < chapters.size(); index++)
      if (chapters.get(index).pageId().equals(chapterPageId))
        return ChapterQuizBuilder.build(rules, document, index, type);
    throw new NoSuchElementException("Unknown chapter: " + chapterPageId);
  }
}
