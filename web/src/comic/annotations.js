/** A page's boxes in their explicit reading order, independent of extraction order or position. */
export function pageBoxes(document, pageId) {
  return (document.boxes || [])
    .filter((box) => box.pageId === pageId)
    .sort((a, b) => a.order - b.order);
}

/**
 * Every line in these boxes, in reading order. Selection, navigation and text editing work on
 * lines; a box with more than one is a text block read a sentence at a time.
 */
export function boxLines(boxes) {
  return boxes.flatMap((box) => box.lines);
}

/** The box holding the line `lineId`. */
export function boxOf(boxes, lineId) {
  return boxes.find((box) => box.lines.some((line) => line.id === lineId));
}

/** Clamps a drawn or dragged region into valid normalized bounds with a minimum size. */
export function clampRegion(region) {
  // Clamp on the stored six-decimal grid so rounding cannot push an edge outside the page.
  const units = 1e6;
  const minimum = 5000;
  const x = Math.min(
    Math.max(Math.round(region.x * units), 0),
    units - minimum,
  );
  const y = Math.min(
    Math.max(Math.round(region.y * units), 0),
    units - minimum,
  );
  return {
    x: x / units,
    y: y / units,
    width:
      Math.min(Math.max(Math.round(region.width * units), minimum), units - x) /
      units,
    height:
      Math.min(
        Math.max(Math.round(region.height * units), minimum),
        units - y,
      ) / units,
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
