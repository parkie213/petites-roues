// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, expect, test, vi } from "vitest";
import { Library } from "../src/library/Library.tsx";

/**
 * Resetting the library from the "..." menu: the item waits while edits are pending, a dialog
 * says what is lost and kept, and only confirming starts the reset job.
 */

const recorded = JSON.parse(
  readFileSync(
    path.join(
      import.meta.dirname,
      "../e2e/fixtures/library/the-library-shelves-the-example-s-comic-and-series.json",
    ),
    "utf8",
  ),
);
const answer = (request) =>
  structuredClone(recorded.find((item) => item.request === request).json);

/** The API with [pending] titles awaiting review; every request's method, URL and body is kept. */
function fakeApi(pending = []) {
  const requests = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options = {}) => {
      const method = options.method ?? "GET";
      requests.push(
        `${method} ${url}${options.body ? ` ${options.body}` : ""}`,
      );
      const { pathname } = new URL(url, "http://localhost");
      if (method === "POST" && pathname === "/api/jobs")
        return Response.json(
          { id: "job", kind: "reset", state: "succeeded", progress: null },
          { status: 202 },
        );
      if (pathname === "/api/review/pending") return Response.json(pending);
      if (pathname === "/api/library")
        return Response.json(answer("GET /api/library"));
      if (pathname === "/api/library/posters")
        return Response.json(answer("GET /api/library/posters"));
      if (pathname === "/api/library/usage")
        return Response.json(answer("GET /api/library/usage"));
      return new Response(null, { status: 204 });
    }),
  );
  return requests;
}

function openLibrary() {
  history.replaceState(null, "", "/?view=posters");
  const target = document.body.appendChild(document.createElement("div"));
  render(
    <MantineProvider env="test">
      <Library actionsTarget={target} />
    </MantineProvider>,
  );
}

const openMenu = () =>
  userEvent.click(screen.getByRole("button", { name: "More library actions" }));
const resetJobs = (requests) =>
  requests.filter((request) => request.startsWith("POST /api/jobs"));

beforeEach(() => {
  localStorage.clear();
  document.body.innerHTML = "";
});

test("the menu holds Reset library, which asks before doing anything", async () => {
  const requests = fakeApi();
  openLibrary();
  await screen.findByRole("link", { name: "Peppa Pig" });
  await openMenu();
  await userEvent.click(
    await screen.findByRole("menuitem", { name: "Reset library" }),
  );
  const dialog = await screen.findByRole("dialog", { name: "Reset library?" });
  expect(dialog.textContent).toContain("Reading and playback positions");
  expect(dialog.textContent).toContain("Phone pairings");
  expect(dialog.textContent).toContain("Review history");
  expect(dialog.textContent).toContain("The registered folders");
  expect(dialog.textContent).toContain("Settings");
  expect(resetJobs(requests)).toEqual([]);

  await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  expect(resetJobs(requests)).toEqual([]);
});

test("confirming starts the reset job", async () => {
  const requests = fakeApi();
  openLibrary();
  await screen.findByRole("link", { name: "Peppa Pig" });
  await openMenu();
  await userEvent.click(
    await screen.findByRole("menuitem", { name: "Reset library" }),
  );
  const dialog = await screen.findByRole("dialog", { name: "Reset library?" });
  await userEvent.click(
    Array.from(dialog.querySelectorAll("button")).find(
      (button) => button.textContent === "Reset library",
    ),
  );
  await waitFor(() =>
    expect(resetJobs(requests)).toEqual(['POST /api/jobs {"kind":"reset"}']),
  );
  expect(await screen.findByText("Library reset and rebuilt.")).toBeTruthy();
});

test("the item is disabled and explained while edits are pending", async () => {
  const requests = fakeApi([{ titleId: "t", title: "T", pending: 2 }]);
  openLibrary();
  await screen.findByRole("link", { name: "Peppa Pig" });
  await openMenu();
  expect(
    await screen.findByText(
      "Edits are pending: process or discard them first.",
    ),
  ).toBeTruthy();
  const item = screen.getByRole("menuitem", { name: "Reset library" });
  expect(item.hasAttribute("disabled")).toBe(true);
  await userEvent.click(item);
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(requests).toContain("GET /api/review/pending");
  expect(resetJobs(requests)).toEqual([]);
});
