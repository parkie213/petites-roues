import { EventEmitter } from "node:events";
import { describe, expect, it, vi } from "vitest";
import { checkRequests, expectWrite, readJson } from "../e2e/requests.mjs";

const path = "/api/titles/example/comic/progress";
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

function exchange({
  method = "PUT",
  body = { pageId: "cover" },
  status = 200,
  finished = Promise.resolve(null),
  responseBody = body,
  url = path,
  resourceType = "fetch",
} = {}) {
  const request = {
    url: () => `http://localhost${url}`,
    method: () => method,
    resourceType: () => resourceType,
    postDataJSON: () => body,
    failure: () => ({ errorText: "net::ERR_ABORTED" }),
    response: async () => response,
  };
  const response = {
    request: () => request,
    ok: () => status >= 200 && status < 300,
    status: () => status,
    finished: () => finished,
    json: async () => responseBody,
  };
  return { request, response };
}

class Page extends EventEmitter {
  waitForRequest(predicate) {
    return new Promise((resolve, reject) => {
      const listener = (request) => {
        if (!predicate(request)) return;
        clearTimeout(timer);
        this.off("request", listener);
        resolve(request);
      };
      const timer = setTimeout(() => {
        this.off("request", listener);
        reject(Error("Expected request was never made"));
      }, 50);
      this.on("request", listener);
    });
  }
  send({ request, response }) {
    this.emit("request", request);
    this.emit("response", response);
  }
}

const target = { method: "PUT", path, body: { pageId: "page-1" } };

describe("journey write assertions", () => {
  it("requires a new request with the intended method and payload, not an old response", async () => {
    const page = new Page();
    const old = exchange({ body: { pageId: "page-1" } });
    page.emit("request", old.request);
    const result = await expectWrite(page, target, async () => {
      page.emit("response", old.response);
      page.send(exchange({ method: "POST", body: { pageId: "page-1" } }));
      page.send(exchange({ body: { pageId: "cover" } }));
      page.send(exchange({ body: { pageId: "page-1" } }));
    });
    expect(result).toEqual({ pageId: "page-1" });
  });

  it("fails when optimistic UI never issues the expected save", async () => {
    await expect(
      expectWrite(new Page(), target, async () => {}),
    ).rejects.toThrow("Expected request was never made");
  });

  it("waits for the response body, not just successful headers", async () => {
    const page = new Page();
    const body = deferred();
    let completed = false;
    const assertion = expectWrite(page, target, async () => {
      page.send(
        exchange({ body: { pageId: "page-1" }, finished: body.promise }),
      );
    }).then(() => {
      completed = true;
    });
    await Promise.resolve();
    await Promise.resolve();
    expect(completed).toBe(false);
    body.resolve(null);
    await assertion;
    expect(completed).toBe(true);
  });

  it("rejects an unsuccessful save instead of trusting the visible state", async () => {
    const page = new Page();
    await expect(
      expectWrite(page, target, async () => {
        page.send(exchange({ body: { pageId: "page-1" }, status: 500 }));
      }),
    ).rejects.toThrow(path);
  });
});

describe("shared request guard", () => {
  it("drains successful writes and removes its listeners", async () => {
    const page = new Page();
    const body = deferred();
    const assertion = checkRequests(page, async () => {
      page.send(exchange({ finished: body.promise }));
      body.resolve(null);
    });
    await assertion;
    expect(page.eventNames()).toEqual([]);
  });

  it.each(["GET", "PUT", "POST", "DELETE"])(
    "rejects HTTP errors for %s",
    async (method) => {
      const page = new Page();
      await expect(
        checkRequests(page, async () => {
          page.send(exchange({ method, status: 503 }));
        }),
      ).rejects.toThrow("HTTP 503");
      expect(page.eventNames()).toEqual([]);
    },
  );

  it("rejects an aborted write even if the application handles the error", async () => {
    const page = new Page();
    const { request } = exchange();
    await expect(
      checkRequests(page, async () => {
        page.emit("request", request);
        page.emit("requestfailed", request);
      }),
    ).rejects.toThrow("ERR_ABORTED");
  });

  it("rejects an abandoned response body after successful headers", async () => {
    const page = new Page();
    await expect(
      checkRequests(page, async () => {
        page.send(
          exchange({ finished: Promise.resolve(Error("navigated away")) }),
        );
      }),
    ).rejects.toThrow("navigated away");
  });

  it.each(["GET", "PUT"])(
    "rejects a %s that never receives a response",
    async (method) => {
      const page = new Page();
      await expect(
        checkRequests(
          page,
          async () => {
            page.emit("request", exchange({ method }).request);
          },
          { timeout: 50 },
        ),
      ).rejects.toThrow("API requests still pending");
      expect(page.eventNames()).toEqual([]);
    },
  );

  it("does not wait for deliberately cancelled read streams", async () => {
    const page = new Page();
    await checkRequests(page, async () => {
      const { request } = exchange({
        method: "GET",
        url: "/api/jobs/example/events",
        resourceType: "eventsource",
      });
      page.emit("request", request);
      page.emit("requestfailed", request);
    });
  });

  it("rejects failed JSON reads even if the application handles the error", async () => {
    const page = new Page();
    const { request } = exchange({ method: "GET" });
    await expect(
      checkRequests(page, async () => {
        page.emit("request", request);
        page.emit("requestfailed", request);
      }),
    ).rejects.toThrow("ERR_ABORTED");
  });

  it("preserves the original journey failure and cleans up", async () => {
    const page = new Page();
    await expect(
      checkRequests(page, async () => {
        throw Error("Wrong saved page");
      }),
    ).rejects.toThrow("Wrong saved page");
    expect(page.eventNames()).toEqual([]);
  });
});

describe("persisted-state reads", () => {
  it("fetches independent snapshots through the browser without using cached state", async () => {
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ pageId: "page-1" }),
    });
    vi.stubGlobal("fetch", fetch);
    const page = { evaluate: (fn, path) => fn(path) };
    expect(await readJson(page, path)).toEqual({ pageId: "page-1" });
    await readJson(page, path);
    expect(fetch.mock.calls).toEqual([
      [path + "?journeyCheck=1", { cache: "no-store" }],
      [path + "?journeyCheck=2", { cache: "no-store" }],
    ]);
  });

  it("rejects a failed persisted-state GET", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: false, status: 503 }),
    );
    await expect(
      readJson({ evaluate: (fn, path) => fn(path) }, path),
    ).rejects.toThrow("503");
  });
});
