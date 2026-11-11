// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { LibraryBrowser } from "../src/library/LibraryBrowser.tsx";
import { folderHref } from "../src/library/navigation.ts";

/** A small indexed repository with documents and media at their own paths. */
function repository() {
  return {
    id: "repo",
    name: "Examples",
    localPath: "/content/examples",
    entries: [
      {
        id: "film",
        title: "A film",
        type: "video",
        relativePath: "docs/document.json",
        documentAvailable: true,
        videoAvailable: true,
        mediaAvailable: true,
      },
      {
        id: "notes",
        title: "Study only",
        type: "video",
        relativePath: "docs/notes.json",
        documentAvailable: true,
        videoAvailable: false,
        mediaAvailable: false,
      },
      {
        id: "missing",
        title: "Missing",
        type: "comic",
        relativePath: "docs/missing.json",
        documentAvailable: false,
        mediaAvailable: false,
      },
    ],
    media: [
      {
        repositoryId: "repo",
        relativePath: "media/Film.mp4",
        kind: "video",
        fingerprint: "video-ref",
        titleId: "film",
        linkedByHand: true,
      },
      {
        repositoryId: "repo",
        relativePath: "media/Bare.mp4",
        kind: "video",
        fingerprint: "bare-ref",
      },
      {
        repositoryId: "repo",
        relativePath: "media/Book.cbz",
        kind: "comic",
        fingerprint: "comic-ref",
      },
    ],
  };
}

function open({
  parts = [],
  repository: selected = "repo",
  busy = false,
  repos = [repository()],
} = {}) {
  const callbacks = {
    navigate: vi.fn(),
    refresh: vi.fn(),
    onRemove: vi.fn(),
    onChanged: vi.fn(),
  };
  render(
    <MantineProvider env="test">
      <LibraryBrowser
        data={{ repos }}
        selection={{ repository: selected, parts }}
        busy={busy}
        {...callbacks}
      />
    </MantineProvider>,
  );
  return callbacks;
}

const column = (name) => within(screen.getByRole("navigation", { name }));

test("repositories describe all descendants and selecting one navigates without reloading", async () => {
  const { navigate } = open({ repository: null });
  expect(
    screen.getByText("Choose a folder to explore your titles."),
  ).toBeTruthy();
  const link = column("Repositories").getByRole("link", { name: /Examples/ });
  expect(link.textContent).toContain("2 videos · 1 comic · 3 study documents");
  expect(link.getAttribute("href")).toBe(folderHref("repo"));
  await userEvent.click(link);
  expect(navigate).toHaveBeenCalledWith("repo", []);
});

test("stale folder paths stop at the last existing folder in columns and breadcrumbs", async () => {
  const { navigate } = open({ parts: ["docs", "gone"] });
  expect(screen.queryByRole("navigation", { name: "gone" })).toBeNull();
  expect(column("docs").getByText("A film")).toBeTruthy();
  const crumb = screen.getByRole("link", { name: "docs", exact: true });
  expect(crumb.getAttribute("href")).toBe(folderHref("repo", ["docs"]));
  await userEvent.click(crumb);
  expect(navigate).toHaveBeenCalledWith("repo", ["docs"]);
});

test("folder arrows navigate and modified clicks retain normal link behavior", () => {
  const { navigate } = open();
  const folder = column("Examples").getByRole("link", { name: /docs/ });
  for (const modifier of ["ctrlKey", "metaKey", "shiftKey", "altKey"]) {
    // Observe the component's decision, then stop jsdom from attempting real navigation.
    const observe = vi.fn((event) => {
      expect(event.defaultPrevented).toBe(false);
      event.preventDefault();
    });
    document.addEventListener("click", observe, { once: true });
    fireEvent.click(folder, { [modifier]: true });
    expect(observe).toHaveBeenCalledOnce();
  }
  expect(navigate).not.toHaveBeenCalled();
  fireEvent.keyDown(folder, { key: "ArrowRight" });
  expect(navigate).toHaveBeenLastCalledWith("repo", ["docs"]);
  fireEvent.keyDown(folder, { key: "ArrowLeft" });
  expect(navigate).toHaveBeenLastCalledWith("repo", []);
});

test("documents offer playback, editing or notes according to availability", async () => {
  const { onChanged } = open({ parts: ["docs"] });
  const docs = column("docs");
  expect(
    docs.getByRole("link", { name: "Play A film" }).getAttribute("href"),
  ).toBe("/watch/film/");
  expect(
    docs.getByRole("link", { name: "Edit A film" }).getAttribute("href"),
  ).toBe("/player/film/");
  expect(
    docs.getByRole("link", { name: "Open Study only" }).getAttribute("href"),
  ).toBe("/player/notes/");
  expect(docs.getByText("Document missing — refresh repository")).toBeTruthy();
  await userEvent.click(docs.getByRole("button", { name: "Locate media…" }));
  const dialog = await screen.findByRole("dialog", { name: "Locate media" });
  expect(within(dialog).getByText("Study only")).toBeTruthy();
  expect(within(dialog).getByText("media/Bare.mp4")).toBeTruthy();
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(null, { status: 204 })),
  );
  await userEvent.click(
    within(dialog).getByRole("button", { name: /^media\/Bare\.mp4/ }),
  );
  await waitFor(() => expect(onChanged).toHaveBeenCalledWith());
  expect(fetch).toHaveBeenCalledWith(
    "/api/library/media-links",
    expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({
        repositoryId: "repo",
        relativePath: "media/Bare.mp4",
        titleId: "notes",
      }),
    }),
  );
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
});

test("media open their linked title or bare video, and an unlinked comic offers linking", async () => {
  open({ parts: ["media"] });
  const media = column("media");
  expect(
    media
      .getAllByRole("link", { name: "Play" })
      .map((a) => a.getAttribute("href")),
  ).toEqual(["/video/bare-ref/", "/watch/film/"]);
  expect(media.queryByRole("link", { name: "Read" })).toBeNull();
  const book = media.getByText("Book.cbz").closest(".mantine-Paper-root");
  await userEvent.click(
    within(book).getByRole("button", { name: "Link to study document…" }),
  );
  const dialog = await screen.findByRole("dialog", {
    name: "Link to study document",
  });
  expect(within(dialog).getByText("Book.cbz")).toBeTruthy();
  expect(
    within(dialog).getByText(/No study documents of this kind/),
  ).toBeTruthy();
});

test.each([true, false])(
  "unlink reports the result to the library (success: %s)",
  async (success) => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        success
          ? new Response(null, { status: 204 })
          : Response.json({ error: "Link is in use" }, { status: 409 }),
      ),
    );
    const { onChanged } = open({ parts: ["media"] });
    await userEvent.click(
      column("media").getByRole("button", { name: "Unlink" }),
    );
    expect(fetch).toHaveBeenCalledWith(
      "/api/library/media-links?repository=repo&path=media%2FFilm.mp4",
      { method: "DELETE" },
    );
    await waitFor(() => expect(onChanged).toHaveBeenCalledOnce());
    if (success) expect(onChanged).toHaveBeenCalledWith();
    else expect(onChanged.mock.calls[0][0].message).toBe("Link is in use");
  },
);

test.each([true, false])(
  "repository menu respects busy state (busy: %s)",
  async (busy) => {
    const { refresh, onRemove } = open({ busy });
    await userEvent.click(
      screen.getByRole("button", { name: "Options for Examples" }),
    );
    const refreshItem = await screen.findByRole("menuitem", {
      name: "Refresh folder",
    });
    const removeItem = screen.getByRole("menuitem", {
      name: "Remove from library…",
    });
    expect(screen.getByText("/content/examples")).toBeTruthy();
    await userEvent.click(refreshItem);
    if (!busy) {
      expect(refresh).toHaveBeenCalledWith("repo");
      await userEvent.click(
        screen.getByRole("button", { name: "Options for Examples" }),
      );
    } else expect(refresh).not.toHaveBeenCalled();
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Remove from library…" }),
    );
    if (busy) {
      expect(removeItem.getAttribute("data-disabled")).not.toBeNull();
      expect(onRemove).not.toHaveBeenCalled();
    } else expect(onRemove).toHaveBeenCalledWith("repo");
  },
);

test("an indexed repository with no content offers refresh guidance", () => {
  open({ repos: [{ ...repository(), entries: [], media: [] }] });
  expect(screen.getByText(/Nothing found. Add videos/)).toBeTruthy();
  expect(column("Repositories").getByText("Empty")).toBeTruthy();
});
