package app.petitesroues.library;

import app.petitesroues.library.discovery.MediaKind;
import app.petitesroues.library.linking.MediaLinks;
import app.petitesroues.library.persistence.MediaLinkStore;
import app.petitesroues.library.persistence.MediaStore;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.library.IndexedMedia;
import app.petitesroues.model.library.MediaLink;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Links titles to indexed media by the library's shared rule ({@link MediaLinks}): a file the user
 * linked to the title by hand, else a file of the title's kind, found in any registered folder at
 * the last refresh and not linked by hand to anything, whose fingerprint equals the document's
 * reference fingerprint. Among several, a copy in the title's own folder first, then the first by
 * folder and path. A file that has gone since the refresh is skipped. Reads the index only; Refresh
 * does the probing.
 */
@Service
public class TitleMediaService {
  private final MediaStore media;
  private final MediaLinkStore links;

  public TitleMediaService(MediaStore media, MediaLinkStore links) {
    this.media = media;
    this.links = links;
  }

  /**
   * The title's media, or empty when nothing is linked to it and no file matches. Reads only the
   * rows that could qualify, since players call this for every range request.
   */
  public Optional<IndexedMedia> media(CatalogueEntry entry) {
    if (entry.location() == null) return Optional.empty();
    var manual = links.all();
    var rows = new ArrayList<IndexedMedia>();
    for (var link : manual)
      if (link.titleId().equals(entry.id()))
        media.find(link.repositoryId(), link.relativePath()).ifPresent(rows::add);
    String fingerprint = entry.location().referenceFingerprint();
    if (fingerprint != null) rows.addAll(media.withFingerprint(fingerprint));
    return new Index(rows.stream().distinct().toList(), manual).match(entry);
  }

  /** A snapshot of the whole media index and its manual links, for matching every title. */
  public Index index() {
    return new Index(media.all(), links.all());
  }

  /** Indexed media with their manual links. */
  public static final class Index {
    private final List<IndexedMedia> all;
    private final List<MediaLinks.Candidate<IndexedMedia>> candidates;
    private final Map<String, List<MediaLinks.Candidate<IndexedMedia>>> byKey;
    private final Map<String, List<MediaLinks.Candidate<IndexedMedia>>> byLink = new HashMap<>();

    private Index(List<IndexedMedia> all, List<MediaLink> manual) {
      this.all = List.copyOf(all);
      var linked = new HashMap<String, String>();
      for (var link : manual)
        linked.put(path(link.repositoryId(), link.relativePath()), link.titleId());
      this.candidates =
          all.stream()
              .map(
                  item ->
                      new MediaLinks.Candidate<>(
                          item,
                          kind(item.kind()),
                          item.fingerprint(),
                          item.repositoryId(),
                          linked.get(path(item.repositoryId(), item.relativePath()))))
              .toList();
      this.byKey = MediaLinks.byKey(candidates);
      for (var candidate : candidates)
        if (candidate.linkedTo() != null)
          byLink.computeIfAbsent(candidate.linkedTo(), ignored -> new ArrayList<>()).add(candidate);
    }

    /** Every indexed file, ordered by folder and path. */
    public List<IndexedMedia> all() {
      return all;
    }

    /** Every indexed file as the shared rule sees it, with its manual link, in the same order. */
    public List<MediaLinks.Candidate<IndexedMedia>> candidates() {
      return candidates;
    }

    /** The title's media, as {@link TitleMediaService#media} chooses it. */
    public Optional<IndexedMedia> match(CatalogueEntry entry) {
      if (entry.location() == null) return Optional.empty();
      var kind = kind(entry.location().documentType());
      String fingerprint = entry.location().referenceFingerprint();
      var relevant = new ArrayList<>(byLink.getOrDefault(entry.id(), List.of()));
      if (fingerprint != null)
        relevant.addAll(byKey.getOrDefault(MediaLinks.key(kind, fingerprint), List.of()));
      return MediaLinks.choose(
          entry.id(),
          kind,
          fingerprint,
          entry.location().repositoryId(),
          relevant.stream().filter(c -> Files.isRegularFile(c.media().file())).toList());
    }

    /** Whether the title's media is linked by hand rather than by fingerprint. */
    public boolean linkedByHand(CatalogueEntry entry, IndexedMedia item) {
      return byLink.getOrDefault(entry.id(), List.of()).stream()
          .anyMatch(candidate -> candidate.media().equals(item));
    }
  }

  private static String path(String repositoryId, String relativePath) {
    return repositoryId + "\n" + relativePath;
  }

  /** The media kind of a document type or an indexed kind: {@code comic}, else video. */
  static MediaKind kind(String type) {
    return "comic".equals(type) ? MediaKind.COMIC : MediaKind.VIDEO;
  }
}
