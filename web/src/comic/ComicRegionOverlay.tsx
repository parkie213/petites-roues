import type { PointerEvent } from "react";
import type { ComicBoxView, ComicRegion } from "../shared/types.ts";
import type { ComicAnnotationTools, ComicStudyTools } from "./comic-tools.ts";
import type { useComicRegionDrag } from "./useComicRegionDrag.ts";
import { ComicBubblePopover } from "./ComicBubblePopover.jsx";

/** A numbered region receives study controls and the page's current drag, independently of its parent props. */
interface ComicRegionOverlayProps {
  box: ComicBoxView;
  region: ComicRegion;
  regionIndex: number;
  number: number;
  outlined: boolean;
  study?: ComicStudyTools;
  annotations?: ComicAnnotationTools;
  drag: ReturnType<typeof useComicRegionDrag>;
}

/** A region selects its box's current sentence; only the first region anchors the study card. */
export function ComicRegionOverlay({
  box,
  region,
  regionIndex,
  number,
  outlined,
  study,
  annotations,
  drag,
}: ComicRegionOverlayProps) {
  const selectedId = study?.selectedId;
  const bubbles = study?.bubbles;
  const annotate = annotations?.enabled;
  const editing = annotations?.locked;
  const { draft, dragBox, movingRegion, startMove } = drag;
  const selectedLine = box.lines.find((line) => line.id === selectedId);
  const isSelected = Boolean(selectedLine);
  // The line the box selects: a block keeps its current sentence, arriving fresh at the first.
  const lineId = (selectedLine || box.lines[0]).id;
  const isOpen = bubbles?.current?.id === box.id;
  const dragging = !draft && dragBox && movingRegion === region;
  const rect = dragging ? dragBox : region;
  const button = (
    <button
      key={box.id + ":" + regionIndex}
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
        else study?.onSelect(lineId);
      }}
      onPointerEnter={(event) =>
        event.pointerType === "mouse" && bubbles?.hover(box.id, lineId)
      }
      onPointerLeave={(event: PointerEvent) =>
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
  if (!bubbles || !study?.cards || regionIndex > 0) return button;
  return (
    <ComicBubblePopover
      key={box.id + ":" + regionIndex}
      box={box}
      selectedId={selectedId}
      blockMode={study.blockMode}
      onBlockMode={study.onBlockMode}
      onSelect={study.onSelect}
      document={study.document}
      number={number}
      opened={isOpen}
      onDismiss={bubbles.dismiss}
      onKeep={() => bubbles.keep(box.id)}
      onPointerEnter={() => bubbles.hover(box.id, lineId)}
      onPointerLeave={(event: PointerEvent) =>
        event.pointerType === "mouse" && bubbles.leave()
      }
    >
      {button}
    </ComicBubblePopover>
  );
}
