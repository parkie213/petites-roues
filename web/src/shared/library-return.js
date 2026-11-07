/**
 * The library page to return to from a player, reader or video: the one last shown in this tab
 * (view, folder or open series), so Back to library lands where the title was opened. Falls back
 * to the library's default view when storage is unavailable or nothing was recorded.
 */
const KEY = "library-return";

export function rememberLibraryPage(path) {
  try {
    sessionStorage.setItem(KEY, path);
  } catch {
    // Returning still works; it lands on the default view.
  }
}

export function libraryReturnPath() {
  try {
    const path = sessionStorage.getItem(KEY);
    if (path === "/" || path?.startsWith("/?")) return path;
  } catch {
    // Storage unavailable.
  }
  return "/";
}
