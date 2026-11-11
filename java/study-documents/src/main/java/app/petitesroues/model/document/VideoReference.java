package app.petitesroues.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The copy of a video that the cues were timed against. It describes the material, not where a copy
 * is on this machine, so it is portable; the library compares a local file with it. Every field is
 * {@code null} when unknown, for example when the original file is no longer at hand.
 *
 * @param fingerprint {@code v1:} and a SHA-256 of the file size and its first and last 4 MiB, as
 *     specified in docs/DOCUMENT-FORMAT.md; it says whether a local file is this exact copy
 * @param sizeBytes the reference file's size in bytes
 * @param durationSeconds the reference video's duration in seconds; a copy of a different cut or
 *     speed has a different duration even when its words are the same
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoReference(String fingerprint, Long sizeBytes, Double durationSeconds) {}
