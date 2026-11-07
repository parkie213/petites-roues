/** Human-readable language names use the interface language, independently of study material. */
export function languageName(tag, displayLanguage = "en") {
  if (!tag) return "Unknown language";
  try {
    return new Intl.DisplayNames([displayLanguage], { type: "language" }).of(
      tag,
    );
  } catch {
    return tag;
  }
}

/** Primary language subtags of scripts written without spaces between words. */
const UNSPACED = new Set(["zh", "yue", "wuu", "ja", "th", "lo", "km", "my"]);

/**
 * What separates a line's phrase chunks when they are joined back into its text: nothing for
 * languages written without word spaces, a single space otherwise. Mirrors ChunkSeparator.java.
 */
export function chunkSeparator(tag) {
  const language = String(tag || "")
    .split(/[-_]/)[0]
    .toLowerCase();
  return UNSPACED.has(language) ? "" : " ";
}

/** IME composition keys belong to the input method, including its committing Enter/Space. */
export function isComposing(event) {
  return Boolean(
    event.isComposing ||
    event.nativeEvent?.isComposing ||
    event.keyCode === 229,
  );
}
