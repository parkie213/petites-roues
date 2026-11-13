import { isDemo } from "../shared/app-location.js";
import { useEffect, useRef, useState } from "react";
import { request } from "../shared/api.ts";

/**
 * Serializes displayed-page writes so an older save cannot overwrite a newer position. Failures
 * leave the queue usable; completion after unmount does not update the reader's error notice.
 * Call save only after the page image loads, or to retry the displayed page.
 */
export function useComicProgress(api: string) {
  const [saveError, setSaveError] = useState("");
  const writes = useRef(Promise.resolve());
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, [api]);
  function save(pageId: string) {
    writes.current = writes.current
      .catch(() => {})
      .then(() =>
        request(api + "/progress", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ pageId }),
          // The demo saves through its worker; Firefox sends keepalive requests to the network instead.
          keepalive: !isDemo,
        }),
      )
      .then(() => {
        if (active.current) setSaveError("");
      })
      .catch((failure: Error) => {
        if (active.current)
          setSaveError(
            `Your reading position could not be saved. ${failure.message}`,
          );
      });
  }

  return { save, saveError };
}
