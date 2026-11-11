package app.petitesroues.model.document;

/**
 * The CBZ that the boxes were drawn against. Like {@link VideoReference} it describes the material,
 * not a local copy, and every field is {@code null} when unknown.
 *
 * @param fingerprint {@code v1:} and a SHA-256 of the file size and its first and last 4 MiB, as
 *     specified in docs/DOCUMENT-FORMAT.md; it says whether a local file is this exact copy
 * @param sizeBytes the reference file's size in bytes
 */
public record ComicReference(String fingerprint, Long sizeBytes) {}
