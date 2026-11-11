package app.petitesroues.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.function.UnaryOperator;

/**
 * A timed subtitle: one line plus inclusive {@code start} and exclusive {@code end} in seconds.
 * {@code listeningCheck} holds one open question about the spoken words, or {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoCue(Line line, Double start, Double end, String listeningCheck) {
  public boolean hasLine() {
    return line != null;
  }

  public VideoCue withLine(Line line) {
    return new VideoCue(line, start, end, listeningCheck);
  }

  public VideoCue withLine(UnaryOperator<Line> change) {
    return withLine(change.apply(line));
  }

  public VideoCue withTiming(Double start, Double end) {
    return new VideoCue(line, start, end, listeningCheck);
  }

  public VideoCue withListeningCheck(String listeningCheck) {
    return new VideoCue(line, start, end, listeningCheck);
  }
}
