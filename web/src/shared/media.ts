import { responseError } from "./api.ts";
import type { Library, MediaItem } from "./types.ts";

/**
 * The indexed video or CBZ with this fingerprint, as the library lists it, or null. Media without a
 * study document is opened by fingerprint, so its page looks up its name and warnings here.
 */
export async function findMedia(
  fingerprint: string,
): Promise<MediaItem | null> {
  const response = await fetch("/api/library");
  if (!response.ok) throw await responseError(response);
  const library: Library = await response.json();
  return (
    library.repositories
      .flatMap((folder) => folder.media)
      .find((item) => item.fingerprint === fingerprint) ?? null
  );
}

/** How a page names media without a document: its series and title, from its path. */
export const mediaName = (
  media: Pick<MediaItem, "series" | "title"> | null | undefined,
  fallback: string,
) =>
  media ? [media.series, media.title].filter(Boolean).join(" · ") : fallback;
