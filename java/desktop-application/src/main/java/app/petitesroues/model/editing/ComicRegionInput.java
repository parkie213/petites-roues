package app.petitesroues.model.editing;

/**
 * Normalized region rectangle supplied by annotation callers, in page fractions with a top-left
 * origin, mirroring the document's ComicRegion semantics.
 */
public record ComicRegionInput(double x, double y, double width, double height) {}
