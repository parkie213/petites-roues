package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;
import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.function.UnaryOperator;

/** A speech bubble or caption: teaching material placed on a page at a reading position. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComicCue(StudyCue study, String pageId, int order, List<ComicRegion> regions) {
  public ComicCue {
    pageId = text(pageId);
    regions = list(regions);
  }

  public boolean hasStudy() {
    return study != null;
  }

  public ComicCue withStudy(StudyCue study) {
    return new ComicCue(study, pageId, order, regions);
  }

  public ComicCue withStudy(UnaryOperator<StudyCue> change) {
    return withStudy(change.apply(study));
  }

  public ComicCue withOrder(int order) {
    return new ComicCue(study, pageId, order, regions);
  }

  public ComicCue withRegions(List<ComicRegion> regions) {
    return new ComicCue(study, pageId, order, regions);
  }
}
