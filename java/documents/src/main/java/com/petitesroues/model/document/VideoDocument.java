package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.list;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** A video body: optional reference metadata and the timed cues, in playback order. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoDocument(
    Integer season,
    Integer episode,
    String transcriptionMethod,
    Double durationSeconds,
    List<VideoCue> cues) {
  public VideoDocument {
    cues = list(cues);
  }

  public VideoDocument withCues(List<VideoCue> cues) {
    return new VideoDocument(season, episode, transcriptionMethod, durationSeconds, cues);
  }

  /** Replaces the cue at {@code index} with {@code change} applied to it. */
  public VideoDocument withCue(int index, UnaryOperator<VideoCue> change) {
    var changed = new ArrayList<>(cues);
    changed.set(index, change.apply(cues.get(index)));
    return withCues(changed);
  }
}
