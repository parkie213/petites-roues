import { chapterQuiz } from "../comic/chapter-quiz.js";

/** Canned API for clients under /demo/ only. The real application UI and requests are unchanged. */
const data = fetch(new URL("demo-data.json", self.location.href), {
  cache: "no-store",
}).then((response) => {
  if (!response.ok) throw new Error("Could not load demo data");
  return response.json();
});
const PROGRESS_CACHE = "petites-roues-demo-progress-v1";
const progressKey = new URL("saved-progress", self.registration.scope).href;
const json = (value, status = 200) => Response.json(value, { status });

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) =>
  event.waitUntil(self.clients.claim()),
);

async function progress() {
  const cache = await caches.open(PROGRESS_CACHE);
  const saved = await cache.match(progressKey);
  return saved ? saved.json() : { pageId: null, updatedAt: null };
}

async function respond(request) {
  const { document, recordings, notes, quizzes } = await data;
  const url = new URL(request.url);
  const path = url.pathname;
  const api = `/api/titles/${encodeURIComponent(document.id)}`;
  const comic = api + "/comic";
  const pages = document.comic.pages;
  const asset = (path) =>
    fetch(new URL(path, self.registration.scope), { signal: request.signal });
  if (path === comic + "/progress") {
    if (request.method === "GET") return json(await progress());
    if (request.method === "PUT") {
      const body = await request.json();
      if (!pages.some((page) => page.id === body.pageId))
        return json({ error: "Unknown comic page" }, 400);
      const saved = {
        pageId: body.pageId,
        updatedAt: new Date().toISOString(),
      };
      const cache = await caches.open(PROGRESS_CACHE);
      await cache.put(progressKey, json(saved));
      return json(saved);
    }
    if (request.method === "DELETE") {
      const cache = await caches.open(PROGRESS_CACHE);
      await cache.delete(progressKey);
      return new Response(null, { status: 204 });
    }
  }
  if (path === comic + "/quiz" && request.method === "POST") {
    try {
      return json(chapterQuiz(document, await request.json()));
    } catch (error) {
      return json({ error: error.message }, 400);
    }
  }
  if (request.method !== "GET")
    return json({ error: "This action needs the desktop server." }, 403);
  if (path === comic) return json(document);
  if (path.startsWith(comic + "/pages/")) {
    const id = decodeURIComponent(path.slice((comic + "/pages/").length));
    if (pages.some((page) => page.id === id))
      return asset(`pages/${encodeURIComponent(id)}.jpg`);
  }
  if (path === api + "/speech") {
    const file = recordings.clips[url.searchParams.get("text")];
    return file
      ? asset(`audio/${file}`)
      : json({ error: "No recording for this text." }, 404);
  }
  if (path === api + "/card")
    return asset(`pages/${encodeURIComponent(pages[0].id)}.jpg`);
  if (path === "/api/jobs/latest") return new Response(null, { status: 204 });
  if (path === "/api/notes") return json(notes);
  if (path === "/api/quizzes") return json(quizzes);
  if (path === "/api/library/usage")
    return json({
      libraryBytes: document.comic.reference.sizeBytes,
      disks: [],
    });
  const saved = await progress();
  const position = pages.findIndex((page) => page.id === saved.pageId);
  const entry = {
    id: document.id,
    documentId: document.id,
    title: document.title,
    sourceLanguage: document.sourceLanguage,
    type: "comic",
    repositoryId: "demo",
    repositoryName: "Demo",
    relativePath: "comics/lulu/document.json",
    documentAvailable: true,
    mediaAvailable: true,
    videoAvailable: false,
    posterAvailable: true,
    seriesPosterAvailable: false,
    unavailable: {},
    progress: position >= 0 ? (position + 1) / pages.length : null,
    series: null,
    season: null,
    episode: null,
    volume: null,
    shelf: null,
    fingerprint: null,
    linkedByHand: false,
  };
  const repository = {
    id: "demo",
    name: "Demo",
    localPath: "Sample comic",
    titles: 1,
    media: 0,
    missing: 0,
    lastRefreshedAt: null,
  };
  if (path === "/api/library")
    return json({
      repositories: [{ repository, entries: [entry], media: [] }],
    });
  if (path === "/api/library/posters") {
    const matches =
      (!url.searchParams.get("language") ||
        url.searchParams.get("language") === document.sourceLanguage) &&
      (!url.searchParams.get("repository") ||
        url.searchParams.get("repository") === repository.id);
    const art = { url: api + "/card" };
    const slot = {
      key: "d:" + document.id,
      entry,
      copies: [entry],
      label: document.title,
      detail: null,
      art,
    };
    const group = {
      key: "comic:" + document.id,
      shelf: "comics",
      type: "comic",
      language: document.sourceLanguage,
      name: document.title,
      single: true,
      slots: [slot],
      seasons: [],
      count: "1 comic",
      art,
    };
    return json({
      shelves: matches
        ? [{ key: "comics", label: "Comics", groups: [group] }]
        : [],
      folders: [repository.id],
      multilingual: false,
    });
  }
  return json({ error: "Not available in the demo." }, 404);
}

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (url.origin === self.location.origin && url.pathname.startsWith("/api/")) {
    event.respondWith(
      respond(event.request).catch((error) =>
        json({ error: error.message }, 500),
      ),
    );
  }
});
