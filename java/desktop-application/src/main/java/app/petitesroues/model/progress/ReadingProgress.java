package app.petitesroues.model.progress;

/** Desktop reading snapshot with an ISO timestamp; null page and timestamp mean unsaved. */
public record ReadingProgress(String pageId, String updatedAt) implements SavedProgress {}
