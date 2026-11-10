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

/** Removes the folder at [localPath] from the real server's library, undoing a test's Add. */
export async function removeFolder(localPath) {
  const folders = await (await fetch(`${api}/repositories`)).json();
  for (const { id } of folders.filter(
    (folder) => folder.localPath === localPath,
  ))
    await fetch(`${api}/repositories/${encodeURIComponent(id)}`, {
      method: "DELETE",
    });
}

/**
 * Forgets the reading and playback progress saved by any of [requests] (`METHOD /path` keys), so
 * a test leaves the library as it found it.
 */
export async function forgetProgress(requests) {
  const saves = requests.filter((request) =>
    /^PUT \/api\/titles\/[^/]+\/(comic\/)?progress$/.test(request),
  );
  for (const save of new Set(saves))
    await fetch(`http://127.0.0.1:${API_PORT}${save.split(" ")[1]}`, {
      method: "DELETE",
    });
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
    for (const name of ["media-fr", "documents-fr"]) {
      const response = await fetch(`${api}/repositories`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          localPath: path.join(repository, "example", name),
        }),
      });
      if (!response.ok)
        throw Error(`Adding ${name} failed: ${await response.text()}`);
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
