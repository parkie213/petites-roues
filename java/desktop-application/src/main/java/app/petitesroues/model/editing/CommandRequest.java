package app.petitesroues.model.editing;

import java.util.List;

/** One atomic, retryable command batch against the displayed state version. */
public record CommandRequest(
    String requestId, String expectedVersion, List<EditCommand> commands) {}
