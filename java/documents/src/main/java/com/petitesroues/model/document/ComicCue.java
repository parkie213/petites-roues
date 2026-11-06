package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;
import static com.petitesroues.model.document.Defaults.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A box on a comic page at a reading position, holding the teaching material printed in it: one or
 * more studies in reading order. A speech bubble or caption usually has one study; a printed text
 * block too long for one card (a recap paragraph, a character profile) is split into one study per
 * sentence, which readers show one at a time or as a list.
 *
 * <p>{@code id} identifies the box itself and never changes with its text; box operations (moving,
 * reordering, deleting) address it. Text operations address a study by the study's own ID.
 */
public record ComicCue(
    String id, String pageId, int order, List<ComicRegion> regions, List<StudyCue> studies) {
  public ComicCue {
    id = text(id);
    pageId = text(pageId);
    regions = list(regions);
    studies = list(studies);
  }

  /** True if one of the box's studies has {@code studyId}. */
  public boolean holds(String studyId) {
    return studies.stream().anyMatch(study -> study.id().equals(studyId));
  }

  /** Replaces the study with {@code studyId}; the box must hold it. */
  public ComicCue withStudy(String studyId, UnaryOperator<StudyCue> change) {
    var changed = new ArrayList<>(studies);
    for (int i = 0; i < changed.size(); i++)
      if (changed.get(i).id().equals(studyId)) {
        changed.set(i, change.apply(changed.get(i)));
        return withStudies(changed);
      }
    throw new IllegalArgumentException("This box holds no study " + studyId);
  }

  public ComicCue withStudies(List<StudyCue> studies) {
    return new ComicCue(id, pageId, order, regions, studies);
  }

  public ComicCue withOrder(int order) {
    return new ComicCue(id, pageId, order, regions, studies);
  }

  public ComicCue withRegions(List<ComicRegion> regions) {
    return new ComicCue(id, pageId, order, regions, studies);
  }
}
