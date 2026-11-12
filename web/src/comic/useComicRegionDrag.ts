import {
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type RefObject,
} from "react";
import type { ComicBoxView, ComicRegion } from "../shared/types.ts";
import type { ComicPageViewProps, PagePoint } from "./comic-page.ts";
import { clampRegion, regionFromDrag } from "./annotations.js";

/**
 * A page's temporary drawing/move/resize gesture. Coordinates are normalized to its image stage;
 * only completed gestures publish changes. Global listeners are released on cancellation,
 * editing locks, mode changes and unmount, so a departed page cannot save a stale drag.
 */
export function useComicRegionDrag(
  stage: RefObject<HTMLDivElement | null>,
  annotate: boolean | undefined,
  editing: boolean | undefined,
  onDraw: ((region: ComicRegion) => void) | undefined,
  onPlacement: ComicPageViewProps["onPlacement"],
) {
  const [draft, setDraft] = useState<PagePoint | null>(null);
  const [dragBox, setDragBox] = useState<ComicRegion | null>(null);
  const cleanup = useRef<(() => void) | undefined>(undefined);
  function endGesture() {
    cleanup.current?.();
    cleanup.current = undefined;
  }
  function cancel() {
    endGesture();
    setDraft(null);
    setDragBox(null);
  }
  useEffect(() => {
    setDraft(null);
    setDragBox(null);
    return () => {
      cleanup.current?.();
      cleanup.current = undefined;
    };
  }, [annotate, editing]);
  function beginGesture(move: (event: PointerEvent) => void, up: () => void) {
    endGesture();
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    window.addEventListener("pointercancel", cancel);
    cleanup.current = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
      window.removeEventListener("pointercancel", cancel);
    };
  }
  function pointerFraction(event: { clientX: number; clientY: number }) {
    const rect = stage.current?.getBoundingClientRect();
    if (!rect) return null;
    return {
      x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
      y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)),
    };
  }

  function startDraw(event: ReactPointerEvent) {
    if (!annotate || editing || event.button !== 0) return;
    const start = pointerFraction(event);
    if (!start) return;
    event.preventDefault();
    setDraft(start);
    let drawn: ComicRegion | null = null;
    const move = (moveEvent: PointerEvent) => {
      const end = pointerFraction(moveEvent);
      if (end) {
        drawn = regionFromDrag(start, end);
        setDragBox(drawn);
      }
    };
    const up = () => {
      endGesture();
      setDraft(null);
      setDragBox(null);
      if (drawn && drawn.width >= 0.01 && drawn.height >= 0.01) onDraw?.(drawn);
    };
    beginGesture(move, up);
  }

  function startMove(
    event: ReactPointerEvent,
    box: ComicBoxView,
    region: ComicRegion,
    corner?: string,
  ) {
    if (!annotate || editing || event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();
    const origin = pointerFraction(event);
    if (!origin) return;
    const original = { ...region };
    let updated: ComicRegion | null = null;
    const move = (moveEvent: PointerEvent) => {
      const at = pointerFraction(moveEvent);
      if (!at) return;
      const dx = at.x - origin.x;
      const dy = at.y - origin.y;
      updated = corner
        ? clampRegion({
            x: corner.includes("w") ? original.x + dx : original.x,
            y: corner.includes("n") ? original.y + dy : original.y,
            width: corner.includes("e")
              ? original.width + dx
              : original.width - dx,
            height: corner.includes("s")
              ? original.height + dy
              : original.height - dy,
          })
        : clampRegion({
            x: original.x + dx,
            y: original.y + dy,
            width: original.width,
            height: original.height,
          });
      setDragBox(updated);
    };
    const up = () => {
      endGesture();
      setDragBox(null);
      if (updated) onPlacement?.(box, [updated]);
    };
    beginGesture(move, up);
  }

  return { draft, dragBox, startDraw, startMove };
}
