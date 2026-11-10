import { createHash } from "node:crypto";
import { mkdir, readdir, readFile, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { test as base, expect } from "@playwright/test";
import { forget } from "./real-server.mjs";

/**
 * The browser tests' API: recorded from the real Java server, then replayed.
 *
 * `npm run e2e:record` runs each test against the real server (see `real-server.mjs`) and saves
 * every `/api` response it makes, in order, to `fixtures/<file>/<test>.json`; images go to
 * `fixtures/blobs/`, named by their content, and a video as the `example/` file it came from.
 * Run-to-run values (times, free space, random ids) are recorded as fixed stand-ins, so recording
 * twice gives the same files. `npm run e2e` replays them, so the tests need only
 * Node and a browser. A request with no recording fails the test, so the recordings can't quietly
 * fall behind the app; re-record after changing what a journey does or what the API returns. CI
 * re-records and fails if the recordings changed, which catches the API drifting from them.
 */
export const RECORD = Boolean(process.env.RECORD);

const fixtures = path.join(import.meta.dirname, "fixtures");
const blobs = path.join(fixtures, "blobs");
const repository = path.resolve(import.meta.dirname, "../..");

const slug = (text) =>
  text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");

function recordingFile(info) {
  const spec = path.basename(info.file).replace(/\.spec\.mjs$/, "");
  return path.join(fixtures, spec, `${slug(info.title)}.json`);
}

const key = (request) => {
  const url = new URL(request.url());
  return `${request.method()} ${url.pathname}${url.search}`;
};

// Paths on the recording machine are saved relative to the repository, so CI and every checkout
// record the same JSON.
const portable = (text) => text.replaceAll(repository, "<repository>");

const instant = /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/;
const disk = new Set(["freeBytes", "totalBytes"]);
// Where a video stopped varies by a frame between runs, so positions are kept coarse.
const coarse = { positionSeconds: 10, progress: 100 };

/**
 * [value] with what differs from run to run (times, the disk's free space, a video's exact
 * position) fixed.
 */
function stable(value, name) {
  if (Array.isArray(value)) return value.map((item) => stable(item));
  // How far a running job has got depends on the machine's speed.
  if (value?.kind && value.state === "running" && value.progress)
    return stable({ ...value, progress: null }, name);
  if (value && typeof value === "object")
    return Object.fromEntries(
      Object.entries(value).map(([k, v]) => [k, stable(v, k)]),
    );
  if (typeof value === "string" && instant.test(value))
    return "2026-01-01T00:00:00Z";
  if (disk.has(name) && typeof value === "number") return 500_000_000_000;
  if (name in coarse && typeof value === "number")
    return Math.round(value * coarse[name]) / coarse[name];
  return value;
}

const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

/** The `example/` file, relative to the repository, holding exactly [body]; null if none does. */
async function exampleFile(body) {
  const folder = path.join(repository, "example");
  for (const name of await readdir(folder, { recursive: true })) {
    const file = path.join(folder, name);
    const info = await stat(file);
    if (
      info.isFile() &&
      info.size === body.length &&
      sha256(await readFile(file)) === sha256(body)
    )
      return path.relative(repository, file);
  }
  return null;
}

// A random (version 4) UUID, such as a job's id. Documents' own ids are name-based (version 5).
const randomId =
  /[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/g;

// A media file's or card's version in its URL, made from the file's path, size and modified time,
// which differ between checkouts.
const fileVersion =
  /\/(media|video|card|series-card)\/(?:\d+-\d+|[0-9a-f]{20})(?=$|[/?"])/g;

/**
 * Replaces each random id in a recording with the next fixed one, the same one wherever it recurs,
 * and file versions with a fixed one.
 */
function idStandIns() {
  const ids = new Map();
  return (text) =>
    text
      .replace(randomId, (id) => {
        if (!ids.has(id))
          ids.set(
            id,
            `00000000-0000-4000-8000-${String(ids.size + 1).padStart(12, "0")}`,
          );
        return ids.get(id);
      })
      .replace(fileVersion, "/$1/recorded");
}

/** [body] answering a request for [range] (`bytes=start-end`), as the real server would. */
function ranged(range, body, type) {
  const [, from, to] = /^bytes=(\d*)-(\d*)$/.exec(range ?? "") ?? [];
  if (from === undefined)
    return { status: 200, headers: { "content-type": type }, body };
  const start = Number(from || 0);
  const end = to ? Math.min(Number(to), body.length - 1) : body.length - 1;
  return {
    status: 206,
    headers: {
      "content-type": type,
      "accept-ranges": "bytes",
      "content-range": `bytes ${start}-${end}/${body.length}`,
    },
    body: body.subarray(start, end + 1),
  };
}

/** One recorded response: JSON inline, text as is, a video by its file, anything else as a blob. */
async function save(request, response) {
  const type = response.headers()["content-type"] || "";
  const entry = { request: key(request), status: response.status(), type };
  const body = await response.body();
  const text = () => portable(body.toString());
  if (type.includes("json"))
    return { ...entry, json: stable(JSON.parse(text())) };
  // A job's event stream is kept to its last event, the outcome: how many progress events come
  // before it depends on the machine's speed.
  if (type.startsWith("text/event-stream"))
    return { ...entry, text: text().trim().split(/\n\n+/).at(-1) + "\n\n" };
  if (type.startsWith("text/")) return { ...entry, text: text() };
  if (body.length === 0) return entry;
  if (type.startsWith("video/")) {
    const file = await exampleFile(body);
    return file ? { ...entry, file } : { ...entry, omitted: "video" };
  }
  const name =
    sha256(body).slice(0, 16) +
    (type.includes("png") ? ".png" : type.includes("webp") ? ".webp" : ".jpg");
  await mkdir(blobs, { recursive: true });
  await writeFile(path.join(blobs, name), body);
  return { ...entry, blob: name };
}

const events = /\/api\/jobs\/[^/]+\/events$/;

async function record(page, file, run) {
  const entries = [];
  const pending = new Set();
  const failures = [];
  const keep = (saving) => {
    const saved = saving
      .then((entry) => entries.push(entry))
      .catch((error) => {
        // A response the browser abandoned (the page navigated away) has no body; anything else
        // is a fault in recording.
        if (!/disposed|navigated away/.test(error.message))
          failures.push(error);
      });
    pending.add(saved);
    saved.finally(() => pending.delete(saved));
  };
  // Responses are watched rather than intercepted: answering a request ourselves would hold up
  // the keepalive progress save a page sends as it unloads, and the navigation with it.
  page.on("response", (response) => {
    const { pathname } = new URL(response.url());
    if (!pathname.startsWith("/api/") || events.test(pathname)) return;
    // A redirect (a card or video to its current version) is recorded with the response it ends
    // at: each hop as a redirect, then the target.
    if (response.status() >= 300 && response.status() < 400) return;
    const redirects = [];
    for (
      let from = response.request().redirectedFrom();
      from;
      from = from.redirectedFrom()
    )
      redirects.unshift({
        request: key(from),
        status: 302,
        location: key(from.redirectedTo()).split(" ")[1],
      });
    entries.push(...redirects);
    keep(save(response.request(), response));
  });
  // A job's event stream is fetched whole and saved here: the page closes it as the job finishes,
  // before the server ends it, which would leave nothing to watch, and on a fast machine before
  // the page's own copy can be read.
  await page.route(events, async (route) => {
    const response = await route.fetch();
    keep(save(route.request(), response));
    await route.fulfill({ response });
  });
  await run(page);
  // What the page is still loading when the test ends (posters behind Back) is saved too.
  await page.waitForLoadState("networkidle").catch(() => {});
  await Promise.allSettled([...pending]);
  if (failures.length)
    throw new AggregateError(failures, "Saving responses failed");
  // What the test saved is undone, so the next test starts from the same library.
  await forget(entries.map((entry) => entry.request));
  // Responses that arrive together (two posters) come in either order, so they are saved sorted by
  // request, each request's own responses still in order; stand-ins are numbered after that.
  const unordered = (entry) => entry.request.replace(randomId, "");
  entries.sort((a, b) => unordered(a).localeCompare(unordered(b)));
  const standIn = idStandIns();
  const saved = entries
    .map((entry) => standIn(JSON.stringify(entry)))
    // How often a page saves its position depends on timing; one save answers them all.
    .filter((entry, index, all) => entry !== all[index - 1])
    .map((entry) => JSON.parse(entry));
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, JSON.stringify(saved, null, 2) + "\n");
}

async function replay(page, file, run) {
  let entries;
  try {
    entries = JSON.parse(await readFile(file, "utf8"));
  } catch {
    throw Error(
      `No recording at ${path.relative(fixtures, file)}: run npm run e2e:record`,
    );
  }
  // Each request's responses in the order they were recorded; the last one repeats.
  const queues = Map.groupBy(entries, (entry) => entry.request);
  const served = new Map();
  const missing = [];
  await page.route("**/api/**", async (route) => {
    const request = key(route.request());
    const queue = queues.get(request);
    if (!queue) {
      missing.push(request);
      return route.fulfill({
        status: 404,
        json: { error: `Not recorded: ${request}` },
      });
    }
    const next = (request) => {
      const queue = queues.get(request);
      const index = served.get(request) ?? 0;
      served.set(request, index + 1);
      return queue[Math.min(index, queue.length - 1)];
    };
    let entry = next(request);
    // A redirect is followed here: the browser doesn't route a media element's redirected request.
    while (entry.location && queues.has(`GET ${entry.location}`))
      entry = next(`GET ${entry.location}`);
    const headers = { "content-type": entry.type };
    if ("json" in entry)
      return route.fulfill({ status: entry.status, json: entry.json });
    if ("text" in entry)
      return route.fulfill({ status: entry.status, headers, body: entry.text });
    if (entry.blob)
      return route.fulfill({
        status: entry.status,
        headers,
        body: await readFile(path.join(blobs, entry.blob)),
      });
    if (entry.file) {
      const body = await readFile(path.join(repository, entry.file));
      return route.fulfill(
        ranged(route.request().headers().range, body, entry.type),
      );
    }
    return route.fulfill({
      status: entry.omitted ? 404 : entry.status,
      headers,
    });
  });
  await run(page);
  expect(missing, "requests with no recording; run npm run e2e:record").toEqual(
    [],
  );
}

/** Playwright's `test`, with every page's `/api` requests recorded or replayed. */
export const test = base.extend({
  page: async ({ page }, use, info) => {
    const file = recordingFile(info);
    await (RECORD ? record(page, file, use) : replay(page, file, use));
  },
});

export { expect };
