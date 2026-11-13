package app.petitesroues.model.library;

/** The exact Add/Refresh command whose identity is retained while its job is running. */
public record DiscoveryRequest(String kind, String localPath, String name, String repositoryId) {}
