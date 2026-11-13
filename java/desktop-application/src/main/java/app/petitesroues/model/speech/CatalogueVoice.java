package app.petitesroues.model.speech;

/** One downloadable Piper model, including its dialect, size and upstream model-card link. */
public record CatalogueVoice(
    String id,
    String name,
    String language,
    String region,
    String quality,
    int speakers,
    long sizeBytes,
    String modelCard,
    boolean installed) {}
