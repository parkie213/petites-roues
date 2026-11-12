import { useCallback, useEffect, useRef, useState } from "react";

const HOVER_CLOSE_DELAY = 180;
// Let the pointer cross nearby boxes on the way into the open card.
const HOVER_SWITCH_DELAY = 250;

/** Owns hover delays, pinned selection and dismissal for all boxes in the displayed spread. */
export function useComicBubbles(
  cards: boolean,
  disabled: boolean,
  onSelect: (id: string) => void,
) {
  const [bubble, setBubble] = useState<{ id: string; pinned: boolean } | null>(
    null,
  );
  const closeTimer = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );
  function cancelPending() {
    clearTimeout(closeTimer.current);
  }
  const dismiss = useCallback(() => {
    clearTimeout(closeTimer.current);
    setBubble(null);
  }, []);
  useEffect(() => () => clearTimeout(closeTimer.current), []);
  useEffect(() => {
    if (disabled) dismiss();
  }, [disabled, dismiss]);
  // Hover-opened cards have no focus, so Escape must also be heard outside the popover.
  useEffect(() => {
    if (!bubble) return;
    const keydown = (event: KeyboardEvent) =>
      event.key === "Escape" && dismiss();
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [bubble, dismiss]);
  return {
    current: bubble,
    hover(id: string, lineId: string) {
      cancelPending();
      // A pinned bubble keeps the study panel too, so crossing the page doesn't change it.
      if (bubble?.pinned) return;
      const open = () => {
        setBubble({ id, pinned: false });
        onSelect(lineId);
      };
      if (cards && bubble && bubble.id !== id)
        closeTimer.current = setTimeout(open, HOVER_SWITCH_DELAY);
      else open();
    },
    leave() {
      cancelPending();
      closeTimer.current = setTimeout(
        () => setBubble((open) => (open?.pinned ? open : null)),
        HOVER_CLOSE_DELAY,
      );
    },
    pin(id: string, lineId: string) {
      cancelPending();
      setBubble((open) =>
        open?.pinned && open.id === id ? null : { id, pinned: true },
      );
      onSelect(lineId);
    },
    dismiss,
    // Keeps a hover-opened card open, e.g. while the learner types a question in it.
    keep(id: string) {
      cancelPending();
      setBubble({ id, pinned: true });
    },
  };
}

export type ComicBubbles = ReturnType<typeof useComicBubbles>;
