import { useEffect, useState } from "react";
import { findMedia, mediaName } from "../shared/media.ts";

/** A video without a study document, as the player shows it: a title and its warnings, no lines. */
export interface BareVideo {
  title: string;
  cues: [];
  warnings: string[];
}

/**
 * The video with [fingerprint] as the player shows it without a study document: named from its
 * path, with no lines, and its warnings (such as no playable audio) for the bar. Also names the
 * browser tab. Null [fingerprint] loads nothing.
 */
export function useBareVideo(fingerprint: string | null) {
  const [media, setMedia] = useState<BareVideo | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!fingerprint) return;
    findMedia(fingerprint).then(
      (found) => {
        const title = mediaName(found, "Video");
        setMedia({ title, cues: [], warnings: found?.warnings ?? [] });
        window.document.title = `Petites Roues · ${title}`;
      },
      (failure: Error) => setError(failure.message),
    );
  }, [fingerprint]);
  return { media, error };
}
