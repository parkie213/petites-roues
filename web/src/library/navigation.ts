/** Builds and reads library URLs for links and browser history. */

/** The library's views: posters of what can be watched or read, every study document, or folders. */
export type LibraryView = "posters" | "documents" | "columns";

/** What the library URL shows: a view, and within it a repository, folder, series and season. */
export interface Selection {
  view: LibraryView;
  repository: string | null;
  /** The folder within [repository], one name per level. */
  parts: string[];
  /** The series whose card is open, in the posters view. */
  group: string | null;
  season: number | null;
}

/** The repositories view, at a folder of the repository [id]. */
export const folderHref = (id: string, parts: readonly string[] = []) =>
  "/?" + new URLSearchParams({ repository: id, folder: parts.join("/") });

/** Posters view: optional repository filter, open series card and season. */
export const postersHref = ({
  repository,
  group,
  season,
}: {
  repository?: string | null;
  group?: string | null;
  season?: number | null;
} = {}) =>
  "/?" +
  new URLSearchParams(
    Object.entries({ view: "posters", repository, group, season })
      .filter(([, value]) => value != null && value !== "")
      .map(([key, value]) => [key, String(value)]),
  );

/** The list of every study document and what it links to. */
export const documentsHref = () => "/?view=documents";

/** Where switching to [view] goes: its top level, with nothing chosen within it. */
export const viewHref = (view: LibraryView) =>
  view === "posters"
    ? postersHref()
    : view === "documents"
      ? documentsHref()
      : "/";

/** What the current URL shows; anything not naming posters or documents is the repositories view. */
export function readSelection(): Selection {
  const query = new URLSearchParams(location.search);
  const view = query.get("view");
  const season = Number(query.get("season"));
  return {
    view: view === "posters" || view === "documents" ? view : "columns",
    repository: query.get("repository"),
    parts: (query.get("folder") || "").split("/").filter(Boolean),
    group: query.get("group"),
    season: Number.isInteger(season) && season > 0 ? season : null,
  };
}

const VIEW_KEY = "library-view";

/**
 * The view a bare library URL opens: Posters unless another view was last chosen. Storage may be
 * unavailable.
 */
export function storedView(): LibraryView {
  try {
    const view = localStorage.getItem(VIEW_KEY);
    return view === "columns" || view === "documents" ? view : "posters";
  } catch {
    return "posters";
  }
}

/** Remembers [view] for the next bare library URL, where storage allows. */
export function rememberView(view: LibraryView) {
  try {
    localStorage.setItem(VIEW_KEY, view);
  } catch {
    // The view still switches; it is just not remembered.
  }
}
