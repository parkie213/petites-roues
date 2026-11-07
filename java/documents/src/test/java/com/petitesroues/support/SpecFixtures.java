package com.petitesroues.support;

import com.petitesroues.model.document.ComicBox;
import com.petitesroues.model.document.ComicPage;
import com.petitesroues.model.document.ComicRegion;
import com.petitesroues.model.document.Line;
import com.petitesroues.model.document.PhraseAlignment;
import com.petitesroues.model.document.PhraseLink;
import java.util.List;

/** Short constructors for document fixture values. */
public final class SpecFixtures {
  private SpecFixtures() {}

  public static PhraseAlignment newAlignment(
      List<String> source, List<String> translation, List<PhraseLink> links) {
    return new PhraseAlignment(source, translation, links);
  }

  public static PhraseLink newLink(List<Integer> source, List<Integer> translation, String note) {
    return new PhraseLink(source, translation, note);
  }

  public static ComicPage newPage(String id, String entry, String label, ComicBox... boxes) {
    return new ComicPage(id, entry, label, List.of(boxes));
  }

  /** A box holding one line, identified as {@code box-} plus the line's ID. */
  public static ComicBox newBubble(Line line, List<ComicRegion> regions) {
    return new ComicBox("box-" + line.id(), regions, List.of(line));
  }

  public static ComicRegion newRegion(double x, double y, double width, double height) {
    return new ComicRegion(x, y, width, height);
  }
}
