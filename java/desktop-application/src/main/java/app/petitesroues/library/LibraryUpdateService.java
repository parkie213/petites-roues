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
 * Entry point for player and reader edits. Each edit runs while holding the shared {@link
 * LibraryUpdateMonitor}, so it is validated against the served document and queued before the next
 * edit, review-queue operation or repository replacement reads that state. Edits are queued, never
 * written to files.
 */
@Service
public class LibraryUpdateService {
  private final LibraryUpdateMonitor monitor;
  private final DocumentEditingService editing;

  public LibraryUpdateService(LibraryUpdateMonitor monitor, DocumentEditingService editing) {
    this.monitor = monitor;
    this.editing = editing;
  }

  public int saveCueTiming(String id, List<UpdateCueTiming> edits) throws IOException {
    synchronized (monitor) {
      return editing.saveCueTiming(id, edits);
    }
  }

  public StudyDocument suggestCorrection(String id, String cueId, String suggestion)
      throws IOException {
    synchronized (monitor) {
      return editing.suggestCorrection(id, cueId, suggestion);
    }
  }

  public StudyDocument comment(String id, String cueId, String text) throws IOException {
    synchronized (monitor) {
      return editing.comment(id, cueId, text);
    }
  }

  public StudyDocument editCue(String titleId, String cueId, VideoCueEdit cue) throws IOException {
    synchronized (monitor) {
      return editing.editCue(titleId, cueId, cue);
    }
  }

  public StudyDocument addCue(String titleId, VideoCueEdit cue) throws IOException {
    synchronized (monitor) {
      return editing.addCue(titleId, cue);
    }
  }

  public StudyDocument splitCue(String titleId, String cueId, SplitCue split) throws IOException {
    synchronized (monitor) {
      return editing.splitCue(titleId, cueId, split);
    }
  }

  public StudyDocument mergeCues(String titleId, MergeCues merge) throws IOException {
    synchronized (monitor) {
      return editing.mergeCues(titleId, merge);
    }
  }

  public StudyDocument deleteCue(String titleId, String cueId) throws IOException {
    synchronized (monitor) {
      return editing.deleteCue(titleId, cueId);
    }
  }

  public StudyDocument editComicText(String id, String lineId, ComicTextEdit edit)
      throws IOException {
    synchronized (monitor) {
      return editing.editComicText(id, lineId, edit);
    }
  }

  public StudyDocument createComicBox(String id, ComicBoxCreate create) throws IOException {
    synchronized (monitor) {
      return editing.createComicBox(id, create);
    }
  }

  public StudyDocument placeComicBox(String id, String boxId, ComicBoxUpdate update)
      throws IOException {
    synchronized (monitor) {
      return editing.placeComicBox(id, boxId, update);
    }
  }

  public StudyDocument deleteComicBox(String id, String boxId) throws IOException {
    synchronized (monitor) {
      return editing.deleteComicBox(id, boxId);
    }
  }
}
