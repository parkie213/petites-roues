import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { createServer } from "../server.mjs";

test("Node serves React routes and streams API requests, downloads and ranges", async (t) => {
  let calls = 0;
  const backend = http.createServer(async (req, res) => {
    calls++;
    let bytes = 0;
    for await (const chunk of req) bytes += chunk.length;
    if (req.url.endsWith("/slow-media")) {
      res.writeHead(200, { "Content-Type": "application/octet-stream" });
      const timer = setInterval(() => res.write(Buffer.alloc(8192)), 5);
      res.on("close", () => clearInterval(timer));
      return;
    }
    if (req.url.endsWith("/media")) {
      res
        .writeHead(206, {
          "Content-Type": "video/mp4",
          "Content-Range": "bytes 2-4/10",
          "Accept-Ranges": "bytes",
        })
        .end(Buffer.from([2, 3, 4]));
      return;
    }
    if (req.url.endsWith("/export")) {
      res
        .writeHead(200, {
          "Content-Type": "application/json",
          "Content-Disposition": 'attachment; filename="example.json"',
        })
        .end('{"cues":[]}');
      return;
    }
    res.writeHead(200, { "Content-Type": "application/json" }).end(
      JSON.stringify({
        bytes,
        origin: req.headers.origin,
        host: req.headers.host,
        range: req.headers.range,
      }),
    );
  });
  await new Promise((resolve) => backend.listen(0, "127.0.0.1", resolve));
  const api = `http://127.0.0.1:${backend.address().port}`;
  const frontend = await createServer({ apiTarget: api });
  await new Promise((resolve) => frontend.listen(0, "127.0.0.1", resolve));
  t.after(() => {
    frontend.closeAllConnections();
    frontend.close();
    backend.closeAllConnections();
    backend.close();
  });
  const origin = `http://127.0.0.1:${frontend.address().port}`;
  const interrupted = await fetch(origin + "/api/titles/example/slow-media");
  const reader = interrupted.body.getReader();
  await reader.read();
  await reader.cancel();
  for (const route of [
    "/",
    "/repositories",
    "/player/example/",
    "/reader/example/",
    "/video/v1%3Aabc/",
    "/notes",
    "/notes/repo-id/pass%C3%A9-compos%C3%A9",
  ]) {
    const response = await fetch(origin + route);
    assert.equal(response.status, 200);
    assert.match(await response.text(), /id="root"/);
  }
  assert.equal((await fetch(origin + "/import")).status, 404);
  assert.equal((await fetch(origin + "/notes/a/b/c")).status, 404);
  assert.equal((await fetch(origin + "/study/example/")).status, 404);
  assert.equal((await fetch(origin + "/package.json")).status, 404);
  const before = calls;
  assert.equal(
    (
      await fetch(origin + "/api/repositories", {
        method: "POST",
        headers: { Origin: "https://example.com" },
        body: "bad",
      })
    ).status,
    403,
  );
  assert.equal(calls, before);
  const response = await fetch(origin + "/api/repositories", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/octet-stream" },
    body: Buffer.alloc(2_000_000, 1),
  });
  const forwarded = await response.json();
  assert.equal(forwarded.bytes, 2_000_000);
  assert.equal(forwarded.origin, api);
  assert.equal(forwarded.host, new URL(api).host);
  const video = await fetch(origin + "/api/titles/example/media", {
    headers: { Range: "bytes=2-4" },
  });
  assert.equal(video.status, 206);
  assert.equal(video.headers.get("Content-Range"), "bytes 2-4/10");
  assert.deepEqual([...new Uint8Array(await video.arrayBuffer())], [2, 3, 4]);
  const download = await fetch(origin + "/api/titles/example/export");
  assert.match(download.headers.get("Content-Disposition"), /attachment/);
  assert.deepEqual(await download.json(), { cues: [] });
});
