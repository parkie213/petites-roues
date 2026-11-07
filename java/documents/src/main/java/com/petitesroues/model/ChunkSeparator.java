package com.petitesroues.model;

import java.util.Locale;
import java.util.Set;

/**
 * What separates a line's phrase chunks when they are joined back into its text: nothing for
 * languages written without spaces between words (Chinese, Japanese, Thai, Lao, Khmer, Burmese),
 * and a single space for everything else. A phrase breakdown binds to its line through this rule,
 * so {@code 我们 / 去 / 公园} spells {@code 我们去公园} while {@code Tu / me / manques.} spells {@code Tu me
 * manques.}. The web and Python tools implement the same list.
 */
public final class ChunkSeparator {
  /** Primary language subtags of scripts written without word spaces. */
  private static final Set<String> UNSPACED =
      Set.of("zh", "yue", "wuu", "ja", "th", "lo", "km", "my");

  private ChunkSeparator() {}

  /** The separator for a BCP 47 tag; an absent or unknown tag uses a single space. */
  public static String of(String languageTag) {
    if (languageTag == null) return " ";
    String language = languageTag.split("[-_]", 2)[0].toLowerCase(Locale.ROOT);
    return UNSPACED.contains(language) ? "" : " ";
  }
}
