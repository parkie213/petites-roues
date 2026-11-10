import { useEffect, useState } from "react";
import { request } from "../shared/api.ts";
import { libraryReturnPath } from "../shared/library-return.ts";

/** Loads the study document and review queue, with the library page to return to. */
export function useStudyDocument({ api, setStatus }) {
  const [doc, setDoc] = useState(null);
  const [cues, setCues] = useState([]);
  const [changes, setChanges] = useState([]);
  const [error, setError] = useState("");
  const [returnPath] = useState(libraryReturnPath);
  useEffect(() => {
    // A video without a study document has no title to load.
    if (!api) return;
    const controller = new AbortController();
    request(api, { signal: controller.signal })
      .then((document) => {
        setDoc(document);
        setCues(document.cues);
        window.document.title = `Petites Roues · ${document.title}`;
      })
      .catch((e) => {
        if (e.name !== "AbortError") setError(e.message);
      });
    request(api + "/review-queue", { signal: controller.signal })
      .then(setChanges)
      .catch((e) => {
        if (e.name !== "AbortError")
          setStatus("Review queue unavailable: " + e.message);
      });
    return () => controller.abort();
  }, [api, setStatus]);
  return { doc, cues, setCues, changes, setChanges, error, returnPath };
}
