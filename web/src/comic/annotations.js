/** Explicit cue order is independent of extraction order or text position. */
export function pageCues(document, pageId) {
  return (document.cues || [])
    .filter((cue) => cue.pageId === pageId)
    .sort((a, b) => a.order - b.order);
}

/** Clamps a drawn or dragged region into valid normalized bounds with a minimum size. */
export function clampRegion(region) {
  const x = Math.min(Math.max(region.x, 0), 1);
  const y = Math.min(Math.max(region.y, 0), 1);
  return {
    x: Math.round(x * 1e6) / 1e6,
    y: Math.round(y * 1e6) / 1e6,
    width:
      Math.round(Math.min(Math.max(region.width, 0.005), 1 - x) * 1e6) / 1e6,
    height:
      Math.round(Math.min(Math.max(region.height, 0.005), 1 - y) * 1e6) / 1e6,
  };
}

/** Rectangle from a drag between two normalized points, orientation-independent. */
export function regionFromDrag(start, end) {
  const x = Math.min(start.x, end.x);
  const y = Math.min(start.y, end.y);
  return clampRegion({
    x,
    y,
    width: Math.abs(end.x - start.x),
    height: Math.abs(end.y - start.y),
  });
}

/** Fits the complete source image; normalized highlights use this exact same rectangle. */
export function pageLayout(
  viewWidth,
  viewHeight,
  imageWidth,
  imageHeight,
  zoom,
  percent,
) {
  if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0)
    return { width: 0, height: 0 };
  const scale =
    zoom === "page"
      ? Math.min(viewWidth / imageWidth, viewHeight / imageHeight)
      : (viewWidth / imageWidth) * (zoom === "custom" ? percent / 100 : 1);
  return { width: imageWidth * scale, height: imageHeight * scale };
}
