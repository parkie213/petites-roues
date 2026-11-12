import type { ComicBoxView, ComicRegion, ComicView } from "../shared/types.ts";

/** A page identity works for both bare CBZ pages and pages of a study document. */
export interface ComicPageItem {
  id: string;
}

/** The page renderer needs only a title and manifest; study cards receive the full document. */
export type ComicPageDocument = Pick<ComicView, "title"> & {
  pages: ComicPageItem[];
};

/** Optional study and annotation tools; bare comics use the same renderer without them. */
export interface ComicPageViewProps {
  document: ComicPageDocument;
  pages: ComicPageItem[];
  api: string;
  zoom: string;
  scale: number;
  boxes: ComicBoxView[];
  rtl: boolean;
  selectedId?: string;
  onSelect: (id: string) => void;
  onLoaded?: () => void;
  debug?: boolean;
  cards: boolean;
  blockMode?: string;
  onBlockMode?: (mode: string) => void;
  editing?: boolean;
  annotate?: boolean;
  onDraw?: (region: ComicRegion, pageId: string) => void;
  onPlacement?: (box: ComicBoxView, regions: ComicRegion[]) => void;
}

/** Position in a page image, measured as a fraction of its rendered dimensions. */
export interface PagePoint {
  x: number;
  y: number;
}
