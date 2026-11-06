package com.petitesroues.study;

import com.petitesroues.library.LibraryUpdateService;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.editing.ComicCueCreate;
import com.petitesroues.model.editing.ComicCueUpdate;
import com.petitesroues.model.editing.ComicTextEdit;
import com.petitesroues.model.editing.DocumentEdit;
import com.petitesroues.model.editing.MergeCues;
import com.petitesroues.model.editing.SplitCue;
import com.petitesroues.model.editing.UpdateCueTiming;
import com.petitesroues.model.editing.VideoCueEdit;
import com.petitesroues.model.library.CatalogueEntry;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Accepts player and reader edits by queueing them; it never writes document files. Each edit is
 * applied to the served document (file plus earlier queued edits) to validate it, then stored with
 * any identities it introduces. Edits that change nothing are not queued. Callers use {@link
 * LibraryUpdateService} so queueing is serialized with repository replacement.
 */
@Service
public class DocumentEditingService {
  private final CatalogueStore titles;
  private final ReviewQueueService served;
  private final ReviewQueue queue;

  public DocumentEditingService(
      CatalogueStore titles, ReviewQueueService served, ReviewQueue queue) {
    this.titles = titles;
    this.served = served;
    this.queue = queue;
  }

  public StudyDocument suggestCorrection(String id, String cueId, String suggestion)
      throws IOException {
    return record(
        id, new DocumentEdit.Suggestion(cueId, suggestion == null ? null : suggestion.trim()));
  }

  /** Queues feedback on a cue for the reviewing agent without changing the document. */
  public StudyDocument comment(String id, String cueId, String text) throws IOException {
    var title = entry(id);
    var current = served.document(title);
    var edit = new DocumentEdit.Comment(cueId, text == null ? null : text.strip());
    DocumentEdits.apply(current, edit);
    queue.append(title, edit);
    return current;
  }

  /** Validates the entire timing batch before applying it; returns the total cue count. */
  public int saveCueTiming(String id, List<UpdateCueTiming> edits) throws IOException {
    return record(id, new DocumentEdit.Timing(edits == null ? null : List.copyOf(edits)))
        .video()
        .cues()
        .size();
  }

  public StudyDocument editCue(String id, String cueId, VideoCueEdit edit) throws IOException {
    return record(id, new DocumentEdit.EditVideoCue(cueId, edit));
  }

  public StudyDocument addCue(String id, VideoCueEdit edit) throws IOException {
    return record(id, new DocumentEdit.AddVideoCue(UUID.randomUUID().toString(), edit));
  }

  public StudyDocument splitCue(String id, String cueId, SplitCue split) throws IOException {
    return record(id, new DocumentEdit.SplitVideoCue(cueId, UUID.randomUUID().toString(), split));
  }

  /** Records the selection in playback order, whatever order the player selected it in. */
  public StudyDocument mergeCues(String id, MergeCues merge) throws IOException {
    if (merge == null || merge.ids() == null)
      throw new IllegalArgumentException("Select at least two distinct consecutive cues");
    var selected = new HashSet<>(merge.ids());
    var ordered = new ArrayList<String>();
    for (var cue : served.document(entry(id)).video().cues())
      if (selected.contains(cue.study().id())) ordered.add(cue.study().id());
    if (ordered.size() != merge.ids().size()) ordered = new ArrayList<>(merge.ids());
    return record(id, new DocumentEdit.MergeVideoCues(List.copyOf(ordered)));
  }

  public StudyDocument deleteCue(String id, String cueId) throws IOException {
    return record(id, new DocumentEdit.DeleteVideoCue(cueId));
  }

  public StudyDocument editComicText(String id, String studyId, ComicTextEdit edit)
      throws IOException {
    return record(id, new DocumentEdit.EditComicText(studyId, edit));
  }

  /** Adds a box with fresh UUIDs for the box and the one study it starts with. */
  public StudyDocument createComicCue(String id, ComicCueCreate create) throws IOException {
    return record(
        id,
        new DocumentEdit.CreateComicCue(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), create));
  }

  public StudyDocument placeComicCue(String id, String cueId, ComicCueUpdate update)
      throws IOException {
    return record(id, new DocumentEdit.PlaceComicCue(cueId, update));
  }

  public StudyDocument deleteComicCue(String id, String cueId) throws IOException {
    return record(id, new DocumentEdit.DeleteComicCue(cueId));
  }

  /** Applies the edit to the served document and queues it unless it changes nothing. */
  private StudyDocument record(String id, DocumentEdit edit) throws IOException {
    var title = entry(id);
    var current = served.document(title);
    var result = DocumentEdits.apply(current, edit);
    if (!result.equals(current)) queue.append(title, edit);
    return result;
  }

  private CatalogueEntry entry(String id) {
    return titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
  }
}
