import { useCallback, useEffect, useState } from "react";
import { request } from "../shared/api.ts";
import type {
  ContentRepository,
  Library,
  LibraryUsage,
  RepositoryContents,
} from "../shared/types.ts";
import { usageLine } from "./usage.js";

/** A registered folder with the study documents and media found in it, in place of its counts. */
export type LibraryRepository = Omit<ContentRepository, "media"> &
  Pick<RepositoryContents, "entries" | "media">;

/** The library as the page shows it. */
export interface LibraryData {
  repos: LibraryRepository[];
}

/**
 * The library's contents, loaded when the page opens, and how much disk it uses, measured
 * separately because walking the folders must not hold up the library itself. A first load that
 * fails is passed to [onError]; [reload] rejects instead, for its caller to report.
 */
export function useLibraryData(onError: (message: string) => void) {
  const [data, setData] = useState<LibraryData | null>(null);
  const [usage, setUsage] = useState("");

  const reload = useCallback(async (signal?: AbortSignal) => {
    const library = await request<Library>("/api/library", { signal });
    request<LibraryUsage>("/api/library/usage", { signal }).then(
      (measured) => setUsage(usageLine(measured)),
      () => setUsage(""),
    );
    setData({
      repos: library.repositories.map(({ repository, entries, media }) => ({
        ...repository,
        entries,
        media,
      })),
    });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    reload(controller.signal).catch((failure: Error) => {
      if (failure.name !== "AbortError") onError(failure.message);
    });
    return () => controller.abort();
  }, [reload, onError]);

  return { data, usage, reload };
}
