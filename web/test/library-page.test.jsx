// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { readFileSync } from "node:fs";
import path from "node:path";
import { beforeEach, expect, test, vi } from "vitest";
import { Library } from "../src/library/Library.tsx";
import { folderHref } from "../src/library/navigation.ts";

/**
 * The library page around its views: which view the URL shows, the toolbar's filters, removing a
 * folder, and failures. The API answers with the example library as the e2e journeys recorded it.
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

/**
 * The API: [library] for GET /api/library (null fails it), the recorded posters and usage, no job
 * running, and [remove] answering a folder's removal. Every request's method and URL is kept.
 */
function fakeApi({
  library = answer("GET /api/library"),
  remove = () => Response.json({ removed: true }),
} = {}) {
  const requests = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options = {}) => {
      const method = options.method ?? "GET";
      requests.push(`${method} ${url}`);
      const { pathname } = new URL(url, "http://localhost");
      if (method === "DELETE") return remove();
      if (pathname === "/api/library")
        return library
          ? Response.json(library)
          : Response.json(
              { error: "The index is unavailable" },
              { status: 500 },
            );
      if (pathname === "/api/library/posters")
        return Response.json(answer("GET /api/library/posters"));
      if (pathname === "/api/library/usage")
        return Response.json(answer("GET /api/library/usage"));
      return new Response(null, { status: 204 });
    }),
  );
  return requests;
}

/** Opens the repositories view on the example's first folder, whose options manage it. */
function openFirstFolder() {
  openAt(folderHref(answer("GET /api/library").repositories[0].repository.id));
}

/** Opens the library at [url], as a browser would. */
function openAt(url) {
  history.replaceState(null, "", url);
  render(
    <MantineProvider env="test">
      <Library />
    </MantineProvider>,
  );
}

const view = (name) => screen.getByRole("radio", { name });
const here = () => location.pathname + location.search;

beforeEach(() => {
  localStorage.clear();
});

test("a bare library URL opens the posters, or the view chosen last", async () => {
  fakeApi();
  openAt("/");
  expect(here()).toBe("/?view=posters");
  await screen.findByRole("link", { name: "Peppa Pig" });
  expect(view("Posters").checked).toBe(true);
});

test("the documents view is remembered for the next bare URL", async () => {
  fakeApi();
  localStorage.setItem("library-view", "documents");
  openAt("/");
  expect(here()).toBe("/?view=documents");
  await waitFor(() => expect(view("Documents").checked).toBe(true));
});

test("the repositories view stays on the bare URL", async () => {
  fakeApi();
  localStorage.setItem("library-view", "columns");
  openAt("/");
  expect(here()).toBe("/");
  await waitFor(() => expect(view("Repositories").checked).toBe(true));
});

test("switching views is remembered and Back returns to the last one", async () => {
  fakeApi();
  openAt("/?view=posters");
  await screen.findByRole("link", { name: "Peppa Pig" });
  await userEvent.click(view("Documents"));
  expect(here()).toBe("/?view=documents");
  expect(localStorage.getItem("library-view")).toBe("documents");

  await act(async () => {
    history.back();
    await new Promise((resolve) =>
      window.addEventListener("popstate", resolve, { once: true }),
    );
  });
  expect(here()).toBe("/?view=posters");
  await waitFor(() => expect(view("Posters").checked).toBe(true));
});

test("searching and the media filter narrow the posters", async () => {
  fakeApi();
  openAt("/?view=posters");
  await screen.findByRole("link", { name: "Peppa Pig" });

  await userEvent.type(
    screen.getByRole("textbox", { name: "Search your library" }),
    "lulu",
  );
  expect(screen.queryByRole("link", { name: "Peppa Pig" })).toBeNull();
  expect(screen.getByRole("link", { name: "Lulu", exact: true })).toBeTruthy();

  await userEvent.clear(
    screen.getByRole("textbox", { name: "Search your library" }),
  );
  const media = screen.getByRole("group", { name: "Media type" });
  await userEvent.click(within(media).getByRole("button", { name: "Films" }));
  expect(screen.queryByRole("link", { name: "Lulu", exact: true })).toBeNull();
  expect(screen.getByRole("link", { name: "Peppa Pig" })).toBeTruthy();
});

test("with several languages, the one chosen narrows the posters and is remembered", async () => {
  const library = answer("GET /api/library");
  const [first] = library.repositories.find((r) => r.entries.length).entries;
  library.repositories[0].entries.push({
    ...first,
    id: "german",
    sourceLanguage: "de",
  });
  const requests = fakeApi({ library });
  openAt("/?view=posters");
  const languages = await screen.findByRole("group", { name: "Language" });
  await userEvent.click(within(languages).getByText("Français"));

  expect(localStorage.getItem("library-language")).toBe("fr");
  await waitFor(() =>
    expect(requests).toContain("GET /api/library/posters?language=fr"),
  );
});

test("removing a folder asks first, then removes it and says the files stay", async () => {
  const requests = fakeApi();
  openFirstFolder();
  const [repository] = answer("GET /api/library").repositories;
  const { id, name } = repository.repository;

  await userEvent.click(
    await screen.findByRole("button", { name: `Options for ${name}` }),
  );
  await userEvent.click(
    await screen.findByRole("menuitem", { name: /Remove from library/ }),
  );
  const dialog = await screen.findByRole("dialog", { name: "Remove folder?" });
  await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog", { name: "Remove folder?" })).toBeNull(),
  );
  expect(requests.filter((r) => r.startsWith("DELETE"))).toEqual([]);

  await userEvent.click(
    screen.getByRole("button", { name: `Options for ${name}` }),
  );
  await userEvent.click(
    await screen.findByRole("menuitem", { name: /Remove from library/ }),
  );
  await userEvent.click(
    within(
      await screen.findByRole("dialog", { name: "Remove folder?" }),
    ).getByRole("button", { name: "Remove from library" }),
  );
  await screen.findByText("Folder removed. Files were left untouched.");
  expect(requests).toContain(`DELETE /api/repositories/${id}`);
  // The library is loaded again to show it without the folder.
  expect(requests.filter((r) => r === "GET /api/library")).toHaveLength(2);
});

test("a removal that fails says why and keeps the dialog open", async () => {
  fakeApi({
    remove: () =>
      Response.json({ error: "The folder is in use" }, { status: 409 }),
  });
  openFirstFolder();
  const [repository] = answer("GET /api/library").repositories;
  await userEvent.click(
    await screen.findByRole("button", {
      name: `Options for ${repository.repository.name}`,
    }),
  );
  await userEvent.click(
    await screen.findByRole("menuitem", { name: /Remove from library/ }),
  );
  await userEvent.click(
    within(
      await screen.findByRole("dialog", { name: "Remove folder?" }),
    ).getByRole("button", { name: "Remove from library" }),
  );
  expect((await screen.findByRole("alert")).textContent).toContain(
    "The folder is in use",
  );
  expect(screen.getByRole("dialog", { name: "Remove folder?" })).toBeTruthy();
});

test("a library that can't load says why", async () => {
  fakeApi({ library: null });
  openAt("/?view=posters");
  const alert = await screen.findByRole("alert");
  expect(alert.textContent).toContain("Something went wrong");
  expect(alert.textContent).toContain("The index is unavailable");
  expect(screen.queryByText("Loading library…")).toBeNull();
});

test("an empty library offers to add a folder, without views to switch", async () => {
  fakeApi({ library: { repositories: [] } });
  openAt("/");
  await screen.findByText("There's nothing here");
  expect(screen.getByRole("button", { name: "Add folder" })).toBeTruthy();
  expect(screen.queryByRole("radio", { name: "Documents" })).toBeNull();
});
