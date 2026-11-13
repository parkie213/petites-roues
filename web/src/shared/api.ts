import { chunkSeparator } from "../player/languages.js";
import type {
  ChunkSeparators,
  ComicBox,
  ComicBoxView,
  CueView,
  DocumentView,
  StudyDocument,
  VideoCue,
} from "./types.ts";

/**
 * Fetches `url` and answers its JSON, a study document turned into its flat view. A failed
 * response throws its `responseError`. The caller names the answer's type; it is not checked.
 */
export async function request<T = unknown>(
  url: string,
  options?: RequestInit,
): Promise<T> {
  const response = await fetch(url, options);
  if (!response.ok) throw await responseError(response);
  const version = response.headers?.get?.("X-State-Version");
  const title = url.match(/^(\/api\/titles\/[^/?]+)/)?.[1];
  if (version && title) versions.set(title, version);
  const value = playerDocument(await response.json());
  const created = response.headers?.get?.("X-Created-Ids");
  if (created && value && typeof value === "object")
    Object.assign(value, { createdIds: created.split(",") });
  return value as T;
}

/** An Error from a failed API response, carrying its HTTP status. */
export type ResponseError = Error & { status: number };

/** The API's {error} message for a failed response, as an Error carrying the HTTP status. */
export async function responseError(
  response: Response,
): Promise<ResponseError> {
  const text = await response.text();
  let message = text;
  try {
    const body = JSON.parse(text);
    message = body.error || body.message || text;
  } catch {
    /* plain HTTP error */
  }
  return Object.assign(
    Error(message || `Request failed (${response.status})`),
    {
      status: response.status,
    },
  );
}

export function titlePath(id: string) {
  return `/api/titles/${encodeURIComponent(id)}`;
}

// The wire and disk contract is the nested document JSON. The display components use a
// flat view to combine teaching material with its video or page placement.
export function playerDocument(document: StudyDocument): DocumentView;
export function playerDocument<T>(document: T): T | DocumentView;
export function playerDocument(document: unknown) {
  if (!isStudyDocument(document)) return document;
  const separators = {
    sourceSeparator: chunkSeparator(document.sourceLanguage),
    translationSeparator: chunkSeparator(document.translationLanguage),
  };
  const { video, comic } = document;
  if (comic) {
    const pages = comic.pages || [];
    return {
      ...document,
      ...comic,
      type: "comic",
      readingDirection: comic.readingDirection || "ltr",
      pages: pages.map(({ boxes, ...page }) => page),
      boxes: pages.flatMap((page) =>
        (page.boxes || []).map((box, i) =>
          comicBox(box, page.id, i + 1, separators),
        ),
      ),
    } satisfies DocumentView;
  }
  return {
    ...document,
    ...video!,
    type: "video",
    cues: (video!.cues || []).map((cue) => videoCue(cue, separators)),
  } satisfies DocumentView;
}

function isStudyDocument(value: unknown): value is StudyDocument {
  const document = value as StudyDocument | null | undefined;
  return Boolean(document?.video || document?.comic);
}

/**
 * A video cue in the flat view: its line's fields beside its timing, and the separators its
 * phrase chunks join with (see `chunkSeparator`).
 */
function videoCue(
  { line, ...timing }: VideoCue,
  separators: ChunkSeparators,
): CueView {
  return { ...timing, ...line, ...separators, source: line?.source || "" };
}

/**
 * A comic box in the flat view: one list for the whole comic, each box knowing its `pageId` and
 * its one-based `order` on that page (its position in the page's `boxes`). Each of its lines knows
 * its page and its box's `boxId`, so a selected line can find its box, and its chunk separators.
 */
function comicBox(
  { lines, ...box }: ComicBox,
  pageId: string,
  order: number,
  separators: ChunkSeparators,
): ComicBoxView {
  return {
    ...box,
    pageId,
    order,
    lines: (lines || []).map((line) => ({
      ...line,
      ...separators,
      source: line.source || "",
      pageId,
      boxId: box.id,
    })),
  };
}

/**
 * Opens this computer's own file dialog (shown by the local server) for a repository `folder` or a
 * `media` file to import. Resolves to the chosen path, or null when cancelled.
 */
export async function chooseOnComputer(
  kind: "folder" | "media",
): Promise<string | null> {
  const { path } = await request<{ path: string | null }>("/api/files/choose", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ kind }),
  });
  return path;
}

/** Opaque versions and unsuccessful logical requests stay together for safe in-session retries. */
const versions = new Map<string, string>();
const pendingCommands = new Map<
  string,
  { requestId: string; expectedVersion: string; commands: unknown[] }
>();

/** Sends typed domain commands atomically. A failed transport retry reuses its original identity. */
export async function sendCommands<T>(
  api: string,
  commands: unknown[],
): Promise<T> {
  const title = api.replace(/\/comic$/, "");
  const key = title + JSON.stringify(commands);
  let body = pendingCommands.get(key);
  if (!body) {
    if (!versions.has(title)) await request(title);
    const expectedVersion = versions.get(title);
    if (!expectedVersion) throw Error("Reload this document before editing");
    body = { requestId: crypto.randomUUID(), expectedVersion, commands };
    pendingCommands.set(key, body);
  }
  try {
    const result = await request<T>(title + "/commands", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    pendingCommands.delete(key);
    return result;
  } catch (failure) {
    const status = (failure as ResponseError).status;
    if (status && status < 500) pendingCommands.delete(key);
    throw failure;
  }
}
