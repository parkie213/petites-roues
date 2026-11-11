import { useEffect, useState } from "react";
import { request } from "../shared/api.ts";
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
  const [error, setError] = useState("");
  const [returnPath] = useState(libraryReturnPath);
  useEffect(() => {
    // A video without a study document has no title to load.
    if (!api) return;
    const controller = new AbortController();
    request<VideoView>(api, { signal: controller.signal })
      .then((document) => {
        setDoc(document);
        setCues(document.cues);
        window.document.title = `Petites Roues · ${document.title}`;
      })
      .catch((e: Error) => {
        if (e.name !== "AbortError") setError(e.message);
      });
    request<unknown[]>(api + "/review-queue", { signal: controller.signal })
      .then(setChanges)
      .catch((e: Error) => {
        if (e.name !== "AbortError")
          setStatus("Review queue unavailable: " + e.message);
      });
    return () => controller.abort();
  }, [api, setStatus]);
  return { doc, cues, setCues, changes, setChanges, error, returnPath };
}
