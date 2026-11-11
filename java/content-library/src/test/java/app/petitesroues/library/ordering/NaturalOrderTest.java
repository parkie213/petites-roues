package app.petitesroues.library.ordering;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NaturalOrderTest {
  @Test
  void comparesWholeDigitRunsAcrossWritingSystems() {
    for (var pair :
        List.of(
            List.of("Tome 2", "Tome 10"),
            List.of("١a", "٢b"),
            List.of("第２話", "第１０話"),
            List.of("page-𝟚", "page-𝟙𝟘"),
            List.of("page-" + "9".repeat(300), "page-1" + "0".repeat(300)))) {
      assertTrue(NaturalOrder.COMPARATOR.compare(pair.get(0), pair.get(1)) < 0, pair.toString());
      assertTrue(NaturalOrder.COMPARATOR.compare(pair.get(1), pair.get(0)) > 0, pair.toString());
    }
  }

  @Test
  void preservesCaseAccentAndLeadingZeroBehaviour() {
    assertEquals(0, NaturalOrder.COMPARATOR.compare("Tome", "tome"));
    assertNotEquals(0, NaturalOrder.COMPARATOR.compare("cote", "côte"));
    var values = new ArrayList<>(List.of("page-10", "page-2", "page-02"));
    values.sort(NaturalOrder.COMPARATOR);
    assertEquals(List.of("page-02", "page-2", "page-10"), values);
  }

  @Test
  void definesConsistentOrderingForMixedUnicodeNames() {
    var names =
        List.of(
            "",
            "1",
            "2",
            "١",
            "٢",
            "a",
            "١a",
            "٢b",
            "第２話",
            "第１０話",
            "page-𝟚",
            "page-𝟙𝟘",
            "Tome 2",
            "tome 02",
            "Tome 10",
            "cote",
            "côte");
    for (String a : names)
      for (String b : names) {
        assertEquals(
            -Integer.signum(NaturalOrder.COMPARATOR.compare(b, a)),
            Integer.signum(NaturalOrder.COMPARATOR.compare(a, b)));
        for (String c : names)
          if (NaturalOrder.COMPARATOR.compare(a, b) <= 0
              && NaturalOrder.COMPARATOR.compare(b, c) <= 0)
            assertTrue(NaturalOrder.COMPARATOR.compare(a, c) <= 0, a + " / " + b + " / " + c);
      }
  }
}
