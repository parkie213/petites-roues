package app.petitesroues.model.editing;

import java.util.List;

/**
 * Placement replacement bound to the regions the annotator actually opened, preventing lost
 * updates. Text and teaching material are never touched by placement edits.
 */
public record ComicBoxUpdate(
    List<ComicRegionInput> expectedRegions, List<ComicRegionInput> regions, Integer order) {}
