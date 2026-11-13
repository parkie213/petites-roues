package app.petitesroues.model.editing;

import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.progress.SavedProgress;
import app.petitesroues.model.review.CueChange;
import java.util.List;

/** One application read of document, review and resume state; never a portable content format. */
public record StudyState(
    StudyDocument document, String version, List<CueChange> pending, SavedProgress progress) {}
