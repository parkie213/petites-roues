import { useRef, useState } from "react";
import { Alert, Button, Loader } from "@mantine/core";
import type { ComicBoxView, ComicRegion } from "../shared/types.ts";
import type { ComicPageItem, ComicPageViewProps } from "./comic-page.ts";
import type { ComicBubbles } from "./useComicBubbles.ts";
import { useComicRegionDrag } from "./useComicRegionDrag.ts";
import { pageLayout } from "./annotations.js";
import { ComicRegionOverlay } from "./ComicRegionOverlay.tsx";

/** One page image with its bubble regions laid over the exact rendered image rectangle. */
export function ComicPage({
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
}: Omit<ComicPageViewProps, "pages" | "rtl" | "debug" | "onDraw"> & {
  page: ComicPageItem;
  width: number;
  height: number;
  numbers: ComicBoxView[];
  outlined: boolean;
  bubbles: ComicBubbles | null;
  onDraw?: (region: ComicRegion) => void;
}) {
  const stage = useRef<HTMLDivElement>(null);
  const [image, setImage] = useState({ width: 1, height: 1 });
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const { draft, dragBox, startDraw, startMove } = useComicRegionDrag(
    stage,
    annotate,
    editing,
    onDraw,
    onPlacement,
  );
  const size = pageLayout(
    width,
    height,
    image.width,
    image.height,
    zoom,
    scale,
  );

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
          boxes.flatMap((box) =>
            box.regions.map((region, r) => (
              <ComicRegionOverlay
                key={box.id + ":" + r}
                box={box}
                region={region}
                r={r}
                numbers={numbers}
                selectedId={selectedId}
                outlined={outlined}
                editing={editing}
                annotate={annotate}
                bubbles={bubbles}
                cards={cards}
                blockMode={blockMode}
                onBlockMode={onBlockMode}
                onSelect={onSelect}
                document={document}
                draft={draft}
                dragBox={dragBox}
                startMove={startMove}
              />
            )),
          )}
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
