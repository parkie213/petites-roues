import { request, responseError } from "../shared/api.js";

const plural = (n, word) => `${n} ${word}${n === 1 ? "" : "s"}`;

/**
 * What a running Add or Refresh is doing, in words: a label, the file being worked on, and how far
 * through its phase it is (0–100), or null when the phase has no count.
 */
export function describeJob(job) {
  const progress = job?.progress;
  if (!progress) return { label: "Starting…", detail: null, value: null };
  const { folder, phase, done, total, current } = progress;
  const step = total > 0 ? ` · ${Math.min(done + 1, total)} of ${total}` : "";
  const value = total > 0 ? Math.round((done / total) * 100) : null;
  switch (phase) {
    case "scanning":
      return { label: `Looking through ${folder}…`, detail: null, value };
    case "media":
      return {
        label: `Checking ${plural(total, "media file")} in ${folder}${step}`,
        detail: current,
        value,
      };
    case "documents":
      return {
        label: `Reading study documents in ${folder}${step}`,
        detail: current,
        value,
      };
    case "saving":
      return { label: `Saving ${folder}…`, detail: null, value: null };
    default:
      return { label: `Working on ${folder}…`, detail: current, value };
  }
}

/** What to tell the person once a job has finished well. */
export function jobSucceeded(job, body) {
  if (job.kind === "add") return "Folder added.";
  return body?.repositoryId ? "Folder refreshed." : "Library refreshed.";
}

/**
 * Starts an Add ({kind: "add", localPath}) or Refresh ({kind: "refresh", repositoryId?}); returns
 * the job, which is the one already running if there is one.
 */
export function startJob(body) {
  return request("/api/jobs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

/** The running job, or the last to finish, or null when none has run since the server started. */
export async function latestJob(signal) {
  const response = await fetch("/api/jobs/latest", { signal });
  if (response.status === 204) return null;
  if (!response.ok) throw await responseError(response);
  return response.json();
}

/**
 * Calls onJob with the job's state as it streams from the server, ending after the finished state.
 * Returns a function that stops following.
 */
export function followJob(id, onJob) {
  const source = new EventSource(`/api/jobs/${encodeURIComponent(id)}/events`);
  source.addEventListener("job", (event) => {
    const job = JSON.parse(event.data);
    // Close before the server ends the stream, so the browser does not reconnect.
    if (job.state !== "running") source.close();
    onJob(job);
  });
  return () => source.close();
}
