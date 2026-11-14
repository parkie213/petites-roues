import { useState } from "react";
import { chooseOnComputer, request } from "../shared/api.ts";
import { jobSucceeded } from "./library-job.js";
import { useLibraryData } from "./useLibraryData.ts";
import { useLibraryJob } from "./useLibraryJob.js";

/**
 * A background job: Add registers a folder; Refresh looks again at one, or at every one; Reset
 * deletes the library's local state and rebuilds it from the folders.
 */
type JobRequest =
  | { kind: "add"; localPath: string }
  | { kind: "refresh"; repositoryId?: string }
  | { kind: "reset" };

/**
 * The library page's contents and what the person does to them: adding, refreshing and removing
 * folders, and what came of it. One error and one message at a time describe the latest outcome;
 * each action clears them as it starts. Add, Refresh and Reset run on the server in the background,
 * polling their progress as [job]; the library is loaded again once each finishes, or a folder
 * is removed.
 */
export function useLibrary() {
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  // Only removing a folder is awaited here; Add and Refresh are jobs.
  const [busy, setBusy] = useState(false);
  // The repository whose removal awaits confirmation.
  const [removing, setRemoving] = useState<string | null>(null);
  const { data, usage, reload } = useLibraryData(setError);
  // Whether player edits await review (a reset refuses then); null until checked.
  const [pendingEdits, setPendingEdits] = useState<boolean | null>(null);

  const { job, start } = useLibraryJob(
    (finished: { state: string; error: string }, body: JobRequest | null) => {
      reload()
        .catch((failure: Error) => setError(failure.message))
        .then(() => {
          if (finished.state === "failed") setError(finished.error);
          else setMessage(jobSucceeded(finished, body));
        });
    },
  );
  const working = busy || job != null;

  async function startJob(body: JobRequest) {
    setError("");
    setMessage("");
    try {
      await start(body);
    } catch (failure) {
      setError((failure as Error).message);
    }
  }

  return {
    data,
    usage,
    error,
    message,
    job,
    /** Whether an action is under way, so others wait. */
    working,
    /** The folder whose removal awaits confirmation, and whether it is under way. */
    removing: removing
      ? {
          id: removing,
          name: data?.repos.find((repo) => repo.id === removing)?.name ?? "",
          underWay: busy,
        }
      : null,

    /** Registers a folder chosen on the computer; Add finds its documents, videos and comics. */
    async addFolder() {
      setError("");
      try {
        const path = await chooseOnComputer("folder");
        if (path) await startJob({ kind: "add", localPath: path });
      } catch (failure) {
        setError((failure as Error).message);
      }
    },
    refreshLibrary: () => startJob({ kind: "refresh" }),
    resetLibrary: () => startJob({ kind: "reset" }),
    /** Whether edits await review; null until checked. */
    pendingEdits,
    /** Looks for edits awaiting review; a failed check leaves the answer unknown. */
    checkPendingEdits() {
      request<unknown[]>("/api/review/pending").then(
        (titles) => setPendingEdits(titles.length > 0),
        () => setPendingEdits(null),
      );
    },
    refreshFolder: (repositoryId: string) =>
      startJob({ kind: "refresh", repositoryId }),

    /** Asks to confirm removing the repository [id]. */
    askToRemove: (id: string) => setRemoving(id),
    /** Withdraws the question, unless the removal is already under way. */
    cancelRemoval() {
      if (!busy) setRemoving(null);
    },
    /** Removes the repository awaiting confirmation from the library; its files stay on disk. */
    async remove() {
      setBusy(true);
      setError("");
      setMessage("");
      try {
        await request(`/api/repositories/${removing}`, { method: "DELETE" });
        await reload();
        setMessage("Folder removed. Files were left untouched.");
        setRemoving(null);
      } catch (failure) {
        setError((failure as Error).message);
      } finally {
        setBusy(false);
      }
    },

    /** Reloads the library after a card or document action, or shows why the action failed. */
    changed(failure?: Error | null) {
      if (failure) setError(failure.message);
      else reload().catch((e: Error) => setError(e.message));
    },
  };
}
