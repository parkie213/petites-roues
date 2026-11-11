import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent,
  type RefObject,
} from "react";
import { animateTimeline } from "../timeline-animation.js";
import { editClock } from "../subtitle-edit.ts";
import {
  timelineHit,
  type Boundary,
  type TimelineHit,
  type TimelineLine,
} from "../timeline.ts";

const HEIGHT = 64;
// Pixels within which a dragged end joins a neighbour's.
const SNAP = 7;
const LANE = { top: 6, height: 30 };

/** What a pointer held down on the timeline is doing. */
type Drag =
  | { kind: "scrub" }
  | { kind: "boundary"; boundary: Boundary }
  | { kind: "move"; offset: number; from: number; at: number; moved: boolean };

/**
 * The subtitles laid out under the video while one is edited, centred on the playhead and drawn
 * from the media clock on each frame. The [selected] line shows its handles: drag an end to move
 * it, or the line to move both. An end dragged onto a neighbour's joins them, so they move
 * together; Alt separates them.
 * Pressing anywhere places the playhead there; pressing another line also edits it, and dragging
 * between lines scrubs. The
 * wheel zooms. The timeline only reports; the editor owns the times it draws.
 */
export function Timeline({
  lines,
  selected,
  picked,
  videoRef,
  heldTimeRef,
  onSeek,
  onSelect,
  onDrag,
  onPick,
  onKeyDown,
  onDragEnd,
}: {
  lines: readonly TimelineLine[];
  selected: number;
  /** The boundary arrow keys nudge, drawn brighter. */
  picked: Boundary;
  videoRef: RefObject<HTMLVideoElement | null>;
  heldTimeRef: RefObject<number>;
  onSeek: (seconds: number) => void;
  onSelect: (index: number, at: number) => void;
  onDrag: (
    mode: Boundary | "move",
    at: number,
    offset: number,
    apart: boolean,
    /** Seconds within which a dragged end joins a neighbour's. */
    snap: number,
  ) => void;
  onPick: (boundary: Boundary) => void;
  onKeyDown: (event: KeyboardEvent<HTMLCanvasElement>) => void;
  /** A drag of the line or its ends was let go. */
  onDragEnd: () => void;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const drag = useRef<Drag | null>(null);
  const viewStart = useRef(0);
  // Seconds either side of the playhead.
  const [span, setSpan] = useState(6);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const element = canvas.current!.parentElement!;
    const observer = new ResizeObserver(([entry]) =>
      setWidth(entry.contentRect.width),
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const pixelsPerSecond = width / (span * 2);
  useEffect(() => {
    const element = canvas.current!;
    const context = element.getContext("2d");
    if (!context || !width) return;
    const ratio = window.devicePixelRatio || 1;
    element.width = Math.round(width * ratio);
    element.height = Math.round(HEIGHT * ratio);
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    function draw(time: number) {
      const ctx = context!;
      const start = time - span;
      viewStart.current = start;
      const x = (t: number) => (t - start) * pixelsPerSecond;
      ctx.clearRect(0, 0, width, HEIGHT);
      const step = span > 30 ? 10 : span > 12 ? 5 : span > 5 ? 2 : 1;
      ctx.font = "10px Manrope, sans-serif";
      for (
        let t = Math.max(0, Math.ceil(start / step) * step);
        t < start + 2 * span;
        t += step
      ) {
        ctx.fillStyle = "#ffffff14";
        ctx.fillRect(Math.round(x(t)), LANE.top + LANE.height + 4, 1, 8);
        ctx.fillStyle = "#87929c";
        ctx.fillText(editClock(t).slice(0, -4), x(t) + 3, HEIGHT - 6);
      }
      lines.forEach((line, i) => {
        if (line.end < start || line.start > start + span * 2) return;
        const left = x(line.start);
        const right = x(line.end);
        const chosen = i === selected;
        ctx.fillStyle = chosen ? "#df796738" : "#2a3038";
        ctx.fillRect(left, LANE.top, Math.max(2, right - left), LANE.height);
        ctx.strokeStyle = chosen ? "#df7967" : "#4a525d";
        ctx.strokeRect(
          left + 0.5,
          LANE.top + 0.5,
          Math.max(1, right - left - 1),
          LANE.height - 1,
        );
        ctx.save();
        ctx.beginPath();
        ctx.rect(
          left + 6,
          LANE.top,
          Math.max(0, right - left - 12),
          LANE.height,
        );
        ctx.clip();
        ctx.fillStyle = chosen ? "#f3e2de" : "#c4cad2";
        ctx.font = "12px Manrope, sans-serif";
        ctx.fillText(line.source.replace(/\n/g, " "), left + 8, LANE.top + 19);
        ctx.restore();
        if (chosen)
          for (const boundary of ["start", "end"] as const) {
            ctx.fillStyle = boundary === picked ? "#ffb3a5" : "#df7967";
            ctx.fillRect(
              (boundary === "start" ? left : right) - 2,
              LANE.top - 3,
              4,
              LANE.height + 6,
            );
          }
      });
      ctx.fillStyle = "#ffd75e";
      ctx.fillRect(Math.round(x(time)), 0, 1, HEIGHT);
    }
    return animateTimeline(videoRef, heldTimeRef, draw);
  }, [
    lines,
    selected,
    picked,
    videoRef,
    heldTimeRef,
    span,
    width,
    pixelsPerSecond,
  ]);
  useEffect(() => {
    const element = canvas.current!;
    const wheel = (event: WheelEvent) => {
      event.preventDefault();
      setSpan((current) =>
        Math.min(
          60,
          Math.max(1.5, current * (event.deltaY > 0 ? 1.15 : 1 / 1.15)),
        ),
      );
    };
    element.addEventListener("wheel", wheel, { passive: false });
    return () => element.removeEventListener("wheel", wheel);
  }, []);

  const seconds = (event: PointerEvent) =>
    Math.max(
      0,
      viewStart.current +
        (event.clientX - canvas.current!.getBoundingClientRect().left) /
          pixelsPerSecond,
    );
  const hit = (event: PointerEvent): TimelineHit =>
    timelineHit(
      lines,
      event.clientX - canvas.current!.getBoundingClientRect().left,
      viewStart.current,
      pixelsPerSecond,
      selected,
    );
  function down(event: PointerEvent<HTMLCanvasElement>) {
    if (event.button !== 0 || !pixelsPerSecond) return;
    const target = hit(event);
    if (target.kind === "select") {
      onSelect(target.index, target.at);
      return;
    }
    canvas.current!.setPointerCapture(event.pointerId);
    if (target.kind === "boundary") {
      onPick(target.boundary);
      drag.current = target;
    } else if (target.kind === "move")
      drag.current = {
        ...target,
        from: event.clientX,
        at: seconds(event),
        moved: false,
      };
    else {
      drag.current = { kind: "scrub" };
      onSeek(target.at);
    }
  }
  function move(event: PointerEvent<HTMLCanvasElement>) {
    const current = drag.current;
    if (!current) {
      const target = pixelsPerSecond ? hit(event) : null;
      canvas.current!.style.cursor =
        target?.kind === "boundary"
          ? "ew-resize"
          : target?.kind === "move"
            ? "grab"
            : target?.kind === "select"
              ? "pointer"
              : "crosshair";
      return;
    }
    const at = seconds(event);
    if (current.kind === "scrub") onSeek(at);
    else if (current.kind === "boundary")
      onDrag(current.boundary, at, 0, event.altKey, SNAP / pixelsPerSecond);
    else {
      // A press inside the line that does not move seeks there instead.
      current.moved ||= Math.abs(event.clientX - current.from) > 3;
      if (current.moved)
        onDrag(
          "move",
          at,
          current.offset,
          event.altKey,
          SNAP / pixelsPerSecond,
        );
    }
  }
  function up() {
    const current = drag.current;
    drag.current = null;
    if (current?.kind === "move" && !current.moved) onSeek(current.at);
    else if (current && current.kind !== "scrub") onDragEnd();
  }
  return (
    <div className="subtitle-timeline">
      <canvas
        ref={canvas}
        tabIndex={0}
        aria-label="Subtitle timeline: drag the line's ends or the line; arrow keys nudge the chosen end; [ and ] set the start and end to the playhead"
        style={{ width: "100%", height: HEIGHT }}
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onLostPointerCapture={() => {
          if (drag.current && drag.current.kind !== "scrub") onDragEnd();
          drag.current = null;
        }}
        onKeyDown={onKeyDown}
      />
    </div>
  );
}
