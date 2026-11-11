package app.petitesroues.model.library.media;

import app.petitesroues.library.discovery.MediaKind;

/**
 * An indexed video or CBZ: owning repository ID, slash-separated relative file path, kind and
 * fingerprint (null if unknown). The repository ID refers to the library's repository catalogue;
 * its display name need not be loaded to match media. Local handles, probe caches, availability and
 * manual links belong to the platform, not to the file's identity.
 */
public record MediaEntry(String repositoryId, String path, MediaKind kind, String fingerprint) {}
