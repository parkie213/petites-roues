import { useEffect, useRef, useState } from "react";
import { Alert, Button, Loader } from "@mantine/core";
import { pageLayout, regionFromDrag, clampRegion } from "./annotations.js";
import { ComicBubblePopover } from "./ComicBubblePopover.jsx";

const HOVER_CLOSE_DELAY = 180;
// While a card is open, another box must be rested on this long to take over, so the pointer
// can cross neighbouring boxes on its way into the open card.
const HOVER_SWITCH_DELAY = 250;

/**
 * Shows one page or a two-page spread in a shared scrolling viewport. Bubble regions are
 * invisible hit areas unless debug or annotate mode outlines them; hovering opens a bubble's
 * study card (when cards are enabled) and selects it for the study panel; clicking pins the
 * bubble until it is dismissed. Hovering another box while a card is open switches only after a
 * short rest, and reaching the open card cancels the switch. A text block's card steps through or
 * lists its lines, as `blockMode` chooses. `selectedId` is a line; boxes are keyed by their own id.
 */
export function ComicPageView({
  document,
  pages,
  api,
  zoom,
  scale,
  boxes,
  rtl,
  selectedId,
  onSelect,
  onLoaded,
  debug,
  cards,
  blockMode,
  onBlockMode,
  editing,
  annotate,
  onDraw,
  onPlacement,
}) {
  const viewport = useRef(null);
  const [area, setArea] = useState({ width: 0, height: 0 });
  const [bubble, setBubble] = useState(null);
  // The pending hover change: closing the open card, or switching it to another box.
  const closeTimer = useRef(null);
  useEffect(() => {
    const observer = new ResizeObserver(([entry]) =>
      setArea({
        width: entry.contentRect.width,
        height: entry.contentRect.height,
      }),
    );
    observer.observe(viewport.current);
    return () => {
      observer.disconnect();
      clearTimeout(closeTimer.current);
    };
  }, []);
  useEffect(() => {
    viewport.current?.scrollTo({ top: 0, left: 0 });
  }, [zoom]);
  useEffect(() => {
    if (annotate || editing) setBubble(null);
  }, [annotate, editing]);
  // Mantine only sees Escape while focus is inside the popover; hover-opened cards have none.
  useEffect(() => {
    if (!bubble) return;
    const keydown = (event) => event.key === "Escape" && setBubble(null);
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [bubble]);

  const interactive = !annotate && !editing;
  const bubbles = {
    current: bubble,
    hover(id, lineId) {
      clearTimeout(closeTimer.current);
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
      clearTimeout(closeTimer.current);
      closeTimer.current = setTimeout(
        () => setBubble((open) => (open?.pinned ? open : null)),
        HOVER_CLOSE_DELAY,
      );
    },
    pin(id, lineId) {
      clearTimeout(closeTimer.current);
      setBubble((open) =>
        open?.pinned && open.id === id ? null : { id, pinned: true },
      );
      onSelect(lineId);
    },
    dismiss() {
      clearTimeout(closeTimer.current);
      setBubble(null);
    },
    // Keeps a hover-opened card open, e.g. while the learner types a question in it.
    keep(id) {
      clearTimeout(closeTimer.current);
      setBubble({ id, pinned: true });
    },
  };

  return (
    <section
      ref={viewport}
      className="comic-viewport"
      onPointerDown={(event) => {
        // Pressing empty page space releases a pinned bubble. Events bubbling from the
        // portalled card are not inside the viewport's DOM and are ignored.
        if (
          viewport.current.contains(event.target) &&
          !event.target.closest(".comic-region")
        )
          bubbles.dismiss();
      }}
      aria-label={pages.length > 1 ? "Comic spread" : "Comic page"}
    >
      <div
        className="comic-spread"
        style={{ flexDirection: rtl ? "row-reverse" : "row" }}
      >
        {pages.map((page, slot) => (
          <ComicPage
            key={page.id}
            document={document}
            page={page}
            api={api}
            width={area.width / pages.length}
            height={area.height}
            zoom={zoom}
            scale={scale}
            boxes={boxes.filter((box) => box.pageId === page.id)}
            numbers={boxes}
            selectedId={selectedId}
            onLoaded={slot === 0 ? onLoaded : undefined}
            outlined={debug || annotate}
            editing={editing}
            annotate={annotate}
            bubbles={interactive ? bubbles : null}
            cards={cards}
            blockMode={blockMode}
            onBlockMode={onBlockMode}
            onSelect={onSelect}
            onDraw={(region) => onDraw?.(region, page.id)}
            onPlacement={onPlacement}
          />
        ))}
      </div>
    </section>
  );
}

/** One page image with its bubble regions laid over the exact rendered image rectangle. */
function ComicPage({
  document,
  page,
  api,
  width,
  height,
  zoom,
  scale,
  boxes,
  numbers,
  selectedId,
  onLoaded,
  outlined,
  editing,
  annotate,
  bubbles,
  cards,
  blockMode,
  onBlockMode,
  onSelect,
  onDraw,
  onPlacement,
}) {
  const stage = useRef(null);
  const [image, setImage] = useState({ width: 1, height: 1 });
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState(null);
  const [dragBox, setDragBox] = useState(null);
  const size = pageLayout(
    width,
    height,
    image.width,
    image.height,
    zoom,
    scale,
  );

  function pointerFraction(event) {
    const rect = stage.current?.getBoundingClientRect();
    if (!rect) return null;
    return {
      x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
      y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)),
    };
  }

  function startDraw(event) {
    if (!annotate || editing || event.button !== 0) return;
    const start = pointerFraction(event);
    if (!start) return;
    event.preventDefault();
    setDraft(start);
    let drawn = null;
    const move = (moveEvent) => {
      const end = pointerFraction(moveEvent);
      if (end) {
        drawn = regionFromDrag(start, end);
        setDragBox(drawn);
      }
    };
    const up = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
      setDraft(null);
      setDragBox(null);
      if (drawn && drawn.width >= 0.01 && drawn.height >= 0.01) onDraw(drawn);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  }

  function startMove(event, box, region, corner) {
    if (!annotate || editing || event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();
    const origin = pointerFraction(event);
    if (!origin) return;
    const original = { ...region };
    let updated = null;
    const move = (moveEvent) => {
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
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
      setDragBox(null);
      if (updated) onPlacement?.(box, [updated]);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  }

  function region(box, region, r) {
    const number = numbers.indexOf(box) + 1;
    const selectedLine = box.lines.find((line) => line.id === selectedId);
    const isSelected = Boolean(selectedLine);
    // The line the box selects: a block keeps its current sentence, arriving fresh at the first.
    const lineId = (selectedLine || box.lines[0]).id;
    const isOpen = bubbles?.current?.id === box.id;
    const dragging = !draft && dragBox && isSelected;
    const rect = dragging ? dragBox : region;
    const button = (
      <button
        key={box.id + ":" + r}
        className={
          "comic-region" +
          (outlined ? " is-outlined" : "") +
          (isOpen ? " is-open" : "") +
          (isSelected && outlined ? " is-selected" : "") +
          (annotate ? " is-annotate" : "")
        }
        aria-label={
          box.lines.length > 1
            ? `Text block ${number}: ${box.lines.length} sentences`
            : `Bubble ${number}: ${box.lines[0].source || "text pending"}`
        }
        aria-expanded={bubbles ? isOpen : undefined}
        aria-pressed={annotate ? isSelected : undefined}
        disabled={editing}
        style={{
          left: `${rect.x * 100}%`,
          top: `${rect.y * 100}%`,
          width: `${rect.width * 100}%`,
          height: `${rect.height * 100}%`,
        }}
        onClick={() => {
          if (draft) return;
          if (bubbles) bubbles.pin(box.id, lineId);
          else onSelect(lineId);
        }}
        onPointerEnter={(event) =>
          event.pointerType === "mouse" && bubbles?.hover(box.id, lineId)
        }
        onPointerLeave={(event) =>
          event.pointerType === "mouse" && bubbles?.leave()
        }
        onPointerDown={(event) => startMove(event, box, region)}
      >
        {outlined && <span aria-hidden="true">{number}</span>}
        {annotate &&
          isSelected &&
          !editing &&
          ["nw", "ne", "sw", "se"].map((corner) => (
            <span
              key={corner}
              className="comic-region-handle"
              aria-hidden="true"
              style={{
                [corner[0] === "n" ? "top" : "bottom"]: -6,
                [corner[1] === "w" ? "left" : "right"]: -6,
              }}
              onPointerDown={(event) => startMove(event, box, region, corner)}
            />
          ))}
      </button>
    );
    // The card anchors to the box's first region only, so split bubbles open one card.
    if (!bubbles || !cards || r > 0) return button;
    return (
      <ComicBubblePopover
        key={box.id + ":" + r}
        box={box}
        selectedId={selectedId}
        blockMode={blockMode}
        onBlockMode={onBlockMode}
        onSelect={onSelect}
        document={document}
        number={number}
        opened={isOpen}
        onDismiss={bubbles.dismiss}
        onKeep={() => bubbles.keep(box.id)}
        onPointerEnter={() => bubbles.hover(box.id, lineId)}
        onPointerLeave={(event) =>
          event.pointerType === "mouse" && bubbles.leave()
        }
      >
        {button}
      </ComicBubblePopover>
    );
  }

  return (
    <div className="comic-page-slot">
      {!loaded && !error && (
        <div className="comic-loading">
          <Loader size="sm" aria-label="Loading page" />
        </div>
      )}
      {error && (
        <div className="comic-message">
          <Alert color="red" title="Page could not load">
            This page could not be displayed.
          </Alert>
          <Button
            mt="md"
            onClick={() => {
              setError(false);
              setAttempt((n) => n + 1);
            }}
          >
            Retry page
          </Button>
        </div>
      )}
      <div
        ref={stage}
        className={
          "comic-page-stage" + (annotate && !editing ? " comic-annotate" : "")
        }
        style={{
          width: size.width,
          height: size.height,
          display: error ? "none" : undefined,
        }}
        onPointerDown={startDraw}
      >
        <img
          key={attempt}
          className="comic-image"
          alt={`${document.title}, page ${document.pages.indexOf(page) + 1}`}
          src={`${api}/pages/${encodeURIComponent(page.id)}?attempt=${attempt}`}
          style={{ opacity: loaded ? 1 : 0 }}
          onLoad={(event) => {
            setImage({
              width: event.currentTarget.naturalWidth,
              height: event.currentTarget.naturalHeight,
            });
            setLoaded(true);
            onLoaded?.();
          }}
          onError={() => {
            setLoaded(false);
            setError(true);
          }}
        />
        {loaded &&
          boxes.flatMap((box) => box.regions.map((r, i) => region(box, r, i)))}
        {dragBox && draft && (
          <div
            className="comic-region comic-draft-box"
            aria-hidden="true"
            style={{
              left: `${dragBox.x * 100}%`,
              top: `${dragBox.y * 100}%`,
              width: `${dragBox.width * 100}%`,
              height: `${dragBox.height * 100}%`,
            }}
          />
        )}
      </div>
    </div>
  );
}
