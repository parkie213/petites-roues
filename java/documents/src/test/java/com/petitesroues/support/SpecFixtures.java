package com.petitesroues.support;

import com.petitesroues.model.document.ComicCue;
import com.petitesroues.model.document.ComicPage;
import com.petitesroues.model.document.ComicRegion;
import com.petitesroues.model.document.PhraseAlignment;
import com.petitesroues.model.document.PhraseLink;
import com.petitesroues.model.document.StudyCue;
import java.util.List;

/** Short constructors for document fixture values. */
public final class SpecFixtures {
  private SpecFixtures() {}

  public static PhraseAlignment newAlignment(
      String sourceText,
      String translationText,
      List<String> source,
      List<String> translation,
      List<PhraseLink> links) {
    return new PhraseAlignment(sourceText, translationText, source, translation, links);
  }

  public static PhraseLink newLink(List<Integer> source, List<Integer> translation, String note) {
    return new PhraseLink(source, translation, note);
  }

  public static ComicPage newPage(String id, String entry, String label) {
    return new ComicPage(id, entry, label);
  }

  /** A box holding one study, identified as {@code box-} plus the study's ID. */
  public static ComicCue newBubble(
      StudyCue study, String pageId, int order, List<ComicRegion> regions) {
    return new ComicCue("box-" + study.id(), pageId, order, regions, List.of(study));
  }

  public static ComicRegion newRegion(double x, double y, double width, double height) {
    return new ComicRegion(x, y, width, height);
  }
}
