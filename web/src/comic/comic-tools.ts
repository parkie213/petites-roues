import type { ComicBoxView, ComicRegion, ComicView } from "../shared/types.ts";
import type { ComicBubbles } from "./useComicBubbles.ts";

/** Both a bare CBZ and a study document supply a title and ordered page identities. */
export type ComicPageManifest = Pick<ComicView, "title"> & {
  pages: { id: string }[];
};

/** The reader's selected sentence and how its study cards should behave. */
export interface ComicStudyControls {
  selectedId?: string;
  onSelect: (id: string) => void;
  cards: boolean;
  blockMode?: string;
  onBlockMode?: (mode: string) => void;
}

/** Study controls together with the document and spread-wide card interaction state. */
export interface ComicStudyTools extends ComicStudyControls {
  document: ComicPageManifest;
  bubbles: ComicBubbles | null;
}

/** Annotation mode, its navigation/edit lock, and completed edits to send back to the reader. */
export interface ComicAnnotationTools {
  enabled: boolean;
  locked: boolean;
  onDraw?: (region: ComicRegion, pageId: string) => void;
  onPlacement?: (box: ComicBoxView, regions: ComicRegion[]) => void;
}
