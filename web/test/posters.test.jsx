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
import { Posters } from "../src/library/Posters.tsx";

function entry(overrides = {}) {
  return {
    id: "film",
    type: "video",
    repositoryId: "repo",
    documentAvailable: true,
    videoAvailable: true,
    mediaAvailable: true,
    progress: 0.4,
    ...overrides,
  };
}
function slot(item = entry()) {
  return {
    key: item.id,
    label: item.id,
    entry: item,
    copies: [item],
    art: null,
  };
}
function group(overrides = {}) {
  return {
    key: "series",
    name: "A series",
    type: "video",
    language: "fr",
    single: false,
    slots: [slot()],
    seasons: [],
    count: "1 episode",
    ...overrides,
  };
}
function open({
  groups = [group()],
  selection = {},
  response,
  ...overrides
} = {}) {
  const posters = {
    shelves: [{ key: "films", label: "Films", groups }],
    folders: ["repo", "other"],
    multilingual: true,
  };
  const fetcher = vi.fn(async () =>
    response ? response() : Response.json(posters),
  );
  vi.stubGlobal("fetch", fetcher);
  const props = {
    data: {
      repos: [
        { id: "repo", name: "Examples", entries: [], media: [] },
        { id: "other", name: "Other", entries: [], media: [] },
      ],
    },
    language: null,
    selection: { repository: null, group: null, season: null, ...selection },
    go: vi.fn(),
    onChanged: vi.fn(),
    search: "",
    mediaFilter: "all",
    attentionOnly: false,
    ...overrides,
  };
  const view = render(
    <MantineProvider env="test">
      <Posters {...props} />
    </MantineProvider>,
  );
  return {
    ...props,
    fetcher,
    rerender: (changes) =>
      view.rerender(
        <MantineProvider env="test">
          <Posters {...props} {...changes} />
        </MantineProvider>,
      ),
  };
}

test("series and folder clicks navigate; modified series clicks keep native behavior", async () => {
  const { go } = open();
  const link = await screen.findByRole("link", { name: "A series" });
  const observe = (event) => {
    expect(event.defaultPrevented).toBe(false);
    event.preventDefault();
  };
  document.addEventListener("click", observe, { once: true });
  fireEvent.click(link, { ctrlKey: true });
  expect(go).not.toHaveBeenCalled();
  await userEvent.click(link);
  expect(go).toHaveBeenLastCalledWith("/?view=posters&group=series");
  await userEvent.click(screen.getByLabelText("Examples"));
  expect(go).toHaveBeenLastCalledWith("/?view=posters&repository=repo");
});

test("series defaults to the first season and switches seasons and back through URLs", async () => {
  const { go, rerender } = open({
    selection: { group: "series", season: 99 },
    groups: [
      group({
        seasons: [1, 2],
        slots: [
          slot(entry({ id: "First", season: 1 })),
          slot(entry({ id: "Second", season: 2 })),
        ],
      }),
    ],
  });
  expect(await screen.findByRole("link", { name: "First" })).toBeTruthy();
  expect(screen.queryByRole("link", { name: "Second" })).toBeNull();
  await userEvent.click(screen.getByLabelText("Season 2"));
  expect(go).toHaveBeenLastCalledWith("/?view=posters&group=series&season=2");
  rerender({ selection: { repository: null, group: "series", season: 2 } });
  expect(await screen.findByRole("link", { name: "Second" })).toBeTruthy();
  await userEvent.click(screen.getByRole("link", { name: "Library" }));
  expect(go).toHaveBeenLastCalledWith("/?view=posters");
});

test.each(["video", "comic"])(
  "clearing %s progress calls its endpoint and reloads",
  async (type) => {
    const { fetcher, onChanged } = open({
      groups: [group({ single: true, slots: [slot(entry({ type }))] })],
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "More options for film" }),
    );
    fetcher.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Clear progress" }),
    );
    await waitFor(() => expect(onChanged).toHaveBeenCalledWith());
    expect(fetcher).toHaveBeenLastCalledWith(
      `/api/titles/film/${type === "comic" ? "comic/" : ""}progress`,
      { method: "DELETE" },
    );
  },
);

test("card action failures reach the library; hand-linked media can be unlinked", async () => {
  const { fetcher, onChanged } = open({
    groups: [
      group({
        single: true,
        slots: [
          slot(
            entry({
              linkedByHand: true,
              mediaRepositoryId: "repo",
              mediaPath: "Film.mp4",
            }),
          ),
        ],
      }),
    ],
  });
  await userEvent.click(
    await screen.findByRole("button", { name: "More options for film" }),
  );
  fetcher.mockResolvedValueOnce(
    Response.json({ error: "Cannot unlink" }, { status: 409 }),
  );
  await userEvent.click(
    await screen.findByRole("menuitem", {
      name: "Unlink video (linked by hand)",
    }),
  );
  await waitFor(() => expect(onChanged).toHaveBeenCalledOnce());
  expect(onChanged.mock.calls[0][0].message).toBe("Cannot unlink");
  expect(fetcher).toHaveBeenLastCalledWith(
    "/api/library/media-links?repository=repo&path=Film.mp4",
    { method: "DELETE" },
  );
});

test("bare media offers a link dialog and copies open their own titles", async () => {
  const bare = entry({
    fingerprint: "raw",
    progress: 0,
    mediaRepositoryId: "repo",
    mediaPath: "Film.mp4",
  });
  open({
    groups: [
      group({
        single: true,
        slots: [
          {
            ...slot(bare),
            copies: [bare, entry({ id: "copy", repositoryId: "other" })],
          },
        ],
      }),
    ],
  });
  expect(
    (await screen.findByRole("link", { name: "A series" })).getAttribute(
      "href",
    ),
  ).toBe("/video/raw/");
  await userEvent.click(
    screen.getByRole("button", { name: "2 copies of film" }),
  );
  expect(
    (await screen.findByRole("menuitem", { name: /Other/ })).getAttribute(
      "href",
    ),
  ).toBe("/watch/copy/");
  await userEvent.keyboard("{Escape}");
  await userEvent.click(
    screen.getByRole("button", { name: "More options for film" }),
  );
  await userEvent.click(
    await screen.findByRole("menuitem", { name: "Link to study document…" }),
  );
  expect(
    within(
      await screen.findByRole("dialog", { name: "Link to study document" }),
    ).getByText("film"),
  ).toBeTruthy();
});

test("load failures are visible and a changed library reloads with repository and language filters", async () => {
  const { fetcher, rerender } = open({
    response: () => Response.json({ error: "Offline" }, { status: 503 }),
    selection: { repository: "repo" },
    language: "fr",
  });
  expect((await screen.findByRole("alert")).textContent).toBe("Offline");
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/library/posters?repository=repo&language=fr",
  );
  fetcher.mockResolvedValueOnce(
    Response.json({ shelves: [], folders: [], multilingual: false }),
  );
  rerender({ data: { repos: [] } });
  expect(
    await screen.findByText("No titles match these filters."),
  ).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});

test("replacing a request aborts the previous load", async () => {
  const { fetcher, rerender } = open({ response: () => new Promise(() => {}) });
  const signal = fetcher.mock.calls[0][1].signal;
  rerender({ language: "en" });
  expect(signal.aborted).toBe(true);
  expect(fetcher).toHaveBeenCalledTimes(2);
});
