import { useRef, useState } from "react";
import { Alert, Button, Loader } from "@mantine/core";
import type { ComicBoxView } from "../shared/types.ts";
import type { ComicAnnotationTools, ComicStudyTools } from "./comic-tools.ts";
import { useComicRegionDrag } from "./useComicRegionDrag.ts";
import { pageLayout } from "./annotations.js";
import { ComicRegionOverlay } from "./ComicRegionOverlay.tsx";

/** The image's geometry, source, load notification and optional overlays belong to this page. */
interface ComicPageProps {
  pageId: string;
  imageUrl: string;
  imageLabel: string;
  viewport: { width: number; height: number };
  zoom: string;
  scale: number;
  boxes: { box: ComicBoxView; number: number }[];
  onLoaded?: () => void;
  outlined: boolean;
  study?: ComicStudyTools;
  annotations?: ComicAnnotationTools;
}

/** One page image owns loading/retry and places its study regions on the fitted image rectangle. */
export function ComicPage({
  pageId,
  imageUrl,
  imageLabel,
  viewport,
  zoom,
  scale,
  boxes,
  onLoaded,
  outlined,
  study,
  annotations,
}: ComicPageProps) {
  const annotate = annotations?.enabled;
  const editing = annotations?.locked;
  const stage = useRef<HTMLDivElement>(null);
  const [image, setImage] = useState({ width: 1, height: 1 });
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const drag = useComicRegionDrag(
    stage,
    annotate,
    editing,
    (region) => annotations?.onDraw?.(region, pageId),
    annotations?.onPlacement,
  );
  const { draft, dragBox } = drag;
  const size = pageLayout(
    viewport.width,
    viewport.height,
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
        onPointerDown={drag.startDraw}
      >
        <img
          key={attempt}
          className="comic-image"
          alt={imageLabel}
          src={`${imageUrl}?attempt=${attempt}`}
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
          boxes.flatMap(({ box, number }) =>
            box.regions.map((region, r) => (
              <ComicRegionOverlay
                key={box.id + ":" + r}
                box={box}
                region={region}
                regionIndex={r}
                number={number}
                outlined={outlined}
                study={study}
                annotations={annotations}
                drag={drag}
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
