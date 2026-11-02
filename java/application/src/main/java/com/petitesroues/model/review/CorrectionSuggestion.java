package com.petitesroues.model.review;

/**
 * A proposed hearing for later assessment. The cue ID is supplied by the request path; the editing
 * service validates this text and attaches it to the current cue without replacing dialogue.
 *
 * @param suggestion description of the possible correction; required, nonblank, at most 2000
 *     characters
 */
public record CorrectionSuggestion(String suggestion) {}
