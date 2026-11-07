package com.petitesroues.library;

import com.petitesroues.library.persistence.MediaStore;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.IndexedMedia;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Links titles to indexed media by fingerprint: a title's video or CBZ is a file of its kind, found
 * in any registered folder at the last refresh, whose fingerprint equals the document's reference
 * fingerprint. Nothing else links them: not sharing a folder, nor series, episode or duration. A
 * document without a reference fingerprint has no media.
 *
 * <p>When several files match (copies of one file), one in the title's own registered folder is
 * preferred, then the first by folder and path. A file that has gone since the refresh is skipped.
 * Reads the index only; Refresh does the probing.
 */
@Service
public class TitleMediaService {
  private final MediaStore media;

  public TitleMediaService(MediaStore media) {
    this.media = media;
  }

  /** The title's media, or empty when it has no reference fingerprint or no file matches. */
  public Optional<IndexedMedia> media(CatalogueEntry entry) {
    String fingerprint = fingerprint(entry);
    return fingerprint == null ? Optional.empty() : pick(entry, media.withFingerprint(fingerprint));
  }

  /** A snapshot of the whole media index, for matching every title while browsing. */
  public Index index() {
    return new Index(media.all());
  }

  /** Indexed media grouped by fingerprint. */
  public static final class Index {
    private final List<IndexedMedia> all;
    private final Map<String, List<IndexedMedia>> byFingerprint;

    private Index(List<IndexedMedia> all) {
      this.all = List.copyOf(all);
      this.byFingerprint = all.stream().collect(Collectors.groupingBy(IndexedMedia::fingerprint));
    }

    /** Every indexed file, ordered by folder and path. */
    public List<IndexedMedia> all() {
      return all;
    }

    /** The title's media, as {@link TitleMediaService#media} chooses it. */
    public Optional<IndexedMedia> match(CatalogueEntry entry) {
      String fingerprint = fingerprint(entry);
      return fingerprint == null
          ? Optional.empty()
          : pick(entry, byFingerprint.getOrDefault(fingerprint, List.of()));
    }
  }

  private static String fingerprint(CatalogueEntry entry) {
    return entry.location() == null ? null : entry.location().referenceFingerprint();
  }

  private static Optional<IndexedMedia> pick(CatalogueEntry entry, List<IndexedMedia> candidates) {
    String kind = "comic".equals(entry.location().documentType()) ? "comic" : "video";
    String repository = entry.location().repositoryId();
    return candidates.stream()
        .filter(item -> item.kind().equals(kind))
        .filter(item -> Files.isRegularFile(item.file()))
        .min(Comparator.comparing((IndexedMedia item) -> !item.repositoryId().equals(repository)));
  }
}
