import { useCallback, useState } from "react";

/**
 * Which subtitles have a phrase's help open. While any does, the other help over the video
 * steps aside. Give [setPhraseHelp] to each subtitle as it opens and closes its phrase help.
 */
export function usePhraseHelp() {
  const [open, setOpen] = useState<ReadonlySet<string>>(() => new Set());
  const setPhraseHelp = useCallback((cueId: string, visible: boolean) => {
    setOpen((current) => {
      if (current.has(cueId) === visible) return current;
      const next = new Set(current);
      if (visible) next.add(cueId);
      else next.delete(cueId);
      return next;
    });
  }, []);
  return { phraseHelpOpen: open.size > 0, setPhraseHelp };
}
