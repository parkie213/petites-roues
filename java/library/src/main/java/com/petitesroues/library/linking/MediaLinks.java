package com.petitesroues.library.linking;

import com.petitesroues.library.discovery.MediaKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * How a study document links to its media, the same on every platform. Two ways, in order:
 *
 * <ol>
 *   <li>A manual link: the user chose this media file for this document. It is local library state
 *       kept by the platform, keyed by the file's path, and wins on both sides: the document uses
 *       the file, and the file belongs to that document even when its fingerprint matches another.
 *   <li>The fingerprint: a media file of the document's kind, not manually linked to anything,
 *       whose fingerprint equals the one the document's reference records, in any registered
 *       folder.
 * </ol>
 *
 * Nothing else links them: not sharing a folder, nor series, episode or duration. A document
 * without a reference fingerprint has media only by a manual link.
 *
 * <p>When several files qualify (copies, or several manual links), one in the document's own folder
 * is preferred, then the first in the order given, which callers keep by folder and path.
 */
public final class MediaLinks {
  private MediaLinks() {}

  /**
   * A media file as linking sees it.
   *
   * @param media the platform's own record of the file
   * @param folder the registered folder holding it, as the platform identifies folders
   * @param linkedTo the document it is manually linked to, as the platform identifies documents, or
   *     null when it is not
   */
  public record Candidate<M>(
      M media, MediaKind kind, String fingerprint, String folder, String linkedTo) {
    /** A file with no manual link. */
    public Candidate(M media, MediaKind kind, String fingerprint, String folder) {
      this(media, kind, fingerprint, folder, null);
    }

    /** Whether a document of this kind with this reference links to this file by fingerprint. */
    boolean freeFor(MediaKind documentKind, String reference) {
      return linkedTo == null && kind == documentKind && reference.equals(fingerprint);
    }
  }

  /** The key a document and its media share, or null for a document without a fingerprint. */
  public static String key(MediaKind kind, String fingerprint) {
    return fingerprint == null ? null : kind.id() + ":" + fingerprint;
  }

  /** Groups candidates by {@link #key}, keeping their order, so each document's lookup is cheap. */
  public static <M> Map<String, List<Candidate<M>>> byKey(List<Candidate<M>> ordered) {
    Map<String, List<Candidate<M>>> groups = new LinkedHashMap<>();
    for (var candidate : ordered)
      groups
          .computeIfAbsent(
              key(candidate.kind(), candidate.fingerprint()), ignored -> new ArrayList<>())
          .add(candidate);
    return groups;
  }

  /**
   * The media a document links to by fingerprint among {@code ordered} candidates, or empty; for a
   * platform without manual links.
   *
   * @param kind the document's kind: a video document links to videos, a comic to CBZs
   * @param fingerprint the document's reference fingerprint, or null
   * @param documentFolder the registered folder holding the document
   */
  public static <M> Optional<M> choose(
      MediaKind kind, String fingerprint, String documentFolder, List<Candidate<M>> ordered) {
    return choose(null, kind, fingerprint, documentFolder, ordered);
  }

  /**
   * The media a document links to among {@code ordered} candidates, or empty: its manually linked
   * files if any, else the files its fingerprint matches that are not manually linked.
   *
   * @param document the document's identity, matched against {@link Candidate#linkedTo}; null when
   *     the platform has no manual links
   * @param kind the document's kind: a video document links to videos, a comic to CBZs
   * @param fingerprint the document's reference fingerprint, or null
   * @param documentFolder the registered folder holding the document
   */
  public static <M> Optional<M> choose(
      String document,
      MediaKind kind,
      String fingerprint,
      String documentFolder,
      List<Candidate<M>> ordered) {
    if (document != null) {
      var linked =
          prefer(
              documentFolder,
              ordered.stream()
                  .filter(c -> document.equals(c.linkedTo()) && c.kind() == kind)
                  .toList());
      if (linked.isPresent()) return linked;
    }
    if (fingerprint == null) return Optional.empty();
    return prefer(
        documentFolder, ordered.stream().filter(c -> c.freeFor(kind, fingerprint)).toList());
  }

  /**
   * The document a media file belongs to: the one it is manually linked to, else the one {@code
   * byKey} gives for its {@link #key} (the document referencing that fingerprint), or null.
   */
  public static String owner(Candidate<?> media, Function<String, String> byKey) {
    if (media.linkedTo() != null) return media.linkedTo();
    return byKey.apply(key(media.kind(), media.fingerprint()));
  }

  private static <M> Optional<M> prefer(String documentFolder, List<Candidate<M>> qualifying) {
    for (var candidate : qualifying)
      if (Objects.equals(candidate.folder(), documentFolder)) return Optional.of(candidate.media());
    return qualifying.stream().findFirst().map(Candidate::media);
  }
}
