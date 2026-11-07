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

const VIEWS = new Set(["posters", "documents", "columns"]);

/** The Repositories view, at its first folder. */
export const columnsHref = () => "/?view=columns";

export function readSelection() {
  const query = new URLSearchParams(location.search);
  const season = Number(query.get("season"));
  return {
    // A folder link opens Repositories; anything else without a view opens Posters.
    view: VIEWS.has(query.get("view"))
      ? query.get("view")
      : query.has("repository")
        ? "columns"
        : "posters",
    repository: query.get("repository"),
    parts: (query.get("folder") || "").split("/").filter(Boolean),
    group: query.get("group"),
    season: Number.isInteger(season) && season > 0 ? season : null,
  };
}
