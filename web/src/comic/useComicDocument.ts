import { useEffect, useState } from "react";
import { request } from "../shared/api.ts";
import type { ComicView } from "../shared/types.ts";
import { resumePage } from "./navigation.js";

/** Loads the comic and saved page together; missing resume pages fall back to the cover. */
export function useComicDocument(api: string) {
  const [document, setDocument] = useState<ComicView | null>(null);
  const [index, setIndex] = useState(0);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController();
    Promise.all([
      request<ComicView>(api, { signal: abort.signal }),
      request<{ pageId?: string | null }>(api + "/progress", {
        signal: abort.signal,
      }),
    ])
      .then(([comic, progress]) => {
        setIndex(resumePage(comic.pages, progress.pageId));
        setDocument(comic);
      })
      .catch((failure) => {
        if (failure.name !== "AbortError") setError(failure.message);
      });
    return () => {
      abort.abort();
    };
  }, [api]);

  return { document, setDocument, index, setIndex, error };
}
