package app.petitesroues.model.progress;

/** A title's local resume state; the title determines whether it contains seconds or a page ID. */
public sealed interface SavedProgress permits PlaybackProgress, ReadingProgress {}
