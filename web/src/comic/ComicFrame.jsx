import { useEffect, useRef, useState } from "react";
import { ActionIcon, Select, Title, Tooltip } from "@mantine/core";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Columns2,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useStoredState } from "../shared/storage.ts";
import { libraryReturnPath } from "../shared/library-return.ts";
import { adjacentView, pageStep, spreadPages } from "./navigation.js";
import "./ComicReader.css";

/**
 * Paging and view state shared by every comic route: which page indexes are visible (one page or
 * a spread, remembered in this browser), page size, and the arrow/Home/End keys. `index` is owned
 * by the caller; `go` ignores turns while `locked` and clamps to the manifest. `onKey` sees every
 * other unmodified key outside form fields.
 */
export function useComicView({ count, index, onIndex, rtl, locked, onKey }) {
  const [spread, setSpread] = useStoredState("comic:spread", false);
  const [zoom, setZoom] = useState("page");
  const [scale, setScale] = useState(100);
  const visible = count > 0 ? spreadPages(index, count, spread) : [];
  const previous = count > 0 ? adjacentView(index, count, spread, -1) : null;
  const next = count > 0 ? adjacentView(index, count, spread, 1) : null;
  function go(target) {
    if (count === 0 || locked || target === null) return;
    const clamped = Math.max(0, Math.min(count - 1, target));
    if (clamped !== index) onIndex(clamped);
  }
  function resize(delta) {
    setScale((value) =>
      Math.max(50, Math.min(300, (zoom === "custom" ? value : 100) + delta)),
    );
    setZoom("custom");
  }

  const keydown = useRef(null);
  keydown.current = (event) => {
    if (
      event.defaultPrevented ||
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      /INPUT|SELECT|TEXTAREA/.test(event.target.tagName) ||
      event.target.isContentEditable
    )
      return;
    const step = pageStep(event.key, rtl ? "rtl" : "ltr");
    if (step) {
      event.preventDefault();
      go(step > 0 ? next : previous);
    } else if (event.key === "Home") {
      event.preventDefault();
      go(0);
    } else if (event.key === "End") {
      event.preventDefault();
      go(count - 1);
    } else onKey?.(event);
  };
  useEffect(() => {
    if (count === 0) return;
    const listener = (event) => keydown.current(event);
    window.addEventListener("keydown", listener);
    return () => window.removeEventListener("keydown", listener);
  }, [count]);

  return {
    count,
    rtl,
    locked,
    visible,
    previous,
    next,
    go,
    spread,
    setSpread,
    zoom,
    setZoom,
    scale,
    resize,
  };
}

/** A pressed-state icon button styled for the comic toolbar. */
export function ToolbarToggle({ label, pressed, onClick, icon, disabled }) {
  return (
    <Tooltip label={label} withArrow>
      <ActionIcon
        variant={pressed ? "light" : "subtle"}
        size="sm"
        color="coral"
        aria-label={label}
        aria-pressed={pressed}
        disabled={disabled}
        onClick={onClick}
      >
        {icon}
      </ActionIcon>
    </Tooltip>
  );
}

/**
 * The reader's top bar: back to the library and the title, then `middle` (e.g. a chapter
 * picker) and the caller's tools on the right.
 */
export function ComicToolbar({ title, middle, children }) {
  return (
    <header className="comic-toolbar">
      <div className="comic-toolbar-title">
        <Tooltip label="Library" withArrow>
          <ActionIcon
            component="a"
            href={libraryReturnPath()}
            variant="subtle"
            size="lg"
            aria-label="Back to library"
          >
            <ArrowLeft size={18} />
          </ActionIcon>
        </Tooltip>
        <div className="comic-title">
          <Title order={1} size="h6" lineClamp={1}>
            {title}
          </Title>
        </div>
      </div>
      {middle}
      <div className="comic-toolbar-tools">{children}</div>
    </header>
  );
}

/** Spread and page-size controls for a {@link useComicView} state. */
export function ViewTools({ view }) {
  const { zoom, scale } = view;
  return (
    <>
      <ToolbarToggle
        label="Two-page spread"
        pressed={view.spread}
        onClick={() => view.setSpread((value) => !value)}
        icon={<Columns2 size={18} />}
        disabled={view.locked}
      />
      <span className="comic-toolbar-divider" />
      <Select
        aria-label="Page size"
        size="xs"
        w={118}
        allowDeselect={false}
        value={zoom}
        onChange={view.setZoom}
        data={[
          { value: "page", label: "Fit page" },
          { value: "width", label: "Fit width" },
          { value: "custom", label: `Zoom ${scale}%` },
        ]}
      />
      <ToolbarToggle
        label="Zoom out"
        onClick={() => view.resize(-25)}
        icon={<ZoomOut size={18} />}
        disabled={zoom === "custom" && scale <= 50}
      />
      <ToolbarToggle
        label="Zoom in"
        onClick={() => view.resize(25)}
        icon={<ZoomIn size={18} />}
        disabled={zoom === "custom" && scale >= 300}
      />
    </>
  );
}

/**
 * Frames the page view (`children`) with page-turn edges, placed for the reading direction, and
 * the page picker.
 */
export function ComicStage({ view, children }) {
  const { rtl, locked, count } = view;
  const keys = rtl ? ["←", "→"] : ["→", "←"];
  const forward = { target: view.next, label: `Next page (${keys[0]})` };
  const back = { target: view.previous, label: `Previous page (${keys[1]})` };
  const turns = rtl ? [forward, back] : [back, forward];
  const edge = (side) => (
    <button
      className="comic-turn"
      aria-label={turns[side].label}
      title={turns[side].label}
      disabled={locked || turns[side].target === null}
      onClick={() => view.go(turns[side].target)}
    >
      {side === 0 ? <ChevronLeft size={28} /> : <ChevronRight size={28} />}
    </button>
  );
  return (
    <div className="comic-view-frame">
      {edge(0)}
      {children}
      {edge(1)}
      <nav className="comic-page-overlay" aria-label="Pages">
        <select
          className="comic-page-select"
          aria-label="Page"
          title={rtl ? "Read right to left" : "Read left to right"}
          disabled={locked}
          value={view.visible[0] ?? 0}
          onChange={(event) => view.go(Number(event.target.value))}
        >
          {Array.from({ length: count }, (_, i) => (
            <option key={i} value={i}>
              {`${i + 1} / ${count}`}
            </option>
          ))}
        </select>
      </nav>
    </div>
  );
}
