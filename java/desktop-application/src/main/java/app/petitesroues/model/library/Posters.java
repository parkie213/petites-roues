package app.petitesroues.model.library;

import app.petitesroues.library.shelves.Shelves;
import java.util.List;
import java.util.Locale;

/**
 * The Posters view, grouped by the shared rules ({@link Shelves}) so the browser only renders it.
 *
 * @param shelves the shelves that have cards, ordered by label
 * @param folders the registered folders that have cards, for the folder filter
 * @param multilingual whether the cards span more than one source language, so each names its own
 */
public record Posters(List<Shelf> shelves, List<String> folders, boolean multilingual) {
  public Posters {
    shelves = List.copyOf(shelves);
    folders = List.copyOf(folders);
  }

  /** A card image, served at {@code url}. */
  public record Art(String url) {}

  /**
   * One tome, episode or film, with its copies in other folders, preferred copy first. {@code
   * shape} is {@code portrait} or {@code landscape}; a landscape slot's art is a frame of its
   * video.
   */
  public record Slot(
      String key,
      PosterEntry entry,
      List<PosterEntry> copies,
      String label,
      String detail,
      String shape,
      Art art) {}

  /**
   * A card: a whole series, or a single title. {@code type} is {@code video} or {@code comic}.
   * {@code shape} is {@code portrait} (2:3) or {@code landscape} (16:9); the shared rules ({@link
   * Shelves}) decide it for each card, so a shelf may hold both. A landscape card's art is a frame
   * of its video, and a portrait card without art is drawn as a placeholder.
   */
  public record Group(
      String key,
      String shelf,
      String type,
      String language,
      String name,
      boolean single,
      List<Slot> slots,
      List<Integer> seasons,
      String count,
      String shape,
      Art art) {}

  /** A shelf of cards, which may mix shapes. */
  public record Shelf(String key, String label, List<Group> groups) {}

  /** The shared grouping's result, with each card image as the URL that serves it. */
  public static List<Shelf> of(List<Shelves.Shelf<PosterEntry>> shelves) {
    return shelves.stream()
        .map(
            shelf ->
                new Shelf(
                    shelf.key(),
                    shelf.label(),
                    shelf.groups().stream()
                        .map(
                            group ->
                                new Group(
                                    group.key(),
                                    group.shelf(),
                                    group.kind().id(),
                                    group.language(),
                                    group.name(),
                                    group.single(),
                                    group.slots().stream().map(Posters::slot).toList(),
                                    group.seasons(),
                                    group.count(),
                                    shape(group.shape()),
                                    art(group.art(), group.frame())))
                        .toList()))
        .toList();
  }

  private static Slot slot(Shelves.Slot<PosterEntry> slot) {
    return new Slot(
        slot.key(),
        slot.entry(),
        slot.copies(),
        slot.label(),
        slot.detail(),
        shape(slot.shape()),
        art(slot.art(), slot.frame()));
  }

  private static String shape(Shelves.Shape shape) {
    return shape.name().toLowerCase(Locale.ROOT);
  }

  /** A card's poster or cover, else a frame of the video the shared rules chose, else none. */
  private static Art art(Shelves.CardArt<PosterEntry> art, PosterEntry frame) {
    if (art != null) return new Art(art.copy().cardUrl(art.series()));
    return frame == null ? null : new Art(frame.frameUrl());
  }
}
