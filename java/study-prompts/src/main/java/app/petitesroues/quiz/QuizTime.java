package app.petitesroues.quiz;

import java.util.OptionalDouble;

/**
 * The one reading and writing of quiz times: strict {@code m:ss} or {@code h:mm:ss}, as a player
 * shows them. Used by every platform so a typed range means the same everywhere. Stateless.
 */
public final class QuizTime {
  private QuizTime() {}

  /**
   * Reads {@code m:ss} or {@code h:mm:ss}: minutes (or hours) are digits only, seconds and, with
   * hours, minutes are exactly two digits from 00 to 59. Anything else, including bare seconds,
   * fractions, signs, spaces and {@code null}, is empty. A present result is finite and not
   * negative.
   */
  public static OptionalDouble parse(String text) {
    if (text == null || !text.matches("\\d+:\\d{2}(?::\\d{2})?")) return OptionalDouble.empty();
    var parts = text.split(":");
    double total = 0;
    for (int i = 0; i < parts.length; i++) {
      double part = Double.parseDouble(parts[i]);
      if (i > 0 && part > 59) return OptionalDouble.empty();
      total = total * 60 + part;
    }
    return Double.isFinite(total) ? OptionalDouble.of(total) : OptionalDouble.empty();
  }

  /**
   * Formats whole seconds, rounding down, as {@code m:ss}, or {@code h:mm:ss} from one hour. Values
   * below zero and non-finite values give {@code 0:00}.
   */
  public static String format(double seconds) {
    long total = Double.isFinite(seconds) ? (long) Math.floor(Math.max(0, seconds)) : 0;
    long hours = total / 3600;
    long minutes = total % 3600 / 60;
    long rest = total % 60;
    return hours > 0
        ? String.format("%d:%02d:%02d", hours, minutes, rest)
        : String.format("%d:%02d", total / 60, rest);
  }
}
