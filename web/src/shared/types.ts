/**
 * The shapes the server's JSON API answers with, written by hand from the Java records that
 * produce them (`app.petitesroues.model.document` and `app.petitesroues.model.library`). They
 * describe only what the web reads; a field the server adds is simply not typed until it is used.
 * Fields the server may leave out or send as null are optional.
 */

/** A phrase link: which source chunks translate as which translation chunks, by index. */
export interface PhraseLink {
  source: number[];
  translation: number[];
}

/** A line split into phrase chunks, with the links between them. */
export interface PhraseAlignment {
  source: string[];
  translation: string[];
  links: PhraseLink[];
}

/** One line of study material: its text, translation and learner notes. */
export interface Line {
  id: string;
  source?: string | null;
  translation?: string | null;
  notes?: string[] | null;
  alignment?: PhraseAlignment | null;
  /** Whether the line carries on the sentence of the line before it. */
  continuesPrevious?: boolean;
  textCheck?: string | null;
}

/** A subtitle: a line and when it is spoken. */
export interface VideoCue {
  line?: Line | null;
  start: number;
  end: number;
  listeningCheck?: string | null;
}

/** A box's place on its page, each value a fraction of the page's width or height. */
export interface ComicRegion {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** A speech bubble or caption: where it is on its page and the lines it holds. */
export interface ComicBox {
  id: string;
  regions: ComicRegion[];
  lines: Line[];
}

export interface ComicPage {
  id: string;
  entry: string;
  label?: string | null;
  boxes: ComicBox[];
}

export interface ComicChapter {
  number?: number | null;
  title?: string | null;
  pageId: string;
}

export type ReadingDirection = "ltr" | "rtl";

export interface VideoBody {
  season?: number | null;
  episode?: number | null;
  reference?: unknown;
  cues: VideoCue[];
}

export interface ComicBody {
  volume?: number | null;
  readingDirection?: ReadingDirection | null;
  reference?: unknown;
  pages: ComicPage[];
  chapters?: ComicChapter[] | null;
}

/** A study document as stored and served: its languages, then a video or a comic body. */
export interface StudyDocument {
  id: string;
  title?: string | null;
  sourceLanguage: string;
  translationLanguage: string;
  notesLanguage: string;
  series?: string | null;
  shelf?: string | null;
  sourceUrl?: string | null;
  video?: VideoBody;
  comic?: ComicBody;
}

/** What a line's phrase chunks join back up with (see `chunkSeparator`). */
export interface ChunkSeparators {
  sourceSeparator: string;
  translationSeparator: string;
}

/**
 * A cue in the player's flat view: its timing and its line's fields side by side, with an empty
 * source rather than none. A cue without a line has none of the line's fields, not even an id.
 */
export type CueView = Omit<VideoCue, "line"> &
  Partial<Omit<Line, "source">> &
  ChunkSeparators & { source: string };

/** A comic line in the flat view, knowing its page and box. */
export type ComicLineView = Omit<Line, "source"> &
  ChunkSeparators & { source: string; pageId: string; boxId: string };

/** A comic box in the flat view: its page and one-based order there, and its lines. */
export type ComicBoxView = Omit<ComicBox, "lines"> & {
  pageId: string;
  order: number;
  lines: ComicLineView[];
};

type DocumentFields = Omit<StudyDocument, "video" | "comic">;

/** A video document as the player shows it. */
export type VideoView = DocumentFields &
  Omit<VideoBody, "cues"> & { type: "video"; cues: CueView[] };

/** A comic document as the reader shows it: its pages without boxes, and every box in one list. */
export type ComicView = DocumentFields &
  Omit<ComicBody, "pages" | "readingDirection"> & {
    type: "comic";
    readingDirection: ReadingDirection;
    pages: Omit<ComicPage, "boxes">[];
    boxes: ComicBoxView[];
  };

export type DocumentView = VideoView | ComicView;

/** A study document in the library, with what is known about its files. */
export interface CatalogueItem {
  id: string;
  documentId: string;
  title?: string | null;
  series?: string | null;
  season?: number | null;
  episode?: number | null;
  volume?: number | null;
  shelf?: string | null;
  sourceLanguage?: string | null;
  sourceUrl?: string | null;
  repositoryId: string;
  repositoryName?: string | null;
  relativePath: string;
  durationSeconds?: number | null;
  documentAvailable: boolean;
  videoAvailable: boolean;
  /** What needs fixing, keyed by what is wrong, in display order. */
  unavailable?: Record<string, string> | null;
  type: "video" | "comic";
  mediaAvailable: boolean;
  posterAvailable: boolean;
  seriesPosterAvailable: boolean;
  progress?: number | null;
  mediaRepositoryId?: string | null;
  mediaPath?: string | null;
  mediaLinkedByHand: boolean;
}

/** A video or CBZ found in a repository; `titleId` names its study document, if it has one. */
export interface MediaItem {
  repositoryId: string;
  relativePath: string;
  series?: string | null;
  title?: string | null;
  kind: "video" | "comic";
  fingerprint: string;
  titleId?: string | null;
  linkedByHand: boolean;
  durationSeconds?: number | null;
  posterAvailable: boolean;
  seriesPosterAvailable: boolean;
  warnings?: string[] | null;
}

export interface ContentRepository {
  id: string;
  name: string;
  localPath: string;
  lastRefreshedAt?: string | null;
  titles: number;
  missing: number;
  media: number;
}

export interface RepositoryContents {
  repository: ContentRepository;
  entries: CatalogueItem[];
  media: MediaItem[];
}

/** `GET /api/library`. */
export interface Library {
  repositories: RepositoryContents[];
}
