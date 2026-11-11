import { languageName } from "../player/languages.js";
import type { CatalogueItem } from "../shared/types.ts";

const KEY = "library-language";

/** A language the library holds: its BCP 47 tag and its own name for itself. */
export interface LibraryLanguage {
  tag: string;
  label: string;
}

/** A language's own name for itself, capitalised: "Français", "Deutsch", "中文". */
export function nativeName(tag: string): string {
  const name: string = languageName(tag, tag);
  return name.charAt(0).toLocaleUpperCase(tag) + name.slice(1);
}

/** The source languages of the library's study documents, by native name. */
export function languagesIn(
  repos: readonly {
    entries: readonly Pick<CatalogueItem, "sourceLanguage">[];
  }[],
): LibraryLanguage[] {
  const tags = new Set(
    repos
      .flatMap((repo) => repo.entries)
      .map((entry) => entry.sourceLanguage)
      .filter((tag): tag is string => Boolean(tag)),
  );
  return [...tags]
    .map((tag) => ({ tag, label: nativeName(tag) }))
    .sort((a, b) => a.label.localeCompare(b.label));
}

/** The language chosen in this browser, or null for all. Storage may be unavailable. */
export function storedLanguage(): string | null {
  try {
    return localStorage.getItem(KEY) || null;
  } catch {
    return null;
  }
}

/** Remembers [tag] as the chosen language, or forgets the choice for null, where storage allows. */
export function rememberLanguage(tag: string | null) {
  try {
    if (tag) localStorage.setItem(KEY, tag);
    else localStorage.removeItem(KEY);
  } catch {
    // The filter still applies; it is just not remembered.
  }
}
