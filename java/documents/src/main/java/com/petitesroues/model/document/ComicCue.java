package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;
import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A speech bubble, caption or one sentence of a printed text block: teaching material placed on a
 * page at a reading position.
 *
 * <p>{@code block} is absent for an ordinary bubble. Cues on one page that share a {@code block}
 * are the sentences of one printed text block, in order: they carry the block's identical regions
 * and occupy contiguous page orders, so readers draw the block once and step or read through its
 * sentences.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComicCue(
    StudyCue study, String pageId, int order, List<ComicRegion> regions, String block) {
  public ComicCue {
    pageId = text(pageId);
    regions = list(regions);
    block = block == null || block.isBlank() ? null : block;
  }

  /** An ordinary bubble outside any text block. */
  public ComicCue(StudyCue study, String pageId, int order, List<ComicRegion> regions) {
    this(study, pageId, order, regions, null);
  }

  public boolean hasStudy() {
    return study != null;
  }

  public boolean inBlock() {
    return block != null;
  }

  public ComicCue withStudy(StudyCue study) {
    return new ComicCue(study, pageId, order, regions, block);
  }

  public ComicCue withStudy(UnaryOperator<StudyCue> change) {
    return withStudy(change.apply(study));
  }

  public ComicCue withOrder(int order) {
    return new ComicCue(study, pageId, order, regions, block);
  }

  public ComicCue withRegions(List<ComicRegion> regions) {
    return new ComicCue(study, pageId, order, regions, block);
  }

  public ComicCue withBlock(String block) {
    return new ComicCue(study, pageId, order, regions, block);
  }
}
