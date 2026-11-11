package app.petitesroues.library;

import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.editing.ComicBoxCreate;
import app.petitesroues.model.editing.ComicBoxUpdate;
import app.petitesroues.model.editing.ComicTextEdit;
import app.petitesroues.model.editing.MergeCues;
import app.petitesroues.model.editing.SplitCue;
import app.petitesroues.model.editing.UpdateCueTiming;
import app.petitesroues.model.editing.VideoCueEdit;
import app.petitesroues.study.DocumentEditingService;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Entry point for player and reader edits. Synchronized methods share this service's monitor, so
 * each edit is validated against the served document and queued before the next edit, review
 * completion or repository replacement reads it. Edits are queued, never written to files.
 */
@Service
public class LibraryUpdateService {
  private final DocumentEditingService editing;

  public LibraryUpdateService(DocumentEditingService editing) {
    this.editing = editing;
  }

  public synchronized int saveCueTiming(String id, List<UpdateCueTiming> edits) throws IOException {
    return editing.saveCueTiming(id, edits);
  }

  public synchronized StudyDocument suggestCorrection(String id, String cueId, String suggestion)
      throws IOException {
    return editing.suggestCorrection(id, cueId, suggestion);
  }

  public synchronized StudyDocument comment(String id, String cueId, String text)
      throws IOException {
    return editing.comment(id, cueId, text);
  }

  public synchronized StudyDocument editCue(String titleId, String cueId, VideoCueEdit cue)
      throws IOException {
    return editing.editCue(titleId, cueId, cue);
  }

  public synchronized StudyDocument addCue(String titleId, VideoCueEdit cue) throws IOException {
    return editing.addCue(titleId, cue);
  }

  public synchronized StudyDocument splitCue(String titleId, String cueId, SplitCue split)
      throws IOException {
    return editing.splitCue(titleId, cueId, split);
  }

  public synchronized StudyDocument mergeCues(String titleId, MergeCues merge) throws IOException {
    return editing.mergeCues(titleId, merge);
  }

  public synchronized StudyDocument deleteCue(String titleId, String cueId) throws IOException {
    return editing.deleteCue(titleId, cueId);
  }

  public synchronized StudyDocument editComicText(String id, String lineId, ComicTextEdit edit)
      throws IOException {
    return editing.editComicText(id, lineId, edit);
  }

  public synchronized StudyDocument createComicBox(String id, ComicBoxCreate create)
      throws IOException {
    return editing.createComicBox(id, create);
  }

  public synchronized StudyDocument placeComicBox(String id, String boxId, ComicBoxUpdate update)
      throws IOException {
    return editing.placeComicBox(id, boxId, update);
  }

  public synchronized StudyDocument deleteComicBox(String id, String boxId) throws IOException {
    return editing.deleteComicBox(id, boxId);
  }
}
