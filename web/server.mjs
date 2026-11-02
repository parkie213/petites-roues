import http from "node:http";
import { createReadStream } from "node:fs";
import { readFile, stat } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const directory = path.dirname(fileURLToPath(import.meta.url));
const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
};
const localHosts = new Set(["localhost", "127.0.0.1", "[::1]"]);
const pageRoute = (pathname) =>
  pathname === "/" ||
  pathname === "/repositories" ||
  /^\/(?:player|watch|reader)\/(?:[A-Za-z0-9_-]+\/?)?$/.test(pathname) ||
  /^\/notes(?:\/[^/]+\/[^/]+)?\/?$/.test(pathname);

export async function createServer({
  dev = false,
  apiTarget = process.env.API_TARGET || "http://127.0.0.1:8877",
} = {}) {
  const backend = new URL(apiTarget);
  if (backend.protocol !== "http:" || !localHosts.has(backend.hostname))
    throw Error("API_TARGET must be a local HTTP server");
  let vite;
  const server = http.createServer(async (req, res) => {
    try {
      const host = new URL(`http://${req.headers.host}`);
      if (!localHosts.has(host.hostname)) {
        res.writeHead(403).end("Local requests only");
        return;
      }
      const url = new URL(req.url, host);
      if (!["GET", "HEAD", "OPTIONS"].includes(req.method)) {
        if (
          (req.headers.origin && req.headers.origin !== host.origin) ||
          req.headers["sec-fetch-site"] === "cross-site"
        ) {
          res.writeHead(403).end("Cross-origin writes are not allowed");
          return;
        }
      }
      if (url.pathname.startsWith("/api/")) {
        const headers = { ...req.headers, host: backend.host };
        // Only rewrite Origin after validating the browser's original origin above.
        if (headers.origin) headers.origin = backend.origin;
        const upstream = http.request(
          new URL(url.pathname + url.search, backend),
          { method: req.method, headers },
          (response) => {
            response.on("error", () => res.destroy());
            res.writeHead(response.statusCode, response.headers);
            response.pipe(res);
          },
        );
        upstream.on("error", () => {
          if (!res.headersSent)
            res.writeHead(502, { "Content-Type": "application/json" }).end(
              JSON.stringify({
                error:
                  "Java API is unavailable. Start the Java server on " +
                  backend.origin,
              }),
            );
          else res.destroy();
        });
        req.on("aborted", () => upstream.destroy());
        res.on("close", () => {
          if (!res.writableFinished) upstream.destroy();
        });
        req.pipe(upstream);
        return;
      }
      if (!["GET", "HEAD"].includes(req.method)) {
        res.writeHead(404).end("Not found");
        return;
      }
      const serve = async () => {
        if (pageRoute(url.pathname)) {
          let html = await readFile(
            path.join(directory, dev ? "index.html" : "dist/index.html"),
            "utf8",
          );
          if (vite) html = await vite.transformIndexHtml(url.pathname, html);
          res
            .writeHead(200, {
              "Content-Type": types[".html"],
              "Cache-Control": "no-store",
            })
            .end(req.method === "HEAD" ? undefined : html);
          return;
        }
        if (dev) {
          res.writeHead(404).end("Not found");
          return;
        }
        const root = path.join(directory, "dist");
        const asset = path.resolve(
          root,
          "." + decodeURIComponent(url.pathname),
        );
        if (!asset.startsWith(root + path.sep)) {
          res.writeHead(404).end("Not found");
          return;
        }
        let info;
        try {
          info = await stat(asset);
        } catch {
          res.writeHead(404).end("Not found");
          return;
        }
        if (!info.isFile()) {
          res.writeHead(404).end("Not found");
          return;
        }
        res.writeHead(200, {
          "Content-Type":
            types[path.extname(asset)] || "application/octet-stream",
          "Content-Length": info.size,
          "Cache-Control": url.pathname.startsWith("/assets/")
            ? "public, max-age=31536000, immutable"
            : "no-cache",
        });
        if (req.method === "HEAD") res.end();
        else
          createReadStream(asset)
            .on("error", () => res.destroy())
            .pipe(res);
      };
      if (vite)
        vite.middlewares(req, res, () =>
          serve().catch((error) => {
            console.error(error);
            res.writeHead(500).end("Could not serve the UI");
          }),
        );
      else await serve();
    } catch (error) {
      console.error(error);
      if (!res.headersSent) res.writeHead(500).end("Could not serve the UI");
      else res.destroy();
    }
  });
  if (dev) {
    vite = await (
      await import("vite")
    ).createServer({
      root: directory,
      server: { middlewareMode: true, hmr: { server } },
      appType: "custom",
    });
  }
  server.on("close", () => vite?.close());
  return server;
}

if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const port = Number(process.env.PORT || 8875);
  const server = await createServer({ dev: process.argv.includes("--dev") });
  server.listen(port, "127.0.0.1", () =>
    console.log(`Petites Roues: http://127.0.0.1:${port}/`),
  );
  for (const signal of ["SIGTERM", "SIGINT"])
    process.on(signal, () => {
      server.close();
      server.closeAllConnections();
    });
}
