import { createHash } from "node:crypto";
import { mkdir, readdir, readFile, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { test as base, expect } from "@playwright/test";
import { forget, forgetProgress } from "./real-server.mjs";
import { checkRequests } from "./requests.mjs";
import {
  compactWrites,
  matchesWrite,
  recordWrite,
  replayWrite,
} from "./write-recordings.mjs";
export { expectWrite, readJson } from "./requests.mjs";

/**
 * The browser tests' API: recorded from the real Java server, then replayed.
 *
 * `npm run e2e:record` runs each test against the real server (see `real-server.mjs`) and saves
 * an answer to every `/api` request it makes, in order, to `fixtures/<file>/<test>.json` (a GET's
 * as the recorder fetches it again, a save's as the page received it); images go to
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
// Library progress is a displayed fraction. Persisted playback positions stay exact: rounding a
// cue start (7.02 to 7.0, for example) restores the wrong subtitle. Live PUTs use a checked template.
const coarse = { progress: 100 };

/**
 * [value] with run-to-run metadata (timestamps, free space and displayed progress fractions)
 * fixed. Persisted playback positions retain their precision.
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
  if (type.includes("json")) {
    const answer = { ...entry, json: JSON.parse(text()) };
    if (
      request.method() === "GET" &&
      response.status() === 200 &&
      new URL(request.url()).pathname === "/api/speech/voices/catalogue"
    ) {
      // The separately installed Siwis fixture can differ by upstream revision. Only catalogue
      // display sizes are approximate; document/media sizes retain their exact domain values.
      const megabyte = 1024 * 1024;
      answer.json.voices = answer.json.voices.map((voice) => ({
        ...voice,
        sizeBytes: Math.round(voice.sizeBytes / megabyte) * megabyte,
      }));
    }
    return stable(
      request.method() === "GET"
        ? answer
        : recordWrite(
            answer,
            JSON.parse(portable(JSON.stringify(request.postDataJSON()))),
          ),
    );
  }
  // A job's event stream is kept to its last event, the outcome: how many progress events come
  // before it depends on the machine's speed.
  if (type.startsWith("text/event-stream"))
    return { ...entry, text: text().trim().split(/\n\n+/).at(-1) + "\n\n" };
  if (type.startsWith("text/")) return { ...entry, text: text() };
  if (body.length === 0)
    return request.method() === "GET"
      ? entry
      : { ...entry, body: request.postDataJSON() };
  if (type.startsWith("video/")) {
    const file = await exampleFile(body);
    return file ? { ...entry, file } : { ...entry, omitted: "video" };
  }
  if (
    type.startsWith("audio/wav") &&
    new URL(request.url()).pathname.endsWith("/preview") &&
    response.status() === 200
  ) {
    // Piper's generated waveform varies. Recording still calls real JNI and the browser plays
    // that response; replay needs stable audio bytes rather than a new blob on every recording.
    expect(body.subarray(0, 4).toString()).toBe("RIFF");
    expect(body.length).toBeGreaterThan(44);
    expect(body.subarray(44).some((sample) => sample !== 0)).toBe(true);
    return {
      ...entry,
      file: "java/desktop-test-fixtures/src/main/resources/media/voice-preview.wav",
    };
  }
  if (type.startsWith("audio/mpeg")) {
    const file =
      "java/desktop-test-fixtures/src/main/resources/media/voice-sample.mp3";
    if (body.equals(await readFile(path.join(repository, file))))
      return { ...entry, file };
  }
  const extension =
    {
      "audio/mpeg": ".mp3",
      "audio/wav": ".wav",
      "image/png": ".png",
      "image/webp": ".webp",
    }[type.split(";")[0]] || ".jpg";
  const name = sha256(body).slice(0, 16) + extension;
  await mkdir(blobs, { recursive: true });
  await writeFile(path.join(blobs, name), body);
  return { ...entry, blob: name };
}

async function record(page, file, run) {
  // A previous test's last progress save may have landed after it was undone.
  await forgetProgress();
  const entries = [];
  const pending = new Set();
  const failures = [];
  const keep = (saving) => {
    const saved = saving
      .then((entry) => entry && entries.push(entry))
      .catch((error) => failures.push(error));
    pending.add(saved);
    saved.finally(() => pending.delete(saved));
  };
  const isApi = (request) =>
    new URL(request.url()).pathname.startsWith("/api/");
  // GETs [url] for the recording, following redirects (a card or video to its current version)
  // one by one so each is recorded as such, as the page's own would be.
  const fetchGet = async (url) => {
    for (;;) {
      const asked = { method: () => "GET", url: () => url };
      const response = await page.request.get(url, { maxRedirects: 0 });
      const location = response.headers().location;
      if (response.status() < 300 || response.status() >= 400 || !location)
        return save(asked, response);
      const target = new URL(location, url);
      entries.push({
        request: key(asked),
        status: 302,
        location: target.pathname + target.search,
      });
      url = target.href;
    }
  };
  // Each GET is fetched again by the recorder the moment the page asks, and that copy saved: the
  // page's own may never finish (it moved on, or closed a job's event stream as the job ended),
  // and whether it did would otherwise change the recording.
  page.on("request", (request) => {
    if (!isApi(request) || request.method() !== "GET") return;
    if (request.redirectedFrom()) return; // fetched with the request it came from
    keep(fetchGet(request.url()));
  });
  // Saves can't be sent twice, so the page's own answers are kept. Responses are watched rather
  // than intercepted: answering a request ourselves would hold up the keepalive progress save a
  // page sends as it unloads, and the navigation with it.
  page.on("response", (response) => {
    const request = response.request();
    if (!isApi(request) || request.method() === "GET") return;
    keep(save(request, response));
  });
  await run(page);
  // What the page is still loading when the test ends (posters behind Back) is saved too.
  await page.waitForLoadState("networkidle");
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
  // The server remembers its last job (another test's Add folder, say) until it restarts; a test
  // that started none is recorded as seeing none, as on a fresh library.
  if (!entries.some((entry) => entry.request === "POST /api/jobs"))
    for (const [index, entry] of entries.entries())
      if (entry.request === "GET /api/jobs/latest")
        entries[index] = { request: entry.request, status: 204, type: "" };
  const saved = compactWrites(entries)
    .map((entry) => standIn(JSON.stringify(entry)))
    // Identical answers to the same request are kept once.
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
    const asked = route.request();
    const request = key(asked);
    const write = !["GET", "HEAD"].includes(asked.method());
    const body = write
      ? stable(JSON.parse(portable(JSON.stringify(asked.postDataJSON()))))
      : null;
    const queue = queues
      .get(request)
      ?.filter((entry) => !write || matchesWrite(entry, body));
    if (!queue?.length) {
      missing.push(write ? `${request} ${JSON.stringify(body)}` : request);
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
    let entry;
    if (write) {
      // Same-payload repeats share a contract; different payloads require their own recording.
      const index = served.get(queue[0]) ?? 0;
      served.set(queue[0], index + 1);
      entry = replayWrite(
        queue[Math.min(index, queue.length - 1)],
        asked.postDataJSON(),
      );
    } else {
      entry = next(request);
    }
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

/**
 * Compares [page] with its reference screenshot [name]. Screenshots are only compared in
 * Playwright's Docker image (`npm run e2e:screenshots`, as CI runs them), where text renders the
 * same for everyone; elsewhere, and while recording, this does nothing. A video's own frame is
 * hidden for it, since a pause can land a frame apart from run to run; what the page draws over
 * the video stays.
 */
export async function looksLike(page, name) {
  if (!process.env.SCREENSHOTS || RECORD) return;
  // Injected rather than passed as the screenshot's `style`, which left the video showing.
  const hidden = await page.addStyleTag({
    content: "video { visibility: hidden !important; }",
  });
  await expect(page).toHaveScreenshot(`${name}.png`);
  await hidden.evaluate((style) => style.remove());
}

/** Opt-in diagnostics: media events and stacks for script-driven seeks/reloads, retained on failure. */
async function logMediaEvents(page, run, info) {
  const events = [];
  const collect = (message) => {
    if (message.text().startsWith("[media] ")) events.push(message.text());
  };
  page.on("console", collect);
  await page.addInitScript(() => {
    const prototype = globalThis.HTMLMediaElement.prototype;
    const time = Object.getOwnPropertyDescriptor(prototype, "currentTime");
    const logWrite = (video, operation, value) => {
      console.debug(
        "[media] " +
          JSON.stringify({
            operation,
            value,
            timestamp: performance.now(),
            currentTime: video.currentTime,
            currentSrc: video.currentSrc,
            stack: new Error().stack,
          }),
      );
    };
    Object.defineProperty(prototype, "currentTime", {
      ...time,
      set(value) {
        logWrite(this, "set currentTime", value);
        time.set.call(this, value);
      },
    });
    const load = prototype.load;
    prototype.load = function (...args) {
      logWrite(this, "load");
      return load.apply(this, args);
    };
    for (const type of [
      "loadstart",
      "emptied",
      "loadedmetadata",
      "loadeddata",
      "canplay",
      "seeking",
      "seeked",
      "play",
      "playing",
      "pause",
      "ended",
      "abort",
      "error",
      "stalled",
      "waiting",
    ]) {
      globalThis.document.addEventListener(
        type,
        (event) => {
          const video = event.target;
          if (!(video instanceof globalThis.HTMLVideoElement)) return;
          console.debug(
            "[media] " +
              JSON.stringify({
                event: type,
                timestamp: performance.now(),
                currentTime: video.currentTime,
                readyState: video.readyState,
                networkState: video.networkState,
                paused: video.paused,
                seeking: video.seeking,
                currentSrc: video.currentSrc,
                error: video.error && {
                  code: video.error.code,
                  message: video.error.message,
                },
              }),
          );
        },
        true,
      );
    }
  });
  try {
    await run(page);
  } finally {
    page.off("console", collect);
    await info.attach("media-events", {
      body: events.join("\n"),
      contentType: "text/plain",
    });
  }
}

/** Playwright's `test`, with every page's `/api` requests recorded or replayed. */
export const test = base.extend({
  logMedia: [false, { option: true }],
  pauseOnOpen: [false, { option: true }],
  page: async ({ page, logMedia, pauseOnOpen }, use, info) => {
    if (pauseOnOpen) {
      // Muted autoplay can succeed even with Chromium's gesture policy. Pause the first play
      // of each video before its clock advances; later user-initiated playback remains real.
      await page.addInitScript(() => {
        const opened = new WeakSet();
        globalThis.document.addEventListener(
          "play",
          (event) => {
            const video = event.target;
            if (
              !(video instanceof globalThis.HTMLVideoElement) ||
              opened.has(video)
            )
              return;
            opened.add(video);
            video.pause();
          },
          true,
        );
      });
    }
    const file = recordingFile(info);
    const run = logMedia ? (page) => logMediaEvents(page, use, info) : use;
    await checkRequests(page, (page) =>
      RECORD ? record(page, file, run) : replay(page, file, run),
    );
  },
});

export { expect };
