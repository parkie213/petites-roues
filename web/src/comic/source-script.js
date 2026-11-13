/**
 * Auto-listening requires letters in the source language's usual script. Foreign-script-only
 * regions and punctuation stay silent; manual listening remains available. This is a script
 * check, not language detection (names and accented dialogue remain eligible).
 */
export function hasSourceScript(text, language) {
  if (!text?.trim()) return false;
  if (!language) return true;
  const script = new Intl.Locale(language).maximize().script;
  // Japanese and Korean use several scripts together.
  const scripts = {
    Jpan: ["Han", "Hiragana", "Katakana"],
    Kore: ["Hangul", "Han"],
    Hans: ["Han"],
    Hant: ["Han"],
  };
  return (scripts[script] ?? [script]).some((value) =>
    new RegExp(`\\p{Script_Extensions=${value}}`, "u").test(text),
  );
}
