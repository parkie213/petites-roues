package app.petitesroues.model.review;

import app.petitesroues.model.editing.DocumentEdit;
import java.util.List;

/**
 * One queued player edit and its review. While pending, its {@code command} is part of the served
 * copy, which was built by replaying the queue over the accepted copy; an agent writes it into the
 * file and then completes the batch that claimed it.
 *
 * @param sequence local task identity
 * @param timestamp time the edit was queued, in UTC
 * @param kind timing, suggestion, edit, add, delete, split, merge or annotate
 * @param cueIds affected cue identities, including removed IDs after a merge or deletion
 * @param suggestion proposed correction text for a suggestion, otherwise {@code null}
 * @param reviewedAt completion time in UTC, or {@code null} while pending
 * @param outcome decision recorded on completion, or {@code null} while pending
 * @param batch claiming review batch, or {@code null} while unclaimed
 * @param state for pending rows: {@code pending} (in the served copy), {@code conflict} (no longer
 *     applies, so left out of it; stored by the last rebuild) or {@code review} (queued before
 *     edits were replayed; already in the document); {@code null} for completed rows
 * @param command the edit itself; {@code null} for {@code review} rows
 */
public record CueChange(
    long sequence,
    String timestamp,
    String kind,
    List<String> cueIds,
    String suggestion,
    String reviewedAt,
    String outcome,
    Long batch,
    String state,
    DocumentEdit command) {
  public CueChange {
    cueIds = List.copyOf(cueIds);
  }

  public CueChange withState(String state) {
    return new CueChange(
        sequence, timestamp, kind, cueIds, suggestion, reviewedAt, outcome, batch, state, command);
  }
}
