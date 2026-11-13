/** Packages the normal app build, original sample comic and saved voice clips for GitHub Pages. */
import { createHash } from "node:crypto";
import { cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { unzipSync } from "fflate";
import { build } from "vite";

const web = path.dirname(fileURLToPath(import.meta.url));
const root = path.dirname(web);
const site = path.join(root, "site");
const output = path.join(site, "dist");
const demo = path.join(output, "demo");
const audio = path.join(site, "demo-audio");
const document = JSON.parse(
  await readFile(
    path.join(
      root,
      "example/documents-fr/comics/lulu/le-parapluie-de-lulu/document.json",
    ),
    "utf8",
  ),
);
// Read chapter scopes from the same comic document served by the desktop app.
let previous = -1;
for (const chapter of document.comic.chapters) {
  const index = document.comic.pages.findIndex(
    (page) => page.id === chapter.pageId,
  );
  if (!chapter.title?.trim() || index <= previous)
    throw new Error(
      "Comic chapters must have titles and start on distinct pages in reading order.",
    );
  previous = index;
}
async function markdownPages(kind) {
  const files = (await readdir(path.join(root, "example/documents-fr", kind)))
    .filter((file) => file.endsWith(".md"))
    .sort();
  return Promise.all(
    files.map(async (file) => {
      const text = await readFile(
        path.join(root, "example/documents-fr", kind, file),
        "utf8",
      );
      const id = file.slice(0, -3);
      return {
        repository: "demo",
        repositoryName: "Demo",
        id,
        title: text.match(/^# (.+)$/m)?.[1] ?? id,
        text,
      };
    }),
  );
}
const [notes, quizzes] = await Promise.all([
  markdownPages("notes"),
  markdownPages("quizzes"),
]);
const recordings = JSON.parse(
  await readFile(path.join(audio, "manifest.json"), "utf8"),
);

// Validate recording coverage before replacing a working preview.
if (recordings.sourceLanguage !== document.sourceLanguage)
  throw new Error(
    "Demo recordings have the wrong source language; regenerate them.",
  );
const texts = new Set(
  document.comic.pages
    .flatMap((page) =>
      (page.boxes ?? []).flatMap((box) =>
        box.lines.flatMap((line) => [
          line.source,
          ...(line.alignment?.source ?? []),
        ]),
      ),
    )
    .filter((text) => text.trim()),
);
const clips = new Map();
for (const text of texts) {
  const name = recordings.clips[text];
  if (!name || path.basename(name) !== name)
    throw new Error(
      `Missing demo recording for ${JSON.stringify(text)}; run site/demo_audio.py.`,
    );
  clips.set(name, await readFile(path.join(audio, name)));
}

// Same Vite config and entry point as the desktop app; only asset URLs are relative for Pages.
await build({ root: web, base: "./" });
await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });
for (const name of ["index.html", "favicon.svg"])
  await cp(path.join(site, name), path.join(output, name));
await cp(path.join(web, "dist"), demo, { recursive: true });
const indexPath = path.join(demo, "index.html");
const index = await readFile(indexPath, "utf8");
await writeFile(
  indexPath,
  index
    .replace('<html lang="en">', '<html lang="en" data-demo="true">')
    .replace('href="/favicon.png', 'href="./favicon.png'),
);

const entries = new Set(document.comic.pages.map((page) => page.entry));
const archive = unzipSync(
  await readFile(
    path.join(root, "example/media-fr/comics/lulu/le-parapluie-de-lulu.cbz"),
  ),
  { filter: (file) => entries.has(file.name) },
);
await mkdir(path.join(demo, "pages"));
for (const page of document.comic.pages) {
  if (!archive[page.entry])
    throw new Error(`Missing comic image: ${page.entry}`);
  await writeFile(
    path.join(demo, "pages", page.id + ".jpg"),
    archive[page.entry],
  );
}
await mkdir(path.join(demo, "audio"));
for (const [name, bytes] of clips)
  await writeFile(path.join(demo, "audio", name), bytes);
const data = JSON.stringify({ document, recordings, notes, quizzes });
await writeFile(path.join(demo, "demo-data.json"), data);
const version = createHash("sha256").update(data).digest("hex");
// Bundle the demo responder and its shared quiz helpers as a classic service worker.
await build({
  configFile: false,
  build: {
    outDir: demo,
    emptyOutDir: false,
    lib: {
      entry: path.join(web, "src/demo/demo-worker.js"),
      name: "DemoApi",
      formats: ["iife"],
      fileName: () => "demo-worker.js",
    },
  },
});
const worker = await readFile(path.join(demo, "demo-worker.js"), "utf8");
await writeFile(
  path.join(demo, "demo-worker.js"),
  worker + `\n// Demo data version: ${version}\n`,
);
console.log(`Built ${output}`);
