package app.petitesroues.model.progress;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PositionsTest {
  @Test
  void videoPositionsRejectInvalidSecondsAndHandleUnknownDuration() {
    for (double seconds :
        new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
      assertThrows(IllegalArgumentException.class, () -> new PlaybackPosition(seconds));
    var position = new PlaybackPosition(30);
    for (Double duration : new Double[] {null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
      assertNull(position.fraction(duration));
    assertEquals(0.5, position.fraction(60.0));
    assertEquals(1.0, position.fraction(20.0));
    assertEquals(0.0, new PlaybackPosition(0).fraction(60.0));
  }

  @Test
  void readingPositionsFollowPageIdentityThroughManifestChanges() {
    var position = new ReadingPosition("b");
    assertEquals(1, position.indexIn(List.of("a", "b", "c")));
    assertEquals(2.0 / 3, position.fractionIn(List.of("a", "b", "c")));
    assertEquals(1.0, position.fractionIn(List.of("a", "b")));
    assertEquals(0.5, position.fractionIn(List.of("b", "a")));
    assertEquals(-1, position.indexIn(List.of("a")));
    assertEquals(-1, position.indexIn(List.of()));
    assertThrows(IllegalArgumentException.class, () -> position.fractionIn(List.of("a")));
    assertThrows(IllegalArgumentException.class, () -> position.fractionIn(List.of()));
    assertThrows(IllegalArgumentException.class, () -> new ReadingPosition(null));
    assertThrows(IllegalArgumentException.class, () -> new ReadingPosition(" "));
  }
}
