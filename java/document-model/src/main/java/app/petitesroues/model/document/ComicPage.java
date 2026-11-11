package app.petitesroues.model.document;

import static app.petitesroues.model.document.Defaults.list;
import static app.petitesroues.model.document.Defaults.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One page image inside the title's CBZ, with an optional printed page label and the text boxes
 * printed on it in reading order. A box's position in {@code boxes} is its reading order on the
 * page.
 */
public record ComicPage(String id, String entry, String label, List<ComicBox> boxes) {

  public ComicPage {
    id = text(id);
    entry = text(entry);
    boxes = list(boxes);
  }

  /** A page with no boxes yet. */
  public ComicPage(String id, String entry, String label) {
    this(id, entry, label, List.of());
  }

  public ComicPage withBoxes(List<ComicBox> boxes) {
    return new ComicPage(id, entry, label, boxes);
  }

  /** Replaces the box at {@code index} with {@code change} applied to it. */
  public ComicPage withBox(int index, UnaryOperator<ComicBox> change) {
    var changed = new ArrayList<>(boxes);
    changed.set(index, change.apply(boxes.get(index)));
    return withBoxes(changed);
  }
}
