package app.petitesroues.media;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * The languages of a video's audio tracks, as media files tag them (ISO 639-2, such as {@code eng}
 * or the bibliographic {@code fre}), turned into the BCP 47 primary tags study documents use
 * ({@code en}, {@code fr}), and kept in track order. Browsers play only the first track.
 */
public final class AudioLanguages {
  /** ISO 639-2 bibliographic codes, which media files often use, and their terminology codes. */
  private static final Map<String, String> BIBLIOGRAPHIC =
      Map.ofEntries(
          Map.entry("alb", "sqi"),
          Map.entry("arm", "hye"),
          Map.entry("baq", "eus"),
          Map.entry("bur", "mya"),
          Map.entry("chi", "zho"),
          Map.entry("cze", "ces"),
          Map.entry("dut", "nld"),
          Map.entry("fre", "fra"),
          Map.entry("geo", "kat"),
          Map.entry("ger", "deu"),
          Map.entry("gre", "ell"),
          Map.entry("ice", "isl"),
          Map.entry("mac", "mkd"),
          Map.entry("mao", "mri"),
          Map.entry("may", "msa"),
          Map.entry("per", "fas"),
          Map.entry("rum", "ron"),
          Map.entry("slo", "slk"),
          Map.entry("tib", "bod"),
          Map.entry("wel", "cym"));

  private static final Map<String, String> TWO_LETTER = new HashMap<>();

  static {
    for (String code : Locale.getISOLanguages())
      try {
        TWO_LETTER.put(Locale.of(code).getISO3Language(), code);
      } catch (MissingResourceException noThreeLetterCode) {
        // Not every two-letter code has a three-letter one.
      }
  }

  private AudioLanguages() {}

  /**
   * The BCP 47 primary tag for a track's language tag: two letters where ISO 639-1 has them,
   * otherwise the tag itself in lower case; empty for an untagged or undetermined ({@code und})
   * track.
   */
  public static String tag(String language) {
    if (language == null || language.isBlank()) return "";
    String code = language.strip().toLowerCase(Locale.ROOT);
    if (code.equals("und") || code.equals("zxx") || code.equals("mis") || code.equals("mul"))
      return "";
    if (code.length() == 2) return code;
    code = BIBLIOGRAPHIC.getOrDefault(code, code);
    return TWO_LETTER.getOrDefault(code, code);
  }

  /** The stored list ("en,fr,"), one item per track, back into tags; empty for null. */
  public static List<String> split(String stored) {
    return stored == null ? List.of() : Arrays.asList(stored.split(",", -1));
  }

  /** A tag's English name for warnings ("French"), or the tag itself when Java has none. */
  public static String name(String tag) {
    String name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH);
    return name.isEmpty() ? tag : name;
  }
}
