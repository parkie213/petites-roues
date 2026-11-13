package app.petitesroues.sync;

import app.petitesroues.sync.model.DesktopTitle;
import app.petitesroues.sync.model.TitleFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How a phone's download folder compares with the desktop's titles, one {@link Group} per series
 * (or standalone title), and what syncing fetches: updates to every title already on the phone,
 * every title of a followed group that is not there yet, and titles chosen one by one. Unfollowing
 * a group keeps its titles and their updates; nothing is ever deleted.
 *
 * @param groups the desktop's titles by {@link DesktopTitle#group()}, in the desktop's order
 */
public record SyncStatus(List<Group> groups) {
  /** Where a title stands on the phone. */
  public enum State {
    /** The desktop cannot offer it yet ({@link DesktopTitle#problem()}). */
    PROBLEM,
    /** None of its own files is on the phone. */
    MISSING,
    /** It is on the phone, but some of its files are missing or differ from the desktop's. */
    UPDATE,
    /** Every file is on the phone and the same as the desktop's. */
    UP_TO_DATE
  }

  /**
   * One title and where it stands.
   *
   * @param remaining bytes still to download for it, including the rest of a resumable part
   * @param chosen whether it was chosen on its own, to copy without following its group
   */
  public record Entry(DesktopTitle title, State state, long remaining, boolean chosen) {}

  /**
   * A series, or a title standing alone.
   *
   * @param key its {@link DesktopTitle#group()}
   * @param name what to call it: the series, or the title
   * @param followed whether the phone fetches its new titles
   */
  public record Group(String key, String name, boolean followed, List<Entry> entries) {
    /** Whether syncing fetches this entry. */
    public boolean fetches(Entry entry) {
      return entry.state() == State.UPDATE
          || (entry.state() == State.MISSING && (followed || entry.chosen()));
    }

    /** The entries syncing fetches. */
    public List<Entry> toSync() {
      return entries.stream().filter(this::fetches).toList();
    }

    /** How many entries are in {@code state}. */
    public int count(State state) {
      return (int) entries.stream().filter(entry -> entry.state() == state).count();
    }

    /** Whether it can be followed: some title in it has no problem. */
    public boolean followable() {
      return count(State.PROBLEM) < entries.size();
    }
  }

  /**
   * Compares {@code titles} with what is in {@code folder}.
   *
   * @param followed the {@link DesktopTitle#group()} keys the phone follows
   * @param chosen the IDs of titles chosen on their own
   * @throws IllegalArgumentException if the desktop names a path outside the folder
   */
  public static SyncStatus of(
      List<DesktopTitle> titles, Set<String> followed, Set<String> chosen, TransferFolder folder)
      throws IOException {
    Map<String, List<Entry>> byGroup = new LinkedHashMap<>();
    Map<String, String> names = new LinkedHashMap<>();
    for (DesktopTitle title : titles) {
      byGroup
          .computeIfAbsent(title.group(), key -> new ArrayList<>())
          .add(entry(title, chosen.contains(title.id()), folder));
      names.putIfAbsent(title.group(), title.series() != null ? title.series() : title.title());
    }
    List<Group> groups = new ArrayList<>();
    byGroup.forEach(
        (key, entries) ->
            groups.add(
                new Group(key, names.get(key), followed.contains(key), List.copyOf(entries))));
    return new SyncStatus(List.copyOf(groups));
  }

  private static Entry entry(DesktopTitle title, boolean chosen, TransferFolder folder)
      throws IOException {
    if (title.problem() != null) return new Entry(title, State.PROBLEM, 0, chosen);
    TransferPlan plan = TransferPlan.of(List.of(title), folder);
    if (plan.steps().isEmpty()) return new Entry(title, State.UP_TO_DATE, 0, chosen);
    State state = onPhone(title, folder) ? State.UPDATE : State.MISSING;
    return new Entry(title, state, plan.remaining(), chosen);
  }

  /**
   * Whether any of the title's own files is finished in the folder. A series poster is shared by
   * its episodes, so it does not count.
   */
  private static boolean onPhone(DesktopTitle title, TransferFolder folder) throws IOException {
    for (TitleFile file : title.files())
      if (!TitleFile.SERIES_POSTER.equals(file.role()) && folder.size(file.segments()) >= 0)
        return true;
    return false;
  }

  /** The titles syncing fetches, in the desktop's order. */
  public List<DesktopTitle> toSync() {
    return groups.stream().flatMap(group -> group.toSync().stream()).map(Entry::title).toList();
  }

  /**
   * The chosen titles still waiting to be copied. Once any of a title's files is on the phone it is
   * fetched as an update, so the rest need not stay chosen.
   */
  public Set<String> stillChosen() {
    return groups.stream()
        .flatMap(group -> group.entries().stream())
        .filter(entry -> entry.chosen() && entry.state() == State.MISSING)
        .map(entry -> entry.title().id())
        .collect(Collectors.toSet());
  }

  /** Bytes syncing downloads. */
  public long remaining() {
    return groups.stream()
        .flatMap(group -> group.toSync().stream())
        .mapToLong(Entry::remaining)
        .sum();
  }
}
