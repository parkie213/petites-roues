package app.petitesroues.model.progress;

import java.util.List;

/** A comic resume position by stable page identity, independent of manifest order and storage. */
public record ReadingPosition(String pageId) {
  public ReadingPosition {
    if (pageId == null || pageId.isBlank())
      throw new IllegalArgumentException("Page ID is required");
  }

  /** Index in the current ordered manifest, or -1 when the saved page no longer exists. */
  public int indexIn(List<String> pageIds) {
    return pageIds.indexOf(pageId);
  }

  /**
   * Fraction through the current manifest, counting the selected page: (index + 1) / page count.
   * Rejects pages absent from the manifest, including an empty manifest.
   */
  public double fractionIn(List<String> pageIds) {
    int index = indexIn(pageIds);
    if (index < 0)
      throw new IllegalArgumentException("Page is not in the current document: " + pageId);
    return (index + 1) / (double) pageIds.size();
  }
}
