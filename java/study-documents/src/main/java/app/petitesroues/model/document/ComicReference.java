package app.petitesroues.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The CBZ that the boxes were drawn against. Like {@link VideoReference} it describes the material,
 * not a local copy, and every field is {@code null} when unknown.
 *
 * @param fingerprint {@code v1:} and a SHA-256 of the file size and its first and last 4 MiB, as
 *     specified in docs/DOCUMENT-FORMAT.md; it says whether a local file is this exact copy
 * @param sizeBytes the reference file's size in bytes
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComicReference(String fingerprint, Long sizeBytes) {}
