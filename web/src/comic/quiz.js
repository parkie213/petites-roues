import { request, titlePath } from "../shared/api.ts";

/** Prepares a chapter brief from the desktop's current served document; exporting stays in the UI. */
export function prepareChapterQuiz(id, chapterPageId, rules, type, signal) {
  return request(`${titlePath(id)}/comic/quiz`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    signal,
    body: JSON.stringify({
      chapterPageId,
      rules,
      type: type
        ? { label: type.label, instructions: type.instructions }
        : null,
    }),
  });
}
