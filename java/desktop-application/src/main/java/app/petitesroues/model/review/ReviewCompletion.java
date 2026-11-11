package app.petitesroues.model.review;

import java.util.Map;

/**
 * Closes a review batch after its edits have been written into the document file and checkpointed.
 * Contains no annotations or document snapshots; completion never writes document files.
 *
 * @param outcome decision for every edit in the batch not listed in {@code outcomes}; defaults to
 *     {@code applied}
 * @param outcomes optional per-sequence decisions, for example {@code {"12":"not_adopted"}}
 */
public record ReviewCompletion(String outcome, Map<Long, String> outcomes) {}
