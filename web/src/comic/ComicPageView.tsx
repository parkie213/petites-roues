import { ComicPage } from "./ComicPage.tsx";
import type { ComicPageViewProps } from "./comic-page.ts";
import { useComicViewport } from "./useComicViewport.ts";
import { useComicBubbles } from "./useComicBubbles.ts";

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
}: ComicPageViewProps) {
  const { viewport, area } = useComicViewport(zoom);
  const interactive = !annotate && !editing;
  const bubbles = useComicBubbles(cards, !interactive, onSelect);

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
            outlined={Boolean(debug || annotate)}
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
