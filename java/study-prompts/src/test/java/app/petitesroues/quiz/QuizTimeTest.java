package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuizTimeTest {
  @Test
  void parsesMinuteAndHourTimes() {
    assertEquals(1800, QuizTime.parse("30:00").getAsDouble());
    assertEquals(3723, QuizTime.parse("1:02:03").getAsDouble());
    assertEquals(0, QuizTime.parse("0:00").getAsDouble());
    assertEquals(7265, QuizTime.parse("121:05").getAsDouble());
  }

  @Test
  void rejectsMalformedTimes() {
    for (String value :
        new String[] {
          null,
          "",
          "-1:00",
          "1:60",
          "1:60:00",
          "abc",
          "1:2",
          "Infinity",
          "90",
          "1:30.5",
          "1:30 ",
          " 1:30",
          "1:2:03",
          "1::30",
          "١:٣٠"
        }) {
      assertTrue(QuizTime.parse(value).isEmpty(), "rejects '" + value + "'");
    }
  }

  @Test
  void formatsRoundingDownAndShowingHoursFromOneHour() {
    assertEquals("0:00", QuizTime.format(0));
    assertEquals("0:09", QuizTime.format(9.99));
    assertEquals("12:05", QuizTime.format(725));
    assertEquals("59:59", QuizTime.format(3599.9));
    assertEquals("1:00:00", QuizTime.format(3600));
    assertEquals("1:02:03", QuizTime.format(3723));
  }

  @Test
  void formatClampsNegativeAndNonFiniteToZero() {
    assertEquals("0:00", QuizTime.format(-5));
    assertEquals("0:00", QuizTime.format(Double.NaN));
    assertEquals("0:00", QuizTime.format(Double.POSITIVE_INFINITY));
  }

  @Test
  void parseAndFormatRoundTrip() {
    for (String value : new String[] {"0:00", "0:59", "12:05", "59:59", "1:00:00", "2:10:09"})
      assertEquals(value, QuizTime.format(QuizTime.parse(value).getAsDouble()));
  }
}
