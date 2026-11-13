import { useEffect, useState } from "react";
import { loadStudyState } from "../shared/api.ts";
import { libraryReturnPath } from "../shared/library-return.ts";
import type { CueView, VideoView } from "../shared/types.ts";

/**
 * Loads the video's study document from [api] (none when null) and its review queue, with the
 * library page to return to. The cues are kept apart so the editor can replace them after a save.
 * Also names the browser tab.
 */
export function useStudyDocument({
  api,
  setStatus,
}: {
  api: string | null;
  setStatus: (message: string) => void;
}) {
  const [doc, setDoc] = useState<VideoView | null>(null);
  const [cues, setCues] = useState<CueView[]>([]);
  const [changes, setChanges] = useState<unknown[]>([]);
  const [progress, setProgress] = useState<{
    positionSeconds?: number;
    updatedAt?: string | null;
  }>();
  const [error, setError] = useState("");
  const [returnPath] = useState(libraryReturnPath);
  useEffect(() => {
    // A video without a study document has no title to load.
    if (!api) return;
    const controller = new AbortController();
    loadStudyState<VideoView>(api, controller.signal)
      .then((state) => {
        setDoc(state.document);
        setCues(state.document.cues);
        setChanges(state.pending);
        setProgress(state.progress);
        window.document.title = `Petites Roues · ${state.document.title}`;
      })
      .catch((e: Error) => {
        if (e.name !== "AbortError") setError(e.message);
      });
    return () => controller.abort();
  }, [api, setStatus]);
  return {
    doc,
    cues,
    setCues,
    changes,
    setChanges,
    error,
    returnPath,
    progress,
  };
}
