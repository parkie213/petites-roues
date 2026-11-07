package com.petitesroues.library.linking;

import com.petitesroues.library.discovery.MediaKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How a study document links to its media, the same on every platform: a media file of the
 * document's kind whose fingerprint equals the one the document's reference records, in any
 * registered folder. Nothing else links them: not sharing a folder, nor series, episode or
 * duration. A document without a reference fingerprint has no media.
 *
 * <p>When several files match (copies of one file), one in the document's own folder is preferred,
 * then the first in the order given, which callers keep by folder and path.
 */
public final class MediaLinks {
  private MediaLinks() {}

  /**
   * A media file as linking sees it.
   *
   * @param media the platform's own record of the file
   * @param folder the registered folder holding it, as the platform identifies folders
   */
  public record Candidate<M>(M media, MediaKind kind, String fingerprint, String folder) {}

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
   * The media a document links to among {@code ordered} candidates, or empty.
   *
   * @param kind the document's kind: a video document links to videos, a comic to CBZs
   * @param fingerprint the document's reference fingerprint, or null
   * @param documentFolder the registered folder holding the document
   */
  public static <M> Optional<M> choose(
      MediaKind kind, String fingerprint, String documentFolder, List<Candidate<M>> ordered) {
    if (fingerprint == null) return Optional.empty();
    Candidate<M> first = null;
    for (var candidate : ordered) {
      if (candidate.kind() != kind || !fingerprint.equals(candidate.fingerprint())) continue;
      if (Objects.equals(candidate.folder(), documentFolder)) return Optional.of(candidate.media());
      if (first == null) first = candidate;
    }
    return Optional.ofNullable(first).map(Candidate::media);
  }
}
