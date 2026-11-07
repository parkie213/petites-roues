/** Builds and reads library URLs for links and browser history. */
export const folderHref = (id, parts = []) =>
  "/?" + new URLSearchParams({ repository: id, folder: parts.join("/") });

/** Posters view: optional repository filter, open series card and season. */
export const postersHref = ({ repository, group, season } = {}) =>
  "/?" +
  new URLSearchParams(
    Object.entries({ view: "posters", repository, group, season }).filter(
      ([, value]) => value != null && value !== "",
    ),
  );

/** The list of every study document and what it links to. */
export const documentsHref = () => "/?view=documents";

const VIEWS = new Set(["posters", "documents"]);

export function readSelection() {
  const query = new URLSearchParams(location.search);
  const season = Number(query.get("season"));
  return {
    view: VIEWS.has(query.get("view")) ? query.get("view") : "columns",
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
export function storedView() {
  try {
    const view = localStorage.getItem(VIEW_KEY);
    return view === "columns" || view === "documents" ? view : "posters";
  } catch {
    return "posters";
  }
}

export function rememberView(view) {
  try {
    localStorage.setItem(VIEW_KEY, view);
  } catch {
    // The view still switches; it is just not remembered.
  }
}
