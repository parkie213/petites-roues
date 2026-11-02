import { useEffect, useState } from "react";
import { request } from "../shared/api.js";

/** Loads the study document, review queue and library return link. */
export function useStudyDocument({ id, api, setStatus }) {
  const [doc, setDoc] = useState(null);
  const [cues, setCues] = useState([]);
  const [changes, setChanges] = useState([]);
  const [error, setError] = useState("");
  const [returnPath, setReturnPath] = useState("/");
  useEffect(() => {
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
  }, [api]);
  useEffect(() => {
    const controller = new AbortController();
    request("/api/library/titles", { signal: controller.signal })
      .then((entries) => {
        const entry = entries.find((row) => row.id === id);
        if (entry?.repositoryId)
          setReturnPath(
            "/?" +
              new URLSearchParams({
                repository: entry.repositoryId,
                folder: (entry.relativePath || "")
                  .split("/")
                  .slice(0, -1)
                  .join("/"),
              }),
          );
      })
      .catch(() => {});
    return () => controller.abort();
  }, [id]);
  return { doc, cues, setCues, changes, setChanges, error, returnPath };
}
