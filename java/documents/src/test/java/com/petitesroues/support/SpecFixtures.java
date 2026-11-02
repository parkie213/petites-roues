package com.petitesroues.support;

import com.petitesroues.model.document.ComicPage;
import com.petitesroues.model.document.ComicRegion;
import com.petitesroues.model.document.PhraseAlignment;
import com.petitesroues.model.document.PhraseLink;
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

  public static ComicRegion newRegion(double x, double y, double width, double height) {
    return new ComicRegion(x, y, width, height);
  }
}
