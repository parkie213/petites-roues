package app.petitesroues.model.document;

import static app.petitesroues.model.document.Defaults.list;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A video body: optional season and episode numbers, the reference copy the cues were timed
 * against, and the timed cues in playback order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoDocument(
    Integer season, Integer episode, VideoReference reference, List<VideoCue> cues)
    implements StudyBody {
  public VideoDocument {
    cues = list(cues);
  }

  public VideoDocument withCues(List<VideoCue> cues) {
    return new VideoDocument(season, episode, reference, cues);
  }

  public VideoDocument withReference(VideoReference reference) {
    return new VideoDocument(season, episode, reference, cues);
  }

  /** Replaces the cue at {@code index} with {@code change} applied to it. */
  public VideoDocument withCue(int index, UnaryOperator<VideoCue> change) {
    var changed = new ArrayList<>(cues);
    changed.set(index, change.apply(cues.get(index)));
    return withCues(changed);
  }
}
