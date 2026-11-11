package app.petitesroues.model.editing;

/**
 * Requests two cues in place of one, with an explicit media-time boundary and source text for each
 * part. The addressed cue and local library entry are supplied by the caller, outside this payload.
 *
 * <p>The inline editor exposes a text cursor and a split time. It derives {@code first} and {@code
 * second} by slicing the displayed source text at that cursor, trimming both parts. It also sends
 * the cue's current draft start/end bounds, including any unsaved timeline corrections. These
 * derived fields do not need separate inputs in the split UI. Clients may omit the bounds to keep
 * the saved cue's outer times.
 *
 * <p>The editing service validates that both parts contain text and the split time lies strictly
 * inside the resulting outer bounds. The first part retains the original cue identity; the second
 * receives a new identity. Translation and alignment review belongs to the editing workflow.
 *
 * @param at media time in seconds at which the first cue ends and the second begins
 * @param first source text before the editor's text cursor, or explicit text from another client
 * @param second source text after the editor's text cursor, or explicit text from another client
 * @param start optional replacement start time in seconds for the first part; {@code null} retains
 *     the saved cue's start
 * @param end optional replacement end time in seconds for the second part; {@code null} retains the
 *     saved cue's end
 */
public record SplitCue(double at, String first, String second, Double start, Double end) {
  /** Requests a split while retaining the saved cue's outer timing bounds. */
  public SplitCue(double at, String first, String second) {
    this(at, first, second, null, null);
  }
}
