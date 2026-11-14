import { request, titlePath } from "../shared/api.ts";

/**
 * Asks the desktop for the video quiz brief of title [id] for [selection]: the playhead
 * `position` in seconds (required), the player's `duration` when known, and optionally the
 * `scope` ("whole" or "range"), its typed `start` and `end` (m:ss or h:mm:ss) and the quiz
 * `type` id. Without a scope the desktop applies its default rule and the reply carries the form
 * it chose, the available quiz types, the line count and the brief together. An unreadable range
 * is not an error: the reply says `valid: false`. All text and rules stay on the desktop.
 */
export function prepareVideoQuiz(id, selection, signal) {
  const query = new URLSearchParams({ position: String(selection.position) });
  for (const name of ["duration", "scope", "start", "end", "type"]) {
    const value = selection[name];
    if (value !== undefined && value !== null && value !== "")
      query.set(name, String(value));
  }
  return request(`${titlePath(id)}/quiz?${query}`, { signal });
}
