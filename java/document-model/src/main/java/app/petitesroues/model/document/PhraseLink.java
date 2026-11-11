package app.petitesroues.model.document;

import static app.petitesroues.model.document.Defaults.list;
import static app.petitesroues.model.document.Defaults.text;

import java.util.List;

/**
 * Zero-based source and translation chunk indices that mean the same thing, with an optional
 * learner note. A link without a note is a reviewed breakdown awaiting the notes stage.
 */
public record PhraseLink(List<Integer> source, List<Integer> translation, String note) {
  public PhraseLink {
    source = list(source);
    translation = list(translation);
    note = text(note);
  }
}
