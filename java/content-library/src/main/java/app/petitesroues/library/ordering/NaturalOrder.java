package app.petitesroues.library.ordering;

import java.math.BigInteger;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared title and archive-page ordering, independent of shelf grouping. */
public final class NaturalOrder {
  private static final Pattern PARTS = Pattern.compile("\\p{Nd}+|\\P{Nd}+");

  private NaturalOrder() {}

  /**
   * Root-locale, case-insensitive ordering with accent distinctions and decimal digit runs compared
   * by value, before text runs. Equal numeric runs fall back to textual order, preserving
   * leading-zero distinctions. Accepts nonnull strings. A per-thread collator avoids sharing
   * mutable collation state.
   */
  public static final Comparator<String> COMPARATOR = NaturalOrder::compare;

  // The shared comparator may run concurrently. Collator is mutable and not guaranteed
  // thread-safe; reuse one per thread to avoid locking comparisons or recreating it each time.
  private static final ThreadLocal<Collator> COLLATOR =
      ThreadLocal.withInitial(
          () -> {
            var collator = Collator.getInstance(Locale.ROOT);
            collator.setStrength(Collator.SECONDARY);
            return collator;
          });

  private static int compare(String a, String b) {
    List<String> x = parts(a), y = parts(b);
    var collator = COLLATOR.get();
    for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
      String p = x.get(i), q = y.get(i);
      boolean pNumber = isNumber(p), qNumber = isNumber(q);
      int order;
      if (pNumber != qNumber) order = pNumber ? -1 : 1;
      else order = pNumber ? number(p).compareTo(number(q)) : collator.compare(p, q);
      if (order != 0) return order;
    }
    int order = Integer.compare(x.size(), y.size());
    return order != 0 ? order : collator.compare(a, b);
  }

  private static boolean isNumber(String part) {
    return Character.isDigit(part.codePointAt(0));
  }

  /** Normalises decimal code points, including supplementary-plane digits, for BigInteger. */
  private static BigInteger number(String digits) {
    var ascii = new StringBuilder();
    digits.codePoints().forEach(cp -> ascii.append((char) ('0' + Character.digit(cp, 10))));
    return new BigInteger(ascii.toString());
  }

  private static List<String> parts(String text) {
    List<String> parts = new ArrayList<>();
    Matcher matcher = PARTS.matcher(text);
    while (matcher.find()) parts.add(matcher.group());
    return parts;
  }
}
