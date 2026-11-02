package com.petitesroues.model.review;

/**
 * Asks the reviewing agent to check one cue, typically after a preparation script annotated it.
 *
 * @param cueId stable ID of an existing cue
 * @param text what to check, up to 2000 characters
 */
public record CueReviewRequest(String cueId, String text) {}
