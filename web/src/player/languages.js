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

/** IME composition keys belong to the input method, including its committing Enter/Space. */
export function isComposing(event) {
  return Boolean(
    event.isComposing ||
    event.nativeEvent?.isComposing ||
    event.keyCode === 229,
  );
}
