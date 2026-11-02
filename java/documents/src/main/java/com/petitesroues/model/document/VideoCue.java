package com.petitesroues.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.function.UnaryOperator;

/**
 * A timed subtitle: teaching material plus inclusive {@code start} and exclusive {@code end} in
 * seconds. {@code listeningCheck} holds one open question about the spoken words, or {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoCue(StudyCue study, Double start, Double end, String listeningCheck) {
  public boolean hasStudy() {
    return study != null;
  }

  public VideoCue withStudy(StudyCue study) {
    return new VideoCue(study, start, end, listeningCheck);
  }

  public VideoCue withStudy(UnaryOperator<StudyCue> change) {
    return withStudy(change.apply(study));
  }

  public VideoCue withTiming(Double start, Double end) {
    return new VideoCue(study, start, end, listeningCheck);
  }

  public VideoCue withListeningCheck(String listeningCheck) {
    return new VideoCue(study, start, end, listeningCheck);
  }
}
