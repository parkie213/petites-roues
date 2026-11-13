package app.petitesroues.model.library;

/** One committed discovery result, or a target-specific failure/skipped registration. */
public record RepositoryOutcome(
    String repositoryId, String state, ContentRepository repository, String error) {}
