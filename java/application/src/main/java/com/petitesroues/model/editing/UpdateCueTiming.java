package com.petitesroues.model.editing;

/**
 * Requests replacement timing bounds for one existing cue. A timing-save request contains a list of
 * these operations; the editing service validates the entire list before changing any cue. Overlap
 * with other cues is allowed.
 *
 * @param id stable cue identity within the addressed study document
 * @param start replacement start in seconds from the video's beginning; required, finite and
 *     nonnegative
 * @param end replacement end in seconds from the video's beginning; required, finite and strictly
 *     after {@code start}
 */
public record UpdateCueTiming(String id, Double start, Double end) {}
