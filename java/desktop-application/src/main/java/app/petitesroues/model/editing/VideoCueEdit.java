package app.petitesroues.model.editing;

import java.util.List;

/**
 * Player-authored subtitle text and timing. Identity and alignment are owned by the editing
 * service, so clients cannot replace reviewed phrase links. Null translation/check values request
 * absence; omitted continuation means false. Start and end must be explicitly supplied.
 */
public record VideoCueEdit(
    Double start,
    Double end,
    String source,
    String translation,
    List<String> notes,
    Boolean continuesPrevious,
    String listeningCheck,
    String textCheck) {}
