import { useCallback, useEffect, useRef, useState } from "react";
import type { PointerEvent } from "react";

/** How long the controls stay after the pointer last asked for them. */
const VISIBLE_MS = 2500;

/** How close to the player's top or bottom, in pixels, the pointer reveals the controls. */
const EDGE = 48;

/** Subtitle parts a pointer explores; the controls keep out of their way. */
const EXPLORING =
  ".video-subtitle-line, .video-subtitle-english, .video-subtitle-notes, .video-full-translation, .video-sentence-notes";

/** The parts of a pointer event over the player that decide whether it shows the controls. */
export interface PlayerPointer {
  target: EventTarget | null;
  clientY: number;
  pointerType: string;
  type: string;
}

/**
 * Whether [pointer] over a player spanning [top] to [bottom] reveals the controls: near either
 * edge, or a touch tap (touch has no hover), but never while it explores a subtitle.
 */
export function revealsControls(
  pointer: PlayerPointer,
  { top, bottom }: { top: number; bottom: number },
) {
  const exploring = Boolean(
    (pointer.target as Element | null)?.closest?.(EXPLORING),
  );
  const atEdge =
    pointer.clientY <= top + EDGE || pointer.clientY >= bottom - EDGE;
  const touchTap =
    pointer.pointerType === "touch" && pointer.type === "pointerdown";
  return !exploring && (atEdge || touchTap);
}

/**
 * The player's controls, shown while the pointer asks for them ([revealsControls]) and hidden a
 * moment after it stops. Give [onPointer] to the player's pointer move and down handlers.
 */
export function useAutoHideControls() {
  const [visible, setVisible] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const wake = useCallback(() => {
    setVisible(true);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setVisible(false), VISIBLE_MS);
  }, []);
  useEffect(() => () => clearTimeout(timer.current), []);
  function onPointer(event: PointerEvent<HTMLElement>) {
    if (revealsControls(event, event.currentTarget.getBoundingClientRect()))
      wake();
    else {
      clearTimeout(timer.current);
      setVisible(false);
    }
  }
  return { visible, onPointer };
}
