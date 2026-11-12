import { isDeepStrictEqual } from "node:util";
import { expect } from "@playwright/test";

export const requestKey = (request) => {
  const url = new URL(request.url());
  return `${request.method()} ${url.pathname}${url.search}`;
};

const isApi = (request) => new URL(request.url()).pathname.startsWith("/api/");
const isWrite = (request) => !["GET", "HEAD"].includes(request.method());
// The browser intentionally cancels superseded images/ranges and closes finished job streams.
const mayCancel = (request) =>
  !isWrite(request) &&
  ["image", "media", "eventsource"].includes(request.resourceType());
const finite = (request) => !mayCancel(request);

/**
 * Observe the request before acting, so an earlier in-flight save cannot satisfy this assertion.
 * Check the method, path and payload, await the whole response, and return its JSON for domain
 * assertions. A body predicate can describe a batch (such as joined subtitle boundaries).
 */
export async function expectWrite(page, { method, path, body }, action) {
  const requested = page.waitForRequest((request) => {
    if (request.method() !== method || new URL(request.url()).pathname !== path)
      return false;
    const actual = request.postDataJSON();
    return typeof body === "function"
      ? body(actual)
      : Object.entries(body ?? {}).every(([name, value]) =>
          isDeepStrictEqual(actual?.[name], value),
        );
  });
  const [request] = await Promise.all([
    requested,
    Promise.resolve().then(action),
  ]);
  const response = await request.response();
  expect(response, `${requestKey(request)} has no response`).not.toBeNull();
  expect(response.ok(), requestKey(request)).toBe(true);
  expect(await response.finished(), requestKey(request)).toBeNull();
  return response.status() === 204 ? undefined : response.json();
}

const checkpoints = new WeakMap();

/**
 * Read persisted state through the browser, so recording and replay observe this independent GET
 * too. Call only after the save completes; session storage and optimistic UI cannot satisfy it.
 */
export async function readJson(page, path) {
  const checkpoint = (checkpoints.get(page) ?? 0) + 1;
  checkpoints.set(page, checkpoint);
  // Give each persisted-state assertion its own recording. Identical initial GETs may otherwise
  // be deduplicated and consume a later snapshot on replay.
  const separator = path.includes("?") ? "&" : "?";
  path += `${separator}journeyCheck=${checkpoint}`;
  return page.evaluate(async (path) => {
    const response = await fetch(path, { cache: "no-store" });
    if (!response.ok) throw Error(`GET ${path}: ${response.status}`);
    return response.json();
  }, path);
}

/**
 * Require complete, successful finite API requests in every journey. Images, media ranges and
 * event streams can be intentionally aborted, but their HTTP/transport errors still fail. Their
 * domain outcomes are asserted by the journey. Writes have no cancellation exception.
 * Listeners stay installed through fixture recording/draining and are removed even on failure.
 */
export async function checkRequests(page, run, { timeout = 5000 } = {}) {
  const pending = new Set();
  const reading = new Set();
  const failures = [];
  const requested = (request) => {
    if (isApi(request) && finite(request)) pending.add(request);
  };
  const failed = (request) => {
    if (!isApi(request)) return;
    const error = request.failure()?.errorText ?? "Request failed";
    if (mayCancel(request) && /ERR_ABORTED|NS_BINDING_ABORTED/.test(error))
      return;
    failures.push(`${requestKey(request)}: ${error}`);
    if (!reading.has(request)) pending.delete(request);
  };
  const responded = async (response) => {
    const request = response.request();
    if (!isApi(request)) return;
    const status = response.status();
    if (status >= 400 || (isWrite(request) && !response.ok()))
      failures.push(`${requestKey(request)}: HTTP ${status}`);
    if (!finite(request)) return;
    reading.add(request);
    try {
      const error = await response.finished();
      if (error) failures.push(`${requestKey(request)}: ${error}`);
    } catch (error) {
      failures.push(`${requestKey(request)}: ${error.message}`);
    } finally {
      reading.delete(request);
      pending.delete(request);
    }
  };
  page.on("request", requested);
  page.on("requestfailed", failed);
  page.on("response", responded);
  try {
    await run(page);
    await expect
      .poll(() => [...pending].map(requestKey), {
        message: "API requests still pending",
        timeout,
      })
      .toEqual([]);
    expect(failures, "API requests must succeed").toEqual([]);
  } finally {
    page.off("request", requested);
    page.off("requestfailed", failed);
    page.off("response", responded);
  }
}
