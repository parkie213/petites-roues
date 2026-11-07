import { languageName } from "../player/languages.js";

const KEY = "library-language";

/** A language's own name for itself, capitalised: "Français", "Deutsch", "中文". */
export function nativeName(tag) {
  const name = languageName(tag, tag);
  return name.charAt(0).toLocaleUpperCase(tag) + name.slice(1);
}

/** The source languages of the library's study documents, by native name. */
export function languagesIn(repos) {
  const tags = new Set(
    repos
      .flatMap((repo) => repo.entries)
      .map((entry) => entry.sourceLanguage)
      .filter(Boolean),
  );
  return [...tags]
    .map((tag) => ({ tag, label: nativeName(tag) }))
    .sort((a, b) => a.label.localeCompare(b.label));
}

/**
 * The library narrowed to one source language, for Posters: only its study documents. Media without a study
 * document has no declared language, so it is shown only when no language is chosen. Null shows
 * everything.
 */
export function inLanguage(repos, tag) {
  if (!tag) return repos;
  return repos.map((repo) => ({
    ...repo,
    entries: repo.entries.filter((entry) => entry.sourceLanguage === tag),
    media: (repo.media ?? []).filter((item) => item.titleId),
  }));
}

/** The language chosen in this browser, or null for all. Storage may be unavailable. */
export function storedLanguage() {
  try {
    return localStorage.getItem(KEY) || null;
  } catch {
    return null;
  }
}

export function rememberLanguage(tag) {
  try {
    if (tag) localStorage.setItem(KEY, tag);
    else localStorage.removeItem(KEY);
  } catch {
    // The filter still applies; it is just not remembered.
  }
}
