// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";
import { ComicReader } from "../src/comic/ComicReader.tsx";

// The reader coordinates page loading and annotation operations. Image geometry and study
// editing have their own components; these stand-ins expose their public callbacks.
const page = vi.hoisted(() => ({ props: null }));
vi.mock("../src/comic/ComicPageView.tsx", () => ({
  ComicPageView: (props) => {
    page.props = props;
    return (
      <section aria-label="Displayed pages">
        <span>{props.pages.map((p) => p.id).join(",")}</span>
        <span>Selected: {props.study.selectedId}</span>
        <button onClick={props.onLoaded}>Image loaded</button>
        <button
          onClick={() => props.annotations.onDraw(region, props.pages[0].id)}
        >
          Draw box
        </button>
        <button
          onClick={() =>
            props.annotations.onPlacement(props.boxes[0], [region])
          }
        >
          Move box
        </button>
        <button onClick={() => props.study.onSelect("line-2")}>
          Select sentence
        </button>
      </section>
    );
  },
}));
vi.mock("../src/comic/ComicStudyPanel.jsx", () => ({
  ComicStudyPanel: ({ onEditing }) => (
    <aside aria-label="Study panel">
      <button onClick={() => onEditing(true)}>Start editing</button>
      <button onClick={() => onEditing(false)}>Finish editing</button>
    </aside>
  ),
}));

const region = { x: 0.2, y: 0.3, width: 0.1, height: 0.2 };
const line = (id, source) => ({ id, source, translation: "Hello", notes: [] });
function comic() {
  return {
    id: "comic",
    title: "Example comic",
    sourceLanguage: "fr",
    translationLanguage: "en",
    notesLanguage: "en",
    comic: {
      readingDirection: "ltr",
      chapters: [
        { number: 1, title: "Beginning", pageId: "p1" },
        { number: 2, title: "Later", pageId: "p3" },
      ],
      pages: [
        {
          id: "p1",
          entry: "1.png",
          boxes: [
            {
              id: "box-1",
              regions: [region],
              lines: [line("line-1", "Bonjour"), line("line-2", "Salut")],
            },
          ],
        },
        { id: "p2", entry: "2.png", boxes: [] },
        { id: "p3", entry: "3.png", boxes: [] },
      ],
    },
  };
}
function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function open({
  resume = "p1",
  read,
  write = () => Response.json(comic()),
} = {}) {
  const fetcher = vi.fn(async (url, options = {}) => {
    if (options.method) return write(url, options);
    if (read) return read(url, options);
    return Response.json(
      url.endsWith("/progress") ? { pageId: resume } : comic(),
    );
  });
  vi.stubGlobal("fetch", fetcher);
  const rendered = render(
    <MantineProvider env="test">
      <ComicReader id="comic" />
    </MantineProvider>,
  );
  return { fetcher, ...rendered };
}
async function annotate() {
  await userEvent.click(
    await screen.findByRole("button", { name: "Edit mode" }),
  );
}

beforeEach(() => {
  localStorage.clear();
  page.props = null;
});

test("loads the saved page but records progress only after the page image loads", async () => {
  const { fetcher } = open({ resume: "p2" });
  expect(screen.getByLabelText("Loading comic")).toBeTruthy();
  expect(
    (await screen.findByRole("combobox", { name: "Page", exact: true })).value,
  ).toBe("1");
  expect(
    fetcher.mock.calls.filter(([, options]) => options.method),
  ).toHaveLength(0);
  await userEvent.click(screen.getByRole("button", { name: "Image loaded" }));
  await waitFor(() =>
    expect(fetcher).toHaveBeenCalledWith(
      "/api/titles/comic/comic/progress",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ pageId: "p2" }),
        keepalive: true,
      }),
    ),
  );
});

test("missing saved pages fall back to the cover and chapter navigation follows manifest order", async () => {
  open({ resume: "gone" });
  const picker = await screen.findByRole("combobox", {
    name: "Page",
    exact: true,
  });
  expect(picker.value).toBe("0");
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Chapter" }),
    "1",
  );
  expect(picker.value).toBe("2");
  fireEvent.keyDown(window, { key: "Home" });
  expect(picker.value).toBe("0");
});

test("serializes progress writes and can retry after failure", async () => {
  const first = deferred();
  let calls = 0;
  const { fetcher } = open({
    write: () => (++calls === 1 ? first.promise : Response.json({})),
  });
  await userEvent.click(
    await screen.findByRole("button", { name: "Image loaded" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "Next page (→)" }));
  await userEvent.click(screen.getByRole("button", { name: "Image loaded" }));
  expect(calls).toBe(1);
  await act(async () =>
    first.resolve(Response.json({ error: "Offline" }, { status: 503 })),
  );
  await waitFor(() => expect(calls).toBe(2));
  await waitFor(() =>
    expect(
      screen.queryByText("Your reading position could not be saved."),
    ).toBeNull(),
  );
  expect(
    fetcher.mock.calls
      .filter(([, o]) => o.method)
      .map(([, o]) => JSON.parse(o.body).pageId),
  ).toEqual(["p1", "p2"]);
});

test("shows progress failures with a retry for the displayed page", async () => {
  let fails = true;
  open({
    write: () =>
      fails
        ? Response.json({ error: "Offline" }, { status: 503 })
        : Response.json({}),
  });
  await userEvent.click(
    await screen.findByRole("button", { name: "Image loaded" }),
  );
  expect(
    await screen.findByText("Your reading position could not be saved."),
  ).toBeTruthy();
  fails = false;
  await userEvent.click(screen.getByRole("button", { name: "Retry saving" }));
  await waitFor(() =>
    expect(
      screen.queryByText("Your reading position could not be saved."),
    ).toBeNull(),
  );
});

test("load failures explain the problem and unmount aborts both initial requests", async () => {
  const { fetcher, unmount } = open({
    read: () => Response.json({ error: "Comic missing" }, { status: 404 }),
  });
  expect(await screen.findByText("Comic missing")).toBeTruthy();
  expect(screen.getByRole("link", { name: "Return to library" })).toBeTruthy();
  unmount();
  expect(
    fetcher.mock.calls
      .slice(0, 2)
      .every(([, options]) => options.signal.aborted),
  ).toBe(true);
});

test("drawing locks navigation, suppresses duplicate writes, and selects the created line", async () => {
  const pending = deferred();
  const { fetcher } = open({ write: () => pending.promise });
  await annotate();
  act(() => {
    page.props.annotations.onDraw(region, "p1");
    page.props.annotations.onDraw(region, "p1");
  });
  expect(
    fetcher.mock.calls.filter(([, options]) => options.method === "POST"),
  ).toHaveLength(1);
  expect(screen.getByRole("button", { name: "Next page (→)" }).disabled).toBe(
    true,
  );
  expect(screen.getByText("Saving box…")).toBeTruthy();
  const saved = comic();
  saved.comic.pages[0].boxes.push({
    id: "new",
    regions: [region],
    lines: [line("new-line", "")],
  });
  await act(async () => pending.resolve(Response.json(saved)));
  expect(await screen.findByText("Selected: new-line")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Next page (→)" }).disabled).toBe(
    false,
  );
  expect(fetcher).toHaveBeenLastCalledWith(
    "/api/titles/comic/comic/boxes",
    expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ pageId: "p1", region }),
    }),
  );
});

test("failed drawings retain their draft for retry or discard", async () => {
  let fails = true;
  const { fetcher } = open({
    write: () =>
      fails
        ? Response.json({ error: "Try later" }, { status: 503 })
        : Response.json(comic()),
  });
  await annotate();
  await userEvent.click(screen.getByRole("button", { name: "Draw box" }));
  expect(await screen.findByText("Try later")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Next page (→)" }).disabled).toBe(
    true,
  );
  fails = false;
  await userEvent.click(
    screen.getByRole("button", { name: "Retry saving box" }),
  );
  await waitFor(() => expect(screen.queryByText("Try later")).toBeNull());
  expect(
    fetcher.mock.calls.filter(([, o]) => o.method === "POST"),
  ).toHaveLength(2);
  fails = true;
  await userEvent.click(screen.getByRole("button", { name: "Draw box" }));
  await screen.findByText("Try later");
  await userEvent.click(
    screen.getByRole("button", { name: "Discard unsaved box" }),
  );
  expect(screen.getByRole("button", { name: "Next page (→)" }).disabled).toBe(
    false,
  );
});

test("placement retains expected regions; deletion confirms the entire selected text block", async () => {
  const confirm = vi.fn(() => false);
  vi.stubGlobal("confirm", confirm);
  const { fetcher } = open();
  await annotate();
  await userEvent.click(screen.getByRole("button", { name: "Move box" }));
  expect(fetcher).toHaveBeenLastCalledWith(
    "/api/titles/comic/comic/boxes/box-1/placement",
    expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({ expectedRegions: [region], regions: [region] }),
    }),
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Delete selected text block" }),
  );
  expect(confirm).toHaveBeenCalledWith(
    "Delete this text block and its 2 sentences?",
  );
  expect(fetcher.mock.calls.some(([, o]) => o.method === "DELETE")).toBe(false);
  confirm.mockReturnValue(true);
  await userEvent.click(
    screen.getByRole("button", { name: "Delete selected text block" }),
  );
  expect(fetcher).toHaveBeenLastCalledWith(
    "/api/titles/comic/comic/boxes/box-1",
    expect.objectContaining({ method: "DELETE" }),
  );
});

test("study editing locks page turns; E edits the selected sentence and ignores form fields", async () => {
  localStorage.setItem("comic:study", "true");
  open();
  await screen.findByRole("button", { name: "Start editing" });
  await userEvent.click(screen.getByRole("button", { name: "Start editing" }));
  fireEvent.keyDown(window, { key: "ArrowRight" });
  expect(
    screen.getByRole("combobox", { name: "Page", exact: true }).value,
  ).toBe("0");
  fireEvent.keyDown(window, { key: "e" });
  expect(screen.queryByRole("dialog")).toBeNull();
  await userEvent.click(screen.getByRole("button", { name: "Finish editing" }));
  await userEvent.click(
    screen.getByRole("button", { name: "Select sentence" }),
  );
  fireEvent.keyDown(
    screen.getByRole("combobox", { name: "Page", exact: true }),
    { key: "e" },
  );
  expect(screen.queryByRole("dialog")).toBeNull();
  fireEvent.keyDown(window, { key: "e", repeat: true });
  expect(screen.queryByRole("dialog")).toBeNull();
  fireEvent.keyDown(window, { key: "e" });
  expect(await screen.findByRole("dialog")).toBeTruthy();
  expect(screen.getByDisplayValue("Salut")).toBeTruthy();
});

test("reader preferences survive remount and annotation mode suppresses quick editing", async () => {
  const first = open();
  await userEvent.click(
    await screen.findByRole("button", { name: "Listen on hover" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "More options" }));
  await userEvent.click(
    await screen.findByRole("menuitem", { name: "Show bubble outlines" }),
  );
  expect(page.props.debug).toBe(true);
  first.unmount();
  open();
  expect(
    await screen.findByRole("button", { name: "Stop listening on hover" }),
  ).toBeTruthy();
  expect(page.props.debug).toBe(true);
  await annotate();
  fireEvent.keyDown(window, { key: "e" });
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("RTL and spread mode keep manifest progress while reversing arrow navigation", async () => {
  localStorage.setItem("comic:spread", "true");
  const rtl = comic();
  rtl.comic.readingDirection = "rtl";
  open({
    read: (url) =>
      Response.json(url.endsWith("/progress") ? { pageId: "p1" } : rtl),
  });
  await screen.findByRole("button", { name: "Image loaded" });
  expect(page.props.pages.map((p) => p.id)).toEqual(["p1"]);
  fireEvent.keyDown(window, { key: "ArrowLeft" });
  expect(page.props.pages.map((p) => p.id)).toEqual(["p2", "p3"]);
  fireEvent.keyDown(window, { key: "ArrowRight" });
  expect(page.props.pages.map((p) => p.id)).toEqual(["p1"]);
});

test("quiz preparation uses the currently displayed chapter and stays stable during page navigation", async () => {
  const { fetcher } = open({
    resume: "p3",
    write: () => Response.json({ text: "A quiz", fileName: "quiz.md" }),
  });
  await userEvent.click(await screen.findByRole("button", { name: /Quiz me/ }));
  await waitFor(() =>
    expect(
      fetcher.mock.calls.some(
        ([url]) => url === "/api/titles/comic/comic/quiz",
      ),
    ).toBe(true),
  );
  const quizRequests = () =>
    fetcher.mock.calls.filter(([url]) => url.endsWith("/quiz"));
  expect(JSON.parse(quizRequests()[0][1].body)).toEqual(
    expect.objectContaining({
      chapterPageId: "p3",
      rules: expect.any(String),
      type: expect.objectContaining({ label: expect.any(String) }),
    }),
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Download" }).disabled).toBe(
      false,
    ),
  );
  // Page navigation re-renders the reader without changing the quiz's selected document.
  fireEvent.keyDown(window, { key: "Home" });
  expect(quizRequests()).toHaveLength(1);
});

test("region editing is available directly from the toolbar and can be finished", async () => {
  open();
  const toggle = await screen.findByRole("button", {
    name: "Edit mode",
  });
  expect(toggle.getAttribute("aria-pressed")).toBe("false");
  await userEvent.click(toggle);
  expect(toggle.getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByRole("button", { name: "Exit edit mode" })).toBe(toggle);
  expect(screen.queryByText(/Region changes save automatically/)).toBeNull();
  expect(screen.getByRole("button", { name: "Editing help" })).toBeTruthy();
  expect(page.props.annotations.enabled).toBe(true);
  expect(
    screen.getByRole("complementary", { name: "Study panel" }),
  ).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Start editing" }));
  expect(toggle.disabled).toBe(true);
  expect(page.props.annotations.locked).toBe(true);
  await userEvent.click(screen.getByRole("button", { name: "Finish editing" }));
  expect(toggle.disabled).toBe(false);
  await userEvent.click(toggle);
  expect(toggle.getAttribute("aria-pressed")).toBe("false");
  expect(page.props.annotations.enabled).toBe(false);
});

test("Delete removes the selected box only in unlocked region editing and outside text fields", async () => {
  const confirm = vi.fn(() => false);
  vi.stubGlobal("confirm", confirm);
  localStorage.setItem("comic:study", "true");
  open();
  await screen.findByRole("button", { name: "Edit mode" });
  fireEvent.keyDown(window, { key: "Delete" });
  expect(confirm).not.toHaveBeenCalled();
  await annotate();
  const input = document.createElement("input");
  document.body.append(input);
  fireEvent.keyDown(input, { key: "Delete" });
  input.remove();
  expect(confirm).not.toHaveBeenCalled();
  fireEvent.keyDown(window, { key: "Delete", repeat: true });
  expect(confirm).not.toHaveBeenCalled();
  fireEvent.keyDown(window, { key: "Delete" });
  expect(confirm).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "Start editing" }));
  fireEvent.keyDown(window, { key: "Delete" });
  expect(confirm).toHaveBeenCalledTimes(1);
});
