package app.petitesroues.model.review;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CueChangeTest {
  @Test
  void queuedTaskRetainsItsCueIdsWhenTheCallersSelectionChanges() {
    var selection = new ArrayList<>(List.of("cue"));
    var change =
        new CueChange(
            1,
            "2026-10-20T12:00:00Z",
            "suggestion",
            selection,
            "A correction",
            null,
            null,
            null,
            null,
            null);
    selection.clear();
    assertEquals(List.of("cue"), change.cueIds());
    assertEquals("A correction", change.suggestion());
    assertThrows(UnsupportedOperationException.class, () -> change.cueIds().clear());
  }
}
