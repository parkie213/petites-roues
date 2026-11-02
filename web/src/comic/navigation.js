/** Manifest order defines forward reading; RTL reverses only physical arrow keys. */
export function pageStep(key, direction) {
  if (key === "ArrowLeft") return direction === "rtl" ? 1 : -1;
  if (key === "ArrowRight") return direction === "rtl" ? -1 : 1;
  return 0;
}

export function resumePage(pages, pageId) {
  const index = pages.findIndex((page) => page.id === pageId);
  return index < 0 ? 0 : index;
}

/**
 * Pages shown together starting from any page index. In spread mode the cover stands alone and
 * later pages pair as (1,2), (3,4)… in manifest order; the reader decides their physical order.
 */
export function spreadPages(index, count, spread) {
  if (!spread || index <= 0) return [Math.max(0, index)];
  const start = index - ((index - 1) % 2);
  return start + 1 < count ? [start, start + 1] : [start];
}

/** First page of the view before or after the one containing index, clamped to the manifest. */
export function adjacentView(index, count, spread, step) {
  const visible = spreadPages(index, count, spread);
  const next = step > 0 ? visible[visible.length - 1] + 1 : visible[0] - 1;
  if (next < 0 || next >= count) return null;
  return spreadPages(next, count, spread)[0];
}

/**
 * Position in `chapters` of the chapter containing page `index`, or -1 before the first one. A
 * chapter runs from its first page until the next chapter starts.
 */
export function chapterAt(pages, chapters, index) {
  let found = -1;
  (chapters ?? []).forEach((chapter, i) => {
    const start = pages.findIndex((page) => page.id === chapter.pageId);
    if (start >= 0 && start <= index) found = i;
  });
  return found;
}

/** "2 · Inconnu", or just the title for an unnumbered section. */
export function chapterName(chapter) {
  return chapter.number
    ? `${chapter.number} · ${chapter.title}`
    : chapter.title;
}
