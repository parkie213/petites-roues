package app.petitesroues.model.editing;

import app.petitesroues.model.document.StudyDocument;
import java.util.List;

/** Current document plus the original acceptance receipt, including server-assigned identities. */
public record CommandResult(
    StudyDocument document, String version, List<String> createdIds, String acceptedVersion) {}
