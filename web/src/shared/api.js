import { chunkSeparator } from "../player/languages.js";

export async function request(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) throw await responseError(response);
  return playerDocument(await response.json());
}

/** The API's {error} message for a failed response, as an Error carrying the HTTP status. */
export async function responseError(response) {
  const text = await response.text();
  let message = text;
  try {
    const body = JSON.parse(text);
    message = body.error || body.message || text;
  } catch {
    /* plain HTTP error */
  }
  const error = Error(message || `Request failed (${response.status})`);
  error.status = response.status;
  return error;
}

export function titlePath(id) {
  return `/api/titles/${encodeURIComponent(id)}`;
}

// The wire and disk contract is the nested document JSON. The display components use a
// flat view to combine teaching material with its video or page placement.
export function playerDocument(document) {
  const body = document?.video || document?.comic;
  if (!body) return document;
  const comic = Boolean(document.comic);
  const separators = {
    sourceSeparator: chunkSeparator(document.sourceLanguage),
    translationSeparator: chunkSeparator(document.translationLanguage),
  };
  return {
    ...document,
    ...body,
    type: comic ? "comic" : "video",
    ...(comic
      ? {
          readingDirection: body.readingDirection || "ltr",
          pages: (body.pages || []).map(({ boxes, ...page }) => page),
          boxes: (body.pages || []).flatMap((page) =>
            (page.boxes || []).map((box, i) =>
              comicBox(box, page.id, i + 1, separators),
            ),
          ),
        }
      : { cues: (body.cues || []).map((cue) => videoCue(cue, separators)) }),
  };
}

/**
 * A video cue in the flat view: its line's fields beside its timing, and the separators its
 * phrase chunks join with (see `chunkSeparator`).
 */
function videoCue({ line, ...timing }, separators) {
  return { ...timing, ...line, ...separators, source: line?.source || "" };
}

/**
 * A comic box in the flat view: one list for the whole comic, each box knowing its `pageId` and
 * its one-based `order` on that page (its position in the page's `boxes`). Each of its lines knows
 * its page and its box's `boxId`, so a selected line can find its box, and its chunk separators.
 */
function comicBox({ lines, ...box }, pageId, order, separators) {
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
export async function chooseOnComputer(kind) {
  const { path } = await request("/api/files/choose", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ kind }),
  });
  return path;
}
