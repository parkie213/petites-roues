import { useEffect, useRef, useState } from "react";
import { animateTimeline } from "../timeline-animation.js";
import { timingHit } from "../timing-edit.js";
import { formatTime } from "../model.js";

export function Timeline({
  cues,
  secondSubtrack,
  videoRef,
  heldTimeRef,
  selected,
  span,
  onZoom,
  onSeek,
  onSelect,
  onEdit,
  editing = false,
  onEditStart,
  onEditEnd,
}) {
  const canvas = useRef(null),
    drag = useRef(null),
    viewStart = useRef(0),
    [size, setSize] = useState({ width: 0, height: 150 });
  useEffect(() => {
    const observer = new ResizeObserver(([entry]) =>
      setSize({
        width: entry.contentRect.width,
        height: entry.contentRect.height,
      }),
    );
    observer.observe(canvas.current.parentElement);
    return () => observer.disconnect();
  }, []);
  const pps = size.width / (span * 2);
  useEffect(() => {
    const element = canvas.current,
      ctx = element.getContext("2d"),
      ratio = devicePixelRatio || 1;
    const width = Math.round(size.width * ratio);
    const height = Math.round(size.height * ratio);
    if (element.width !== width) element.width = width;
    if (element.height !== height) element.height = height;
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    function draw(time) {
      const start = time - span;
      viewStart.current = start;
      const { width: w, height: h } = size,
        gridTop = secondSubtrack ? 76 : 38;
      const x = (t) => (t - start) * pps;
      ctx.clearRect(0, 0, w, h);
      const step = span > 30 ? 10 : span > 12 ? 5 : span > 5 ? 2 : 1;
      ctx.font = "10px monospace";
      ctx.strokeStyle = "#252a31";
      ctx.fillStyle = "#87929c";
      for (
        let t = Math.max(0, Math.ceil(start / step) * step);
        t < start + 2 * span;
        t += step
      ) {
        ctx.beginPath();
        ctx.moveTo(x(t), gridTop);
        ctx.lineTo(x(t), h);
        ctx.stroke();
        ctx.fillText(formatTime(t).slice(0, -4), x(t) + 3, h - 5);
      }
      cues.forEach((cue, i) => {
        if (cue.end < start || cue.start > start + span * 2) return;
        const left = x(cue.start),
          width = Math.max(2, x(cue.end) - left);
        ctx.fillStyle = i === selected ? "#355540" : "#23392d";
        ctx.fillRect(left, 5, width, 30);
        ctx.strokeStyle = i === selected ? "#9cd6a6" : "#4a6a55";
        ctx.strokeRect(left + 0.5, 5.5, width - 1, 29);
        ctx.save();
        ctx.beginPath();
        ctx.rect(left + 2, 5, Math.max(0, width - 4), 30);
        ctx.clip();
        ctx.fillStyle = "#d5e6db";
        ctx.font = "11px system-ui";
        ctx.fillText(
          `${i + 1}: ${cue.source.replace(/\n/g, " ")}`,
          left + 3,
          20,
        );
        ctx.restore();
      });
      if (editing)
        cues.forEach((cue) => {
          if (cue.end < start || cue.start > start + span * 2) return;
          const left = x(cue.start),
            right = x(cue.end);
          ctx.fillStyle = "#39d98a";
          ctx.fillRect(left - 2, 2, 5, 17);
          ctx.beginPath();
          ctx.moveTo(left + 3, 2);
          ctx.lineTo(left + 10, 2);
          ctx.lineTo(left + 3, 10);
          ctx.fill();
          ctx.fillStyle = "#ff7a6b";
          ctx.fillRect(right - 2, 21, 5, 17);
          ctx.beginPath();
          ctx.moveTo(right - 2, 30);
          ctx.lineTo(right - 9, 38);
          ctx.lineTo(right - 2, 38);
          ctx.fill();
        });

      if (secondSubtrack) {
        secondSubtrack.cues.forEach((cue, i) => {
          if (cue.end < start || cue.start > start + span * 2) return;
          const left = x(cue.start),
            width = Math.max(2, x(cue.end) - left);
          ctx.fillStyle = "#25364b";
          ctx.fillRect(left, 41, width, 30);
          ctx.strokeStyle = "#6587ad";
          ctx.strokeRect(left + 0.5, 41.5, width - 1, 29);
          ctx.save();
          ctx.beginPath();
          ctx.rect(left + 2, 41, Math.max(0, width - 4), 30);
          ctx.clip();
          ctx.fillStyle = "#c5d9f2";
          ctx.font = "11px system-ui";
          ctx.fillText(
            `${i + 1}: ${cue.source.replace(/\n/g, " ")}`,
            left + 3,
            57,
          );
          ctx.restore();
        });
      }
      ctx.strokeStyle = "#ffd75e";
      ctx.beginPath();
      ctx.moveTo(x(time), 0);
      ctx.lineTo(x(time), h);
      ctx.stroke();
    }
    return animateTimeline(videoRef, heldTimeRef, draw);
  }, [
    cues,
    secondSubtrack,
    videoRef,
    heldTimeRef,
    selected,
    span,
    editing,
    size,
    pps,
  ]);
  useEffect(() => {
    const element = canvas.current;
    const wheel = (event) => {
      event.preventDefault();
      onZoom(span * (event.deltaY > 0 ? 1.15 : 1 / 1.15));
    };
    element.addEventListener("wheel", wheel, { passive: false });
    return () => element.removeEventListener("wheel", wheel);
  }, [span, onZoom]);
  function position(event) {
    const rect = canvas.current.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }
  function down(event) {
    const point = position(event),
      t = viewStart.current + point.x / pps;
    if (secondSubtrack && point.y >= 41 && point.y <= 71) {
      const cue = secondSubtrack.cues.find(
        (cue) => t >= cue.start && t <= cue.end,
      );
      onSeek(Math.max(0, cue?.start ?? t));
      return;
    }
    const hit = editing
      ? timingHit(cues, point, viewStart.current, pps, selected)
      : null;
    drag.current = hit || { mode: "scrub" };
    canvas.current.setPointerCapture(event.pointerId);
    if (hit) {
      onEditStart?.();
      onSelect(hit.index);
    } else onSeek(Math.max(0, t));
  }
  function move(event) {
    if (!drag.current) return;
    const t = Math.max(0, viewStart.current + position(event).x / pps);
    const hit = drag.current;
    if (hit.mode === "scrub") onSeek(t);
    else onEdit(hit.index, hit.mode, t, hit.offset || 0);
  }
  return (
    <div className="wavewrap">
      {secondSubtrack && (
        <span className="subtrack-label" title={secondSubtrack.name}>
          Green: current · Blue: {secondSubtrack.name} (read-only)
        </span>
      )}
      <canvas
        ref={canvas}
        aria-label={
          editing
            ? "Subtitle timeline with draggable timings"
            : "Subtitle timeline, click to seek"
        }
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={() => {
          const wasEditing = drag.current && drag.current.mode !== "scrub";
          drag.current = null;
          if (wasEditing) onEditEnd?.();
        }}
        onLostPointerCapture={() => {
          const wasEditing = drag.current && drag.current.mode !== "scrub";
          drag.current = null;
          if (wasEditing) onEditEnd?.();
        }}
      />
    </div>
  );
}
