package com.petitesroues.model.library;

import com.petitesroues.library.shelves.Shelves;
import java.util.List;

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

  /** One tome, episode or film, with its copies in other folders, preferred copy first. */
  public record Slot(
      String key,
      PosterEntry entry,
      List<PosterEntry> copies,
      String label,
      String detail,
      Art art) {}

  /** A card: a whole series, or a single title. {@code type} is {@code video} or {@code comic}. */
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
      Art art) {}

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
                                    art(group.art())))
                        .toList()))
        .toList();
  }

  private static Slot slot(Shelves.Slot<PosterEntry> slot) {
    return new Slot(
        slot.key(), slot.entry(), slot.copies(), slot.label(), slot.detail(), art(slot.art()));
  }

  private static Art art(Shelves.CardArt<PosterEntry> art) {
    return art == null ? null : new Art(art.copy().cardUrl(art.series()));
  }
}
