package app.petitesroues.model.document;

/**
 * A rectangle in normalised page coordinates (0–1, top-left origin) including margins, independent
 * of zoom.
 */
public record ComicRegion(double x, double y, double width, double height) {}
