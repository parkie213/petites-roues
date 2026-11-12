import { useComicViewport } from "./useComicViewport.ts";
import { useComicBubbles } from "./useComicBubbles.ts";
import { ComicPage } from "./ComicPage.tsx";
import type { ComicBoxView } from "../shared/types.ts";
import type {
  ComicAnnotationTools,
  ComicPageManifest,
  ComicStudyControls,
  ComicStudyTools,
} from "./comic-tools.ts";

/** The spread's images and optional study/annotation controls; bare comics need neither tool. */
interface ComicPageViewProps {
  document: ComicPageManifest;
  pages: ComicPageManifest["pages"];
  api: string;
  zoom: string;
  scale: number;
  boxes: ComicBoxView[];
  rtl: boolean;
  onLoaded?: () => void;
  debug?: boolean;
  study?: ComicStudyControls;
  annotations?: ComicAnnotationTools;
}

/**
 * Shows one page or a two-page spread in a shared scrolling viewport. Bubble regions are
 * invisible hit areas unless debug or annotate mode outlines them; hovering opens a bubble's
 * study card (when cards are enabled) and selects it for the study panel; clicking pins the
 * bubble until it is dismissed. Hovering another box while a card is open switches only after a
 * short rest, and reaching the open card cancels the switch. A text block's card steps through or
 * lists its lines, as the study controls choose. The study selection is a line; boxes are keyed
 * by their own id.
 */
export function ComicPageView({
  document,
  pages,
  api,
  zoom,
  scale,
  boxes,
  rtl,
  onLoaded,
  debug,
  study,
  annotations,
}: ComicPageViewProps) {
  const { viewport, area } = useComicViewport(zoom);
  const interactive = !annotations?.enabled && !annotations?.locked;
  const bubbles = useComicBubbles(study?.cards ?? false, !interactive, (id) =>
    study?.onSelect(id),
  );
  const studyTools: ComicStudyTools | undefined = study && {
    ...study,
    document,
    bubbles: interactive ? bubbles : null,
  };

  return (
    <section
      ref={viewport}
      className="comic-viewport"
      onPointerDown={(event) => {
        // Pressing empty page space releases a pinned bubble. Events bubbling from the
        // portalled card are not inside the viewport's DOM and are ignored.
        if (
          viewport.current?.contains(event.target as Node) &&
          !(event.target as Element).closest(".comic-region")
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
            pageId={page.id}
            imageUrl={`${api}/pages/${encodeURIComponent(page.id)}`}
            imageLabel={`${document.title}, page ${document.pages.indexOf(page) + 1}`}
            viewport={{ width: area.width / pages.length, height: area.height }}
            zoom={zoom}
            scale={scale}
            boxes={boxes
              .filter((box) => box.pageId === page.id)
              .map((box) => ({ box, number: boxes.indexOf(box) + 1 }))}
            onLoaded={slot === 0 ? onLoaded : undefined}
            outlined={Boolean(debug || annotations?.enabled)}
            study={studyTools}
            annotations={annotations}
          />
        ))}
      </div>
    </section>
  );
}
