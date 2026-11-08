package com.petitesroues.library.shelves;

import com.petitesroues.library.discovery.MediaKind;
import java.math.BigInteger;
import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groups library entries into shelves of cards, the same in the browser and on Android. A document
 * may name its shelf (any label, such as "Manga"); otherwise an entry goes on its kind's default
 * shelf:
 *
 * <ul>
 *   <li>Comics: comics, grouped by series; a series' slots are its volumes. A comic whose document
 *       names no series takes the one its media's folder names ({@link MediaNames}), as volumes
 *       still awaiting documents do.
 *   <li>Series: videos with a season or episode, grouped by series. A series whose videos have no
 *       numbers at all (a channel such as Le Parisien) is a series too, ordered by title.
 *   <li>Films: other videos, one card each, even when they name a series that has episodes.
 * </ul>
 *
 * <p>A series in several source languages is one card per language, since a learner follows one
 * language's episodes. Media without a study document has no language; it joins its series' card
 * when that series has only one language. A series card sits on the shelf most of its entries name,
 * so one stray volume does not split the series, and is named by the spelling most of them use.
 *
 * <p>A slot is one tome, episode or film; copies of it from several folders share the slot, and the
 * preferred copy is the one whose document and media are present. Entries arrive in folder order,
 * which breaks ties between equally available copies. Shelves are ordered by label and cards by
 * name, comparing numbers by value ("Tome 2" before "Tome 10").
 */
public final class Shelves {
  private Shelves() {}

  private static final Pattern PARTS = Pattern.compile("\\d+|\\D+");

  /** Locale-aware order that compares digit runs as numbers, so "Tome 2" precedes "Tome 10". */
  public static final Comparator<String> NATURAL =
      new Comparator<>() {
        private final Collator collator = collator();

        @Override
        public int compare(String a, String b) {
          List<String> x = parts(a), y = parts(b);
          for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
            String p = x.get(i), q = y.get(i);
            int order =
                Character.isDigit(p.charAt(0)) && Character.isDigit(q.charAt(0))
                    ? new BigInteger(p).compareTo(new BigInteger(q))
                    : collator.compare(p, q);
            if (order != 0) return order;
          }
          int order = Integer.compare(x.size(), y.size());
          return order != 0 ? order : collator.compare(a, b);
        }
      };

  /**
   * A card image: {@code copy}'s own art, or with {@code series} the poster in the folder above it.
   * Each platform turns the copy into its own image reference.
   */
  public record CardArt<T>(T copy, boolean series) {}

  /**
   * One tome, episode or film.
   *
   * @param entry the preferred copy
   * @param copies every copy, preferred first
   * @param detail "S1 · E2" for numbered videos, else null
   * @param art the first copy with its own art, or null
   */
  public record Slot<T>(
      String key, T entry, List<T> copies, String label, String detail, CardArt<T> art) {}

  /**
   * A card: a whole series, or a {@code single} title.
   *
   * @param key unique across shelves: the shelf's key, then the series or slot
   * @param shelf the key of the shelf the card is on
   * @param language the source language most of its entries declare, or null
   * @param seasons the seasons its slots have, in order
   * @param count "3 tomes", "2 seasons · 12 episodes"; null for a single title
   */
  public record Group<T>(
      String key,
      String shelf,
      MediaKind kind,
      String language,
      String name,
      boolean single,
      List<Slot<T>> slots,
      List<Integer> seasons,
      String count,
      CardArt<T> art) {}

  /**
   * A shelf with its cards sorted by name; {@code key} matches labels ignoring case and spacing.
   */
  public record Shelf<T>(String key, String label, List<Group<T>> groups) {}

  /** Series and shelf names match ignoring case, spacing and Unicode composition. */
  public static String seriesKey(String name) {
    return Normalizer.normalize(name, Normalizer.Form.NFC)
        .strip()
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  /** The shelves that have cards, ordered by label, each with its cards sorted by name. */
  public static <T extends Shelvable> List<Shelf<T>> shelves(List<T> entries) {
    List<Placed<T>> placed = new ArrayList<>();
    List<Entry<T>> comics = new ArrayList<>(), videos = new ArrayList<>();
    for (T item : entries)
      if (item.kind() == MediaKind.COMIC) comics.add(new Entry<>(item, comicSeries(item)));
      else videos.add(new Entry<>(item, item.series()));

    for (var group : bySeries(withSeries(comics, true)).entrySet())
      placed.add(place(seriesGroup(MediaKind.COMIC, group.getKey(), group.getValue()), "Comics"));
    for (var group : singleGroups(MediaKind.COMIC, withSeries(comics, false)))
      placed.add(place(group, "Comics"));

    List<Entry<T>> loose = new ArrayList<>(withSeries(videos, false));
    for (var series : bySeries(withSeries(videos, true)).entrySet()) {
      List<Entry<T>> group = series.getValue();
      List<Entry<T>> numbered = group.stream().filter(e -> numbered(e.item)).toList();
      if (numbered.isEmpty()) {
        placed.add(place(seriesGroup(MediaKind.VIDEO, series.getKey(), group), "Series"));
        continue;
      }
      placed.add(place(seriesGroup(MediaKind.VIDEO, series.getKey(), numbered), "Series"));
      group.stream().filter(e -> !numbered(e.item)).forEach(loose::add);
    }
    for (var group : singleGroups(MediaKind.VIDEO, loose)) placed.add(place(group, "Films"));

    Map<String, List<String>> labels = new LinkedHashMap<>();
    Map<String, List<Group<T>>> groups = new LinkedHashMap<>();
    for (var item : placed) {
      String key = seriesKey(item.label);
      labels.computeIfAbsent(key, k -> new ArrayList<>()).add(item.label);
      var group = item.group;
      groups
          .computeIfAbsent(key, k -> new ArrayList<>())
          .add(
              new Group<>(
                  key + ":" + group.key(),
                  key,
                  group.kind(),
                  group.language(),
                  group.name(),
                  group.single(),
                  group.slots(),
                  group.seasons(),
                  group.count(),
                  group.art()));
    }
    List<Shelf<T>> result = new ArrayList<>();
    for (var key : groups.keySet()) {
      var cards = new ArrayList<>(groups.get(key));
      cards.sort(Comparator.comparing(Group::name, Comparator.nullsLast(NATURAL)));
      result.add(new Shelf<>(key, mostCommon(labels.get(key)), List.copyOf(cards)));
    }
    result.sort(Comparator.comparing(Shelf::label, NATURAL));
    return List.copyOf(result);
  }

  /** An entry with the series it is grouped under. */
  private record Entry<T extends Shelvable>(T item, String series) {}

  private record Placed<T>(String label, Group<T> group) {}

  private static String comicSeries(Shelvable item) {
    if (hasText(item.series()) || item.mediaPath() == null) return item.series();
    return MediaNames.fromPath(item.mediaPath()).series();
  }

  private static <T extends Shelvable> List<Entry<T>> withSeries(
      List<Entry<T>> entries, boolean wanted) {
    return entries.stream().filter(e -> hasText(e.series) == wanted).toList();
  }

  /** The shelf a card belongs on: the label most of its copies name, else its kind's default. */
  private static <T extends Shelvable> Placed<T> place(Group<T> group, String fallback) {
    List<String> labels = new ArrayList<>();
    for (var slot : group.slots())
      for (var copy : slot.copies())
        labels.add(hasText(copy.shelf()) ? copy.shelf().strip() : fallback);
    return new Placed<>(mostCommon(labels), group);
  }

  /**
   * Groups entries by series and source language. Entries without a language join their series'
   * only language; with several, they keep a card of their own.
   */
  private static <T extends Shelvable> Map<String, List<Entry<T>>> bySeries(
      List<Entry<T>> entries) {
    Map<String, Set<String>> languages = new LinkedHashMap<>();
    for (var entry : entries) {
      var known = languages.computeIfAbsent(seriesKey(entry.series), k -> new LinkedHashSet<>());
      if (entry.item.sourceLanguage() != null) known.add(entry.item.sourceLanguage());
    }
    Map<String, List<Entry<T>>> groups = new LinkedHashMap<>();
    for (var entry : entries) {
      String series = seriesKey(entry.series);
      var known = languages.get(series);
      String language = entry.item.sourceLanguage();
      if (language == null && known.size() == 1) language = known.iterator().next();
      String key = language != null ? series + "@" + language : series;
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
    }
    return groups;
  }

  private static <T extends Shelvable> Group<T> seriesGroup(
      MediaKind kind, String key, List<Entry<T>> entries) {
    List<T> items = entries.stream().map(Entry::item).toList();
    var slots = makeSlots(items);
    var seasons = new TreeSet<Integer>();
    for (var slot : slots) if (slot.entry().season() != null) seasons.add(slot.entry().season());
    String unit =
        kind == MediaKind.COMIC
            ? "tome"
            : items.stream().anyMatch(Shelves::numbered) ? "episode" : "video";
    T poster =
        slots.stream()
            .flatMap(slot -> slot.copies().stream())
            .filter(Shelvable::hasSeriesPoster)
            .findFirst()
            .orElse(null);
    String count =
        seasons.size() > 1
            ? plural(seasons.size(), "season") + " · " + plural(slots.size(), unit)
            : plural(slots.size(), unit);
    return new Group<>(
        key,
        null,
        kind,
        languageOf(items),
        mostCommon(entries.stream().map(e -> e.series.strip()).toList()),
        false,
        slots,
        List.copyOf(seasons),
        count,
        poster != null
            ? new CardArt<>(poster, true)
            : slots.stream().map(Slot::art).filter(Objects::nonNull).findFirst().orElse(null));
  }

  private static <T extends Shelvable> List<Group<T>> singleGroups(
      MediaKind kind, List<Entry<T>> entries) {
    List<Group<T>> groups = new ArrayList<>();
    for (var slot : makeSlots(entries.stream().map(Entry::item).toList())) {
      // A film in its own folder often has only that folder's poster, which is its series poster.
      CardArt<T> art = slot.art();
      if (art == null)
        art =
            slot.copies().stream()
                .filter(Shelvable::hasSeriesPoster)
                .findFirst()
                .map(copy -> new CardArt<>(copy, true))
                .orElse(null);
      groups.add(
          new Group<>(
              slot.key(),
              null,
              kind,
              languageOf(slot.copies()),
              slot.entry().title(),
              true,
              List.of(slot),
              List.of(),
              null,
              art));
    }
    return groups;
  }

  private static <T extends Shelvable> List<Slot<T>> makeSlots(List<T> entries) {
    Map<String, List<T>> byKey = new LinkedHashMap<>();
    for (T entry : entries)
      byKey.computeIfAbsent(slotKey(entry), k -> new ArrayList<>()).add(entry);
    List<Slot<T>> slots = new ArrayList<>();
    for (var copies : byKey.entrySet()) {
      // Stable sort: equally ready copies keep folder order.
      var ordered = new ArrayList<>(copies.getValue());
      ordered.sort(Comparator.comparingInt(Shelves::ready).reversed());
      T entry = ordered.get(0);
      CardArt<T> art =
          ordered.stream()
              .filter(Shelvable::hasTitleArt)
              .findFirst()
              .map(copy -> new CardArt<>(copy, false))
              .orElse(null);
      slots.add(
          new Slot<>(
              copies.getKey(),
              entry,
              List.copyOf(ordered),
              slotLabel(entry),
              slotDetail(entry),
              art));
    }
    slots.sort(SLOT_ORDER);
    return List.copyOf(slots);
  }

  /** Copies share a slot only within one language: another language's tome 1 is not a copy. */
  private static String slotKey(Shelvable entry) {
    String language = entry.sourceLanguage() != null ? entry.sourceLanguage() + ":" : "";
    if (entry.kind() == MediaKind.COMIC && entry.volume() != null)
      return language + "v" + entry.volume();
    if (entry.kind() != MediaKind.COMIC && numbered(entry))
      return language
          + "s"
          + (entry.season() == null ? "" : entry.season())
          + "e"
          + (entry.episode() == null ? "" : entry.episode());
    return "d:" + entry.documentId();
  }

  private static final Comparator<Slot<? extends Shelvable>> SLOT_ORDER =
      Comparator.<Slot<? extends Shelvable>>comparingInt(
              slot -> or(slot.entry().volume(), Integer.MAX_VALUE))
          .thenComparingInt(slot -> or(slot.entry().season(), 0))
          .thenComparingInt(slot -> or(slot.entry().episode(), Integer.MAX_VALUE))
          .thenComparing(slot -> slot.entry().title(), Comparator.nullsLast(NATURAL));

  private static String slotLabel(Shelvable entry) {
    if (entry.kind() == MediaKind.COMIC && entry.volume() != null) return "Tome " + entry.volume();
    return entry.title();
  }

  private static String slotDetail(Shelvable entry) {
    if (entry.kind() == MediaKind.COMIC || !numbered(entry)) return null;
    List<String> parts = new ArrayList<>();
    if (entry.season() != null) parts.add("S" + entry.season());
    if (entry.episode() != null) parts.add("E" + entry.episode());
    return String.join(" · ", parts);
  }

  /** The language most of the entries declare, or null when none declares one. */
  private static String languageOf(List<? extends Shelvable> entries) {
    var tags = entries.stream().map(Shelvable::sourceLanguage).filter(Objects::nonNull).toList();
    return tags.isEmpty() ? null : mostCommon(tags);
  }

  /** The most frequent value; ties go to the first seen. */
  private static String mostCommon(List<String> values) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String value : values) counts.merge(value, 1, Integer::sum);
    String best = null;
    int most = 0;
    for (var count : counts.entrySet())
      if (count.getValue() > most) {
        best = count.getKey();
        most = count.getValue();
      }
    return best;
  }

  private static int ready(Shelvable entry) {
    if (!entry.documentAvailable()) return 0;
    return entry.mediaAvailable() ? 2 : 1;
  }

  private static boolean numbered(Shelvable entry) {
    return entry.season() != null || entry.episode() != null;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static int or(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private static String plural(int n, String word) {
    return n + " " + word + (n == 1 ? "" : "s");
  }

  private static Collator collator() {
    Collator collator = Collator.getInstance(Locale.ROOT);
    collator.setStrength(Collator.SECONDARY);
    return collator;
  }

  private static List<String> parts(String text) {
    List<String> parts = new ArrayList<>();
    Matcher matcher = PARTS.matcher(text);
    while (matcher.find()) parts.add(matcher.group());
    return parts;
  }
}
