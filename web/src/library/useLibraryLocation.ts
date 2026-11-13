import { appLocation, appHistory, appHref } from "../shared/app-location.js";
import { useEffect, useState } from "react";
import { rememberLibraryPage } from "../shared/library-return.ts";
import {
  folderHref,
  readSelection,
  rememberView,
  storedView,
  viewHref,
  type LibraryView,
  type Selection,
} from "./navigation.ts";

/**
 * Where the library is, kept in the URL so links, reloads and Back work: the selection the URL
 * shows, and ways to move that push a history entry. A bare URL opens the view chosen last (the
 * repositories view is the bare URL itself), and each page shown is remembered for players and
 * readers to return to.
 */
export function useLibraryLocation() {
  const [selection, setSelection] = useState<Selection>(() => {
    const remembered = storedView();
    if (!appLocation().search && remembered !== "columns")
      appHistory(viewHref(remembered), true);
    return readSelection();
  });
  useEffect(() => {
    rememberLibraryPage(appLocation().pathname + appLocation().search);
  }, [selection]);
  useEffect(() => {
    const update = () => setSelection(readSelection());
    window.addEventListener("popstate", update);
    return () => window.removeEventListener("popstate", update);
  }, []);

  /** Shows the library URL [next], adding it to history unless it is already shown. */
  function go(next: string) {
    if (
      appHref(next) !== appHref(appLocation().pathname + appLocation().search)
    )
      appHistory(next);
    setSelection(readSelection());
  }
  return {
    selection,
    go,
    /** Opens a folder of [repository] in the repositories view. */
    navigate: (repository: string, parts: string[]) =>
      go(folderHref(repository, parts)),
    /** Shows [view], remembering it for the next bare library URL. */
    switchView(view: LibraryView) {
      rememberView(view);
      go(viewHref(view));
    },
  };
}
