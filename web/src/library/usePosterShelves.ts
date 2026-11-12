import { useEffect, useState } from "react";
import { request } from "../shared/api.ts";
import type { PosterShelves } from "../shared/types.ts";
import type { LibraryData } from "./useLibraryData.ts";

/** Reloads server-owned shelves after library changes; cancels loads when filters change or unmount. */
export function usePosterShelves(
  data: LibraryData,
  repository: string | null,
  language: string | null,
) {
  const [posters, setPosters] = useState<PosterShelves | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    const query = new URLSearchParams(
      Object.entries({ repository, language }).filter(
        (pair): pair is [string, string] => Boolean(pair[1]),
      ),
    );
    request<PosterShelves>(`/api/library/posters?${query}`, {
      signal: controller.signal,
    }).then(
      (next) => {
        setPosters(next);
        setError("");
      },
      (failure) => {
        if (failure.name !== "AbortError") setError(failure.message);
      },
    );
    return () => controller.abort();
  }, [data, repository, language]);
  return { posters, error };
}
