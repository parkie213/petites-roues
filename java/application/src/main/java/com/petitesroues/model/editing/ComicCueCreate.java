package com.petitesroues.model.editing;

/**
 * New bubble annotation: one region on a manifest page with optional printed text. The cue id and
 * reading order are assigned by the service, never the caller.
 */
public record ComicCueCreate(String pageId, String source, ComicRegionInput region) {}
