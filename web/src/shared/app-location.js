/** The Pages demo opts in at runtime; the desktop and demo use the same app bundle. */
export const isDemo =
  typeof document !== "undefined" &&
  document.documentElement.dataset.demo === "true";

/** Logical application URL; demo routes live in a query parameter so static hosting can reload them. */
export function appLocation() {
  return isDemo
    ? new URL(
        new URLSearchParams(location.search).get("route") || "/",
        location.origin,
      )
    : new URL(location.href);
}

/** Keeps internal links inside the static demo, leaving desktop links unchanged. */
export function appHref(path) {
  if (!isDemo) return path;
  const target = new URL(path, location.origin);
  if (target.pathname === location.pathname && target.searchParams.has("route"))
    return target.pathname + target.search;
  return (
    location.pathname +
    "?route=" +
    encodeURIComponent(target.pathname + target.search)
  );
}

/** Updates history using the same logical routes on both hosts. */
export function appHistory(path, replace = false) {
  history[replace ? "replaceState" : "pushState"](null, "", appHref(path));
}

/** Public app assets are rooted beside the demo index, or at the desktop server root. */
export function publicAsset(name) {
  return isDemo ? new URL(name, location.href).pathname : "/" + name;
}
