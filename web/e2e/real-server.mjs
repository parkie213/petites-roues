import { startVoiceProvider } from "./voice-provider.mjs";
import { spawn } from "node:child_process";
import { createWriteStream } from "node:fs";
import { mkdtemp, readdir, rm, mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

/**
 * Global setup for recording: starts the built Java server on the test port with a throwaway
 * library in a temporary folder, and adds the repository's example folders, as Getting started
 * does by hand. Nothing touches the developer's own `content/` library. Build the server first
 * (`./java/mvnw -f java/pom.xml -pl desktop-server -am package`). Returns the teardown that stops it.
 */
export const API_PORT = 18877;
const api = `http://127.0.0.1:${API_PORT}/api`;
const repository = path.resolve(import.meta.dirname, "../..");
// The library every test starts from.
const folders = ["media-fr", "documents-fr"].map((name) =>
  path.join(repository, "example", name),
);

async function jar() {
  const target = path.join(repository, "java/desktop-server/target");
  const found = (await readdir(target).catch(() => [])).find((name) =>
    /^desktop-server-.*\.jar$/.test(name),
  );
  if (!found)
    throw Error(`No server jar in ${target}: build the Java server first`);
  return path.join(target, found);
}

async function waitForServer(server) {
  for (let tries = 0; tries < 120; tries++) {
    if (server.exitCode !== null)
      throw Error("The Java server stopped; see its log");
    try {
      if ((await fetch(`${api}/repositories`)).ok) return;
    } catch {
      /* not listening yet */
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw Error("The Java server did not start within a minute");
}

/**
 * Forgets every title's reading and playback progress, whichever test saved it. A player saves its
 * position as it unloads, and that save can land after its own test was undone, so each recording
 * also starts with this.
 */
export async function forgetProgress() {
  const library = await (await fetch(`${api}/library`)).json();
  for (const entry of library.repositories.flatMap((repo) => repo.entries))
    if (entry.progress != null)
      await fetch(
        `${api}/titles/${encodeURIComponent(entry.id)}/` + "progress",
        { method: "DELETE" },
      );
}

/**
 * Undoes what [requests] (`METHOD /path` keys) saved on the real server: reading and playback
 * progress is forgotten, queued subtitle/comic edits are discarded and added folders are removed, so a
 * test leaves the library as it found it.
 */
export async function forget(requests) {
  const api = `http://127.0.0.1:${API_PORT}`;
  for (const { id, localPath } of await (
    await fetch(`${api}/api/repositories`)
  ).json())
    if (!folders.includes(localPath))
      await fetch(`${api}/api/repositories/${encodeURIComponent(id)}`, {
        method: "DELETE",
      });
  const changed = (pattern) =>
    new Set(
      requests.flatMap((request) => pattern.exec(request)?.slice(1) ?? []),
    );
  for (const language of changed(/^PUT \/api\/speech\/voices\/([^/]+)$/))
    await fetch(`${api}/api/speech/voices/${language}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ voice: null }),
    });
  for (const id of changed(
    /^POST \/api\/speech\/voices\/catalogue\/([^/]+)\/install$/,
  )) {
    const speechDirectory = process.env.PETITESROUES_E2E_VOICES;
    if (!speechDirectory || id !== "fr_FR-comparison-medium")
      throw Error("Unexpected voice fixture installation");
    for (const suffix of [".onnx", ".onnx.json", ".MODEL_CARD"])
      await rm(path.join(speechDirectory, id + suffix), { force: true });
  }
  await forgetProgress();
  for (const title of changed(
    /^(?:PUT|POST|DELETE) \/api\/titles\/([^/]+)\/(?:commands)\b/,
  )) {
    const queue = `${api}/api/titles/${title}/review-queue`;
    const rows = await (await fetch(queue)).json();
    await fetch(`${queue}/discard`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(rows.map((row) => row.sequence)),
    });
  }
}

export default async function startRealServer() {
  const folder = await mkdtemp(path.join(os.tmpdir(), "petites-roues-e2e-"));
  // Listing/preferences need model/configuration pairs, not native synthesis or downloaded models.
  const provider = await startVoiceProvider(folder);
  const voices = path.join(folder, "voices");
  process.env.PETITESROUES_E2E_VOICES = voices;
  await mkdir(voices);
  for (const voice of ["fr_FR-a.onnx", "fr_FR-b.onnx"])
    for (const suffix of ["", ".json"])
      await writeFile(path.join(voices, voice + suffix), suffix ? "{}" : "");
  const log = createWriteStream(path.join(folder, "server.log"));
  const server = spawn(
    "java",
    [
      "-jar",
      await jar(),
      `--server.port=${API_PORT}`,
      `--petitesroues.speech.voices=${voices}`,
      `--petitesroues.speech.catalogue=${provider.url}voices.json`,
      `--petitesroues.speech.repository=${provider.url}`,
      `--petitesroues.speech.samples=${provider.url}`,
      `--spring.datasource.url=jdbc:sqlite:${path.join(folder, "library.sqlite")}`,
    ],
    { cwd: folder, stdio: ["ignore", "pipe", "pipe"] },
  );
  server.stdout.pipe(log);
  server.stderr.pipe(log);
  try {
    await waitForServer(server);
    for (const localPath of folders) {
      const response = await fetch(`${api}/jobs`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ kind: "add", localPath }),
      });
      if (!response.ok)
        throw Error(`Adding ${localPath} failed: ${await response.text()}`);
      const started = await response.json();
      let job = started;
      const deadline = Date.now() + 30000;
      while (job.state === "running" && Date.now() < deadline) {
        await new Promise((resolve) => setTimeout(resolve, 50));
        job = await (
          await fetch(`${api}/jobs/${encodeURIComponent(started.id)}`)
        ).json();
      }
      if (job.state !== "succeeded")
        throw Error(job.error || "Add job did not finish");
      const saved = await (await fetch(`${api}/repositories`)).json();
      if (!saved.some((repository) => repository.localPath === localPath))
        throw Error("Add was not persisted");
    }
  } catch (error) {
    server.kill();
    await provider.close();
    throw Error(`${error.message} (log: ${path.join(folder, "server.log")})`, {
      cause: error,
    });
  }
  return async () => {
    server.kill();
    await new Promise((resolve) => server.once("exit", resolve));
    await provider.close();
    await rm(folder, { recursive: true, force: true });
  };
}
