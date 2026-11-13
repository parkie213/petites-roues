// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { request } from "../src/shared/api.ts";
import { ComicStudyPanel } from "../src/comic/ComicStudyPanel.jsx";
import { StudyLanguagesProvider } from "../src/player/StudyLanguages.jsx";

vi.mock("../src/comic/ReadAloud.jsx", () => ({
  useReadAloud: () => ({
    text: null,
    loading: false,
    error: "",
    speak: vi.fn(),
  }),
}));
const first = {
  id: "first",
  source: "Bonjour.",
  translation: "Hello.",
  notes: ["A greeting."],
  textCheck: "Check this text",
};
const second = {
  id: "second",
  source: "Salut.",
  translation: "Hi.",
  notes: [],
};
const last = {
  id: "last",
  source: "Au revoir.",
  translation: "Goodbye.",
  notes: [],
};
const boxes = [
  { id: "block", lines: [first, second] },
  { id: "bubble", lines: [last] },
];
function open(overrides = {}) {
  const props = {
    boxes,
    lines: [first, second, last],
    cue: first,
    api: "/api/titles/panel/comic",
    onSelect: vi.fn(),
    onSaved: vi.fn(),
    onEditing: vi.fn(),
    ...overrides,
  };
  const wrap = (next) => (
    <MantineProvider env="test">
      <StudyLanguagesProvider
        document={{
          sourceLanguage: "fr",
          translationLanguage: "en",
          notesLanguage: "en",
        }}
      >
        <ComicStudyPanel {...next} />
      </StudyLanguagesProvider>
    </MantineProvider>
  );
  const view = render(wrap(props));
  return {
    ...props,
    ...view,
    rerender: (next) => view.rerender(wrap({ ...props, ...next })),
  };
}
const button = (name) => screen.getByRole("button", { name, exact: true });
const draft = () => screen.getByRole("textbox", { name: "French text" });
function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
beforeEach(async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        Response.json({}, { headers: { "X-State-Version": "panel-state" } }),
      ),
  );
  await request("/api/titles/panel");
  vi.stubGlobal("fetch", vi.fn());
});

test("empty pages explain the absence of annotations and show no editing or navigation controls", () => {
  open({ cue: undefined, boxes: [], lines: [] });
  expect(screen.getByRole("heading", { name: "This page" })).toBeTruthy();
  expect(screen.getByText("No annotations on this page yet.")).toBeTruthy();
  expect(screen.queryByRole("button")).toBeNull();
});

test("navigation walks sentences and bubbles in reading order, including keyboard selection inside a block", () => {
  const { onSelect, rerender } = open();
  expect(screen.getByRole("heading", { name: "Sentence 1 of 2" })).toBeTruthy();
  expect(button("Previous bubble").disabled).toBe(true);
  fireEvent.click(button("Next bubble"));
  expect(onSelect).toHaveBeenLastCalledWith("second");
  fireEvent.keyDown(button("Salut."), { key: "Enter" });
  expect(onSelect).toHaveBeenLastCalledWith("second");
  fireEvent.keyDown(button("Bonjour."), { key: " " });
  expect(onSelect).toHaveBeenLastCalledWith("first");
  rerender({ cue: last });
  expect(screen.getByRole("heading", { name: "Bubble 2 of 2" })).toBeTruthy();
  expect(button("Next bubble").disabled).toBe(true);
  fireEvent.click(button("Previous bubble"));
  expect(onSelect).toHaveBeenLastCalledWith("second");
});

test("editing locks navigation, validates the draft and cancel discards unsaved changes", () => {
  const { onEditing } = open();
  expect(screen.getByText("Check this text")).toBeTruthy();
  fireEvent.click(button("Edit text"));
  expect(onEditing).toHaveBeenLastCalledWith(true);
  expect(draft().value).toBe(first.source);
  expect(button("Save text").disabled).toBe(true);
  expect(button("Next bubble").disabled).toBe(true);
  expect(screen.queryByRole("button", { name: "Salut." })).toBeNull();
  fireEvent.change(draft(), { target: { value: "  " } });
  expect(button("Save text").disabled).toBe(true);
  fireEvent.change(draft(), { target: { value: "Changed." } });
  expect(button("Save text").disabled).toBe(false);
  fireEvent.click(button("Cancel"));
  expect(onEditing).toHaveBeenLastCalledWith(false);
  expect(button("Next bubble").disabled).toBe(false);
  fireEvent.click(button("Edit text"));
  expect(draft().value).toBe(first.source);
});

test("saving uses the original text as a conflict guard and keeps the editor locked until success", async () => {
  const pending = deferred();
  fetch.mockReturnValueOnce(pending.promise);
  const { onSaved, onEditing } = open({ cue: { ...first, id: "line/one" } });
  fireEvent.click(button("Edit text"));
  fireEvent.change(draft(), { target: { value: " Changed text. " } });
  fireEvent.click(button("Save text"));
  expect(fetch).toHaveBeenCalledWith(
    "/api/titles/panel/commands",
    expect.objectContaining({
      method: "POST",
      body: expect.any(String),
    }),
  );
  expect(JSON.parse(fetch.mock.calls[0][1].body).commands).toEqual([
    {
      type: "edit-comic-text",
      lineId: "line/one",
      edit: { expectedSource: first.source, source: " Changed text. " },
    },
  ]);
  expect(draft().disabled).toBe(true);
  expect(button("Cancel").disabled).toBe(true);
  expect(onSaved).not.toHaveBeenCalled();
  const saved = { id: "updated-document" };
  await act(async () => pending.resolve(Response.json(saved)));
  expect(onSaved).toHaveBeenCalledWith(saved);
  expect(onEditing).toHaveBeenLastCalledWith(false);
  expect(screen.queryByRole("textbox")).toBeNull();
});

test("failed saves retain the draft and lock, and retry unlocks only after a successful save", async () => {
  fetch
    .mockResolvedValueOnce(
      Response.json({ error: "Text changed elsewhere" }, { status: 409 }),
    )
    .mockResolvedValueOnce(Response.json({ id: "saved" }));
  const { onEditing, onSaved } = open();
  fireEvent.click(button("Edit text"));
  fireEvent.change(draft(), { target: { value: "My correction" } });
  fireEvent.click(button("Save text"));
  expect(await screen.findByText("Text changed elsewhere")).toBeTruthy();
  expect(draft().value).toBe("My correction");
  expect(button("Next bubble").disabled).toBe(true);
  expect(onEditing).toHaveBeenLastCalledWith(true);
  expect(onSaved).not.toHaveBeenCalled();
  fireEvent.click(button("Save text"));
  await waitFor(() => expect(onEditing).toHaveBeenLastCalledWith(false));
  expect(onSaved).toHaveBeenCalledWith({ id: "saved" });
  expect(screen.queryByText("Text changed elsewhere")).toBeNull();
});

test("cancelling a failed edit clears its error before the next edit", async () => {
  fetch.mockResolvedValueOnce(
    Response.json({ error: "Offline" }, { status: 503 }),
  );
  open();
  fireEvent.click(button("Edit text"));
  fireEvent.change(draft(), { target: { value: "Correction" } });
  fireEvent.click(button("Save text"));
  await screen.findByText("Offline");
  fireEvent.click(button("Cancel"));
  fireEvent.click(button("Edit text"));
  expect(screen.queryByText("Offline")).toBeNull();
  expect(draft().value).toBe(first.source);
});

test("an empty source can enter editing and leave it without becoming a valid save", () => {
  open({ cue: { ...last, source: "", translation: null, notes: [] } });
  expect(screen.getByText("Translation awaiting review.")).toBeTruthy();
  fireEvent.click(button("Add text"));
  expect(draft().value).toBe("");
  expect(button("Save text").disabled).toBe(true);
  fireEvent.change(draft(), { target: { value: "New transcription" } });
  expect(button("Save text").disabled).toBe(false);
  fireEvent.click(button("Cancel"));
  expect(button("Add text")).toBeTruthy();
});
