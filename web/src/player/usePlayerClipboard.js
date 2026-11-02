import { titleInfo, formatTime } from "./model.js";
import { alignmentState } from "./alignment.js";

/** Builds clipboard actions from the current cue and the registered document API. */
export function usePlayerClipboard({
  api,
  id,
  doc,
  cue,
  displayIndex,
  dirty,
  saveNow,
  setStatus,
}) {
  async function copyReviewPrompt() {
    try {
      if (dirty) await saveNow();
      const url = location.origin + api;
      await navigator.clipboard.writeText(
        `Follow agents/workflows/review-content.md and agents/guides/translation-notes.md to process the review queue for "${doc.title}" (title ID: ${id}). ` +
          `Source language: ${doc.sourceLanguage}; translation language: ${doc.translationLanguage}; notes language: ${doc.notesLanguage}. Write teaching explanations in the notes language. ` +
          `The app is running: POST ${url}/review/batches to claim my queued edits. Write the batch's document into the registered document.json, update translations, notes, alignments and checks for the affected cues, commit and push to the existing content upstream, then POST ${url}/review/batches/{batch}/complete. Repeat until the claim returns 204. ` +
          `My subtitle corrections don't confirm the dialogue: keep or set a listeningCheck when the audio is in doubt. Do not create routine backups or exports.`,
      );
      setStatus("Review prompt copied. Paste it into your coding agent.");
    } catch (failure) {
      setStatus("Could not copy review prompt: " + failure.message);
    }
  }
  async function copy(attempt) {
    if (!doc || !cue) return;
    const lines = [
      titleInfo(doc),
      `#${displayIndex + 1} · ${formatTime(cue.start)} → ${formatTime(cue.end)}`,
      attempt ?? cue.source,
      ...(attempt == null
        ? [
            cue.translation,
            cue.listeningCheck,
            cue.textCheck,
            ...(alignmentState(cue) === "ready"
              ? cue.alignment.links.map((link) => link.note)
              : cue.notes),
          ]
        : []),
    ].filter(Boolean);
    try {
      await navigator.clipboard.writeText(lines.join("\n"));
      setStatus("Copied with title and timestamps");
    } catch {
      setStatus("Clipboard unavailable");
    }
  }

  return { copy, copyReviewPrompt };
}
