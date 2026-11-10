import { spawn } from "node:child_process";
import { createWriteStream } from "node:fs";
import { mkdtemp, readdir, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

/**
 * Global setup for recording: starts the built Java server on the test port with a throwaway
 * library in a temporary folder, and adds the repository's example folders, as Getting started
 * does by hand. Nothing touches the developer's own `content/` library. Build the server first
 * (`./java/mvnw -f java/pom.xml -pl server -am package`). Returns the teardown that stops it.
 */
export const API_PORT = 18877;
const api = `http://127.0.0.1:${API_PORT}/api`;
const repository = path.resolve(import.meta.dirname, "../..");
// The library every test starts from.
const folders = ["media-fr", "documents-fr"].map((name) =>
  path.join(repository, "example", name),
);

async function jar() {
  const target = path.join(repository, "java/server/target");
  const found = (await readdir(target).catch(() => [])).find((name) =>
    /^petites-roues-server-.*\.jar$/.test(name),
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
 * Undoes what [requests] (`METHOD /path` keys) saved on the real server: reading and playback
 * progress is forgotten, queued subtitle edits are discarded and added folders are removed, so a
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
  for (const path of changed(
    /^PUT (\/api\/titles\/[^/]+\/(?:comic\/)?progress)$/,
  ))
    await fetch(api + path, { method: "DELETE" });
  for (const title of changed(
    /^(?:PUT|POST|DELETE) \/api\/titles\/([^/]+)\/(?:cues|timings)\b/,
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
  const log = createWriteStream(path.join(folder, "server.log"));
  const server = spawn(
    "java",
    [
      "-jar",
      await jar(),
      `--server.port=${API_PORT}`,
      `--spring.datasource.url=jdbc:sqlite:${path.join(folder, "library.sqlite")}`,
    ],
    { cwd: folder, stdio: ["ignore", "pipe", "pipe"] },
  );
  server.stdout.pipe(log);
  server.stderr.pipe(log);
  try {
    await waitForServer(server);
    for (const localPath of folders) {
      const response = await fetch(`${api}/repositories`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ localPath }),
      });
      if (!response.ok)
        throw Error(`Adding ${localPath} failed: ${await response.text()}`);
    }
  } catch (error) {
    server.kill();
    throw Error(`${error.message} (log: ${path.join(folder, "server.log")})`, {
      cause: error,
    });
  }
  return async () => {
    server.kill();
    await new Promise((resolve) => server.once("exit", resolve));
    await rm(folder, { recursive: true, force: true });
  };
}
