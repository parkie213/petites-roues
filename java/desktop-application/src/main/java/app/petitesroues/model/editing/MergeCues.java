package app.petitesroues.model.editing;

import java.util.List;

/**
 * Requests that consecutive cues in one study document be combined. The editing service validates
 * the selection and combines cues in document order, regardless of selection order.
 *
 * @param ids stable IDs of at least two distinct, existing, consecutive cues; selection order does
 *     not affect the merged text or which cue identity is retained
 */
public record MergeCues(List<String> ids) {}
