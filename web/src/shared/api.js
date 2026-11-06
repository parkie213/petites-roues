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
  return {
    ...document,
    ...body,
    type: comic ? "comic" : "video",
    ...(comic
      ? {
          readingDirection: body.readingDirection || "ltr",
          boxes: (body.boxes || []).map(comicBox),
        }
      : { cues: (body.cues || []).map(videoCue) }),
  };
}

/** A video cue in the flat view: its line's fields beside its timing. */
function videoCue({ line, ...timing }) {
  return { ...timing, ...line, source: line?.source || "" };
}

/**
 * A comic box in the flat view keeps its own `id` and placement; each of its lines knows its page
 * and its box's `boxId`, so a selected line can find its box.
 */
function comicBox({ lines, ...box }) {
  return {
    ...box,
    lines: (lines || []).map((line) => ({
      ...line,
      source: line.source || "",
      pageId: box.pageId,
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
