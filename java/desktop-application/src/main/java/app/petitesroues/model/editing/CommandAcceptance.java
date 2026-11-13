package app.petitesroues.model.editing;

import java.util.List;

/** Compact durable receipt: the accepted state version and server-assigned identities. */
public record CommandAcceptance(String version, List<String> createdIds) {}
