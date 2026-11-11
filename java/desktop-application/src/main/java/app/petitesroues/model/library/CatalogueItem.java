package app.petitesroues.model.library;

import java.util.Map;

/**
 * A title summary for browsing the library without opening every study document. Local absolute
 * paths are omitted; repository-relative paths retain the content's folder structure.
 *
 * <p>A registered entry remains listed when its document disappears, for example after a folder
 * move or an unavailable content drive. Retaining the entry preserves its local identity, progress
 * and reviews. Refresh reconciles its location and missing status; removing a repository explicitly
 * unregisters its entries. Document availability reflects both refresh status and the file check
 * performed when this summary was built, not a guarantee that the file can later be read.
 *
 * @param id local entry identity used by player URLs and title operations
 * @param documentId portable study-material identity, which may recur in other repositories
 * @param title registered display title
 * @param series optional series name
 * @param season optional season number
 * @param episode optional episode number
 * @param volume optional comic volume (tome) number
 * @param shelf the library shelf the document names, or null for the default for its type
 * @param sourceLanguage the document's source language (BCP 47), or null
 * @param sourceUrl optional reference to the source material
 * @param repositoryId owning repository identity, or {@code null} for a standalone registration
 * @param repositoryName owning repository display name, or {@code null} for a standalone entry
 * @param relativePath repository-relative document path, or {@code null} for a standalone entry
 * @param durationSeconds optional reference-video duration in seconds
 * @param documentAvailable whether the document is marked present and currently exists as a file
 * @param videoAvailable whether a video linked to the document (by hand or by its reference
 *     fingerprint) was indexed and still exists; this does not imply that the document is available
 * @param type indexed document discriminator: video or comic
 * @param mediaAvailable whether a video or CBZ matching the document's reference fingerprint was
 *     indexed and still exists
 * @param unavailable diagnostics in display order: {@code title} describes a missing document or,
 *     when the document is present, unmatched media; {@code audio} says a matched video has no
 *     browser-playable audio track; {@code duration} that a hand-linked video's length differs from
 *     the document's. Empty when all is well. A missing video does not prevent opening the document
 *     and its notes
 * @param posterAvailable whether the title folder has a {@code poster.*} card image
 * @param seriesPosterAvailable whether a folder above the title, below its repository root, has a
 *     {@code poster.*} image for the series card
 * @param progress how far through the title the local resume position is, from 0 to 1: a video's
 *     position over its reference duration, or a comic's (page index + 1) over its page count;
 *     {@code null} when there is no saved position, the video has no known duration, or a comic
 *     position predates stored fractions
 * @param mediaRepositoryId the folder holding the linked video or CBZ, or {@code null} when none is
 *     linked
 * @param mediaPath the linked media's path within that folder, or {@code null}
 * @param mediaLinkedByHand whether that media was linked by hand rather than by fingerprint; a
 *     hand-linked video whose length differs from the document's has a {@code duration} problem
 */
public record CatalogueItem(
    String id,
    String documentId,
    String title,
    String series,
    Integer season,
    Integer episode,
    Integer volume,
    String shelf,
    String sourceLanguage,
    String sourceUrl,
    String repositoryId,
    String repositoryName,
    String relativePath,
    Double durationSeconds,
    boolean documentAvailable,
    boolean videoAvailable,
    Map<String, String> unavailable,
    String type,
    boolean mediaAvailable,
    boolean posterAvailable,
    boolean seriesPosterAvailable,
    Double progress,
    String mediaRepositoryId,
    String mediaPath,
    boolean mediaLinkedByHand) {}
