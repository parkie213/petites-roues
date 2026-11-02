package com.petitesroues.library;

import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.editing.ComicCueCreate;
import com.petitesroues.model.editing.ComicCueUpdate;
import com.petitesroues.model.editing.ComicTextEdit;
import com.petitesroues.model.editing.MergeCues;
import com.petitesroues.model.editing.SplitCue;
import com.petitesroues.model.editing.UpdateCueTiming;
import com.petitesroues.model.editing.VideoCueEdit;
import com.petitesroues.study.DocumentEditingService;
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

  public synchronized StudyDocument editComicText(String id, String cueId, ComicTextEdit edit)
      throws IOException {
    return editing.editComicText(id, cueId, edit);
  }

  public synchronized StudyDocument createComicCue(String id, ComicCueCreate create)
      throws IOException {
    return editing.createComicCue(id, create);
  }

  public synchronized StudyDocument placeComicCue(String id, String cueId, ComicCueUpdate update)
      throws IOException {
    return editing.placeComicCue(id, cueId, update);
  }

  public synchronized StudyDocument deleteComicCue(String id, String cueId) throws IOException {
    return editing.deleteComicCue(id, cueId);
  }
}
