import { request, responseError } from "../shared/api.ts";

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
 * the accepted job; a different operation already running is a conflict.
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
 * Polls sequentially while a job runs; disconnecting aborts a request and clears the next poll.
 * Transport failures are retried, as they do not establish whether the server's work finished.
 */
export function followJob(started, onJob) {
  const controller = new AbortController();
  let timer;
  async function poll() {
    try {
      const job = await request(`/api/jobs/${encodeURIComponent(started.id)}`, {
        signal: controller.signal,
      });
      if (controller.signal.aborted) return;
      onJob(job);
      if (job.state !== "running") return;
    } catch {
      if (controller.signal.aborted) return;
    }
    timer = setTimeout(poll, 500);
  }
  if (started.state === "running") timer = setTimeout(poll, 500);
  else onJob(started);
  return () => {
    controller.abort();
    clearTimeout(timer);
  };
}
