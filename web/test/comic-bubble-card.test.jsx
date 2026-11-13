// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ComicBubbleCard } from "../src/comic/ComicBubbleCard.jsx";
import { StudyLanguagesProvider } from "../src/player/StudyLanguages.jsx";

const voice = vi.hoisted(() => ({
  speak: vi.fn(),
  autoListen: false,
  text: null,
  loading: false,
  error: "",
}));
vi.mock("../src/comic/ReadAloud.jsx", () => ({ useReadAloud: () => voice }));
const cue = {
  id: "line",
  pageId: "p1",
  source: "Un petit chat",
  translation: "A little cat indeed",
  notes: ["Sentence note."],
  alignment: {
    source: ["Un", "petit", "chat"],
    translation: ["A", "little", "cat", "indeed"],
    links: [
      { source: [0], translation: [0], note: "First phrase." },
      { source: [1, 2], translation: [1, 2], note: "Together." },
      { source: [], translation: [3] },
    ],
  },
};
const document = {
  title: "Comic",
  sourceLanguage: "fr",
  translationLanguage: "en",
  notesLanguage: "en",
  pages: [{ id: "p1" }],
  boxes: [],
};
function open(overrides = {}) {
  const props = { cue, document, onKeep: vi.fn(), ...overrides };
  const wrap = (next) => (
    <MantineProvider env="test">
      <StudyLanguagesProvider document={document}>
        <ComicBubbleCard {...next} />
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
const phrase = (name) => screen.getByRole("button", { name, exact: true });
const tick = (ms) => act(() => vi.advanceTimersByTime(ms));
beforeEach(() => {
  voice.speak.mockClear();
  voice.autoListen = false;
  Object.defineProperties(navigator, {
    clipboard: {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue() },
    },
    share: { configurable: true, value: undefined },
  });
});
afterEach(() => vi.useRealTimers());

test("hover temporarily overrides a pin; leaving restores it and Listen reads the pinned phrase", () => {
  open();
  fireEvent.click(phrase("Un"));
  fireEvent.mouseEnter(phrase("chat"));
  expect(phrase("chat").getAttribute("data-active")).toBe("true");
  expect(phrase("Un").getAttribute("aria-pressed")).toBe("true");
  fireEvent.click(phrase("Listen"));
  expect(voice.speak).toHaveBeenCalledWith("Un");
  fireEvent.mouseLeave(phrase("chat"));
  expect(phrase("Un").getAttribute("data-active")).toBe("true");
  fireEvent.click(phrase("Un"));
  fireEvent.click(phrase("Listen"));
  expect(voice.speak).toHaveBeenLastCalledWith(cue.source);
});

test.each(["Enter", " "])(
  "source and translation chunks share keyboard selection with %s; unmatched chunks remain text",
  (key) => {
    open();
    fireEvent.keyDown(phrase("little"), { key });
    expect(phrase("petit").getAttribute("aria-pressed")).toBe("true");
    fireEvent.focus(phrase("Un"));
    expect(phrase("Un").getAttribute("data-active")).toBe("true");
    fireEvent.blur(phrase("Un"));
    expect(phrase("petit").getAttribute("data-active")).toBe("true");
    fireEvent.keyDown(phrase("petit"), { key });
    expect(phrase("petit").getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByText("indeed").getAttribute("role")).toBeNull();
    expect(
      screen.getByRole("list", { name: "Sentence notes" }).textContent,
    ).toBe("Sentence note.");
  },
);

test("opening a question keeps the card, includes only the pinned phrase, and Escape closes the form", async () => {
  const { onKeep } = open();
  fireEvent.click(phrase("petit"));
  fireEvent.mouseEnter(phrase("Un"));
  fireEvent.click(phrase("Ask about this bubble"));
  expect(onKeep).toHaveBeenCalledOnce();
  expect(screen.getByText("About “petit”")).toBeTruthy();
  fireEvent.change(screen.getByRole("textbox", { name: "Your question" }), {
    target: { value: "Why this phrase?" },
  });
  fireEvent.click(phrase("Copy"));
  const message = navigator.clipboard.writeText.mock.calls[0][0];
  expect(message).toContain("My question: Why this phrase?");
  expect(message).toContain("I'm asking about the phrase “petit”.");
  expect(message).toContain(`Bubble: ${cue.source}`);
  expect((await screen.findByRole("status")).textContent).toBe(
    "Copied: paste it into a chat.",
  );
  fireEvent.keyDown(screen.getByRole("textbox"), { key: "Escape" });
  expect(screen.queryByRole("textbox")).toBeNull();
  expect(phrase("Ask about this bubble").getAttribute("aria-expanded")).toBe(
    "false",
  );
});

test("chat opening and copying happen before clipboard completion, with a default question", async () => {
  let resolve;
  navigator.clipboard.writeText.mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const openChat = vi.spyOn(window, "open").mockImplementation(() => null);
  open();
  fireEvent.click(phrase("Ask about this bubble"));
  fireEvent.click(phrase("ChatGPT ↗"));
  const message = navigator.clipboard.writeText.mock.calls[0][0];
  expect(message).toContain("Please explain this bubble in more depth.");
  expect(openChat).toHaveBeenCalledWith(
    `https://chatgpt.com/?q=${encodeURIComponent(message)}`,
    "_blank",
    "noopener",
  );
  await act(async () => resolve());
  expect((await screen.findByRole("status")).textContent).toContain(
    "Opened ChatGPT and copied the message",
  );
});

test("clipboard failure leaves the question intact and native Share uses the same message", async () => {
  navigator.clipboard.writeText.mockRejectedValue(Error("Denied"));
  const share = vi.fn().mockResolvedValue();
  Object.defineProperty(navigator, "share", {
    configurable: true,
    value: share,
  });
  open();
  fireEvent.click(phrase("Ask about this bubble"));
  fireEvent.change(screen.getByRole("textbox"), {
    target: { value: "Explain this" },
  });
  fireEvent.click(phrase("Copy"));
  expect((await screen.findByRole("status")).textContent).toContain(
    "Could not copy",
  );
  expect(screen.getByRole("textbox").value).toBe("Explain this");
  fireEvent.click(phrase("Share…"));
  expect(share).toHaveBeenCalledWith({
    text: navigator.clipboard.writeText.mock.calls[0][0],
  });
});

test("auto-listen waits for a rest, runs once per mount, and cancels when the card closes", () => {
  vi.useFakeTimers();
  voice.autoListen = true;
  const { rerender, unmount } = open();
  tick(249);
  expect(voice.speak).not.toHaveBeenCalled();
  tick(1);
  expect(voice.speak).toHaveBeenCalledWith(cue.source);
  rerender({ cue: { ...cue, notes: ["Updated note"] } });
  tick(500);
  expect(voice.speak).toHaveBeenCalledOnce();
  unmount();
  voice.speak.mockClear();
  const second = open();
  second.unmount();
  tick(500);
  expect(voice.speak).not.toHaveBeenCalled();
});

test("quiet placeholder cards never auto-listen; missing translations and documents remain usable", () => {
  vi.useFakeTimers();
  voice.autoListen = true;
  open({
    quiet: true,
    document: null,
    cue: { ...cue, alignment: null, translation: null },
  });
  tick(500);
  expect(voice.speak).not.toHaveBeenCalled();
  expect(screen.getByText("No translation for this bubble yet.")).toBeTruthy();
  expect(
    screen.queryByRole("button", { name: "Ask about this bubble" }),
  ).toBeNull();
  fireEvent.click(phrase("Listen"));
  expect(voice.speak).toHaveBeenCalledWith(cue.source);
});

test("a removed alignment falls back to the edited bubble without losing the question", async () => {
  const { rerender } = open();
  fireEvent.click(phrase("petit"));
  fireEvent.click(phrase("Ask about this bubble"));
  fireEvent.change(screen.getByRole("textbox"), {
    target: { value: "My question" },
  });
  rerender({ cue: { ...cue, source: "Edited bubble", alignment: null } });
  expect(screen.queryByText("About “petit”")).toBeNull();
  expect(screen.getByRole("textbox").value).toBe("My question");
  fireEvent.click(phrase("Listen"));
  expect(voice.speak).toHaveBeenCalledWith("Edited bubble");
  fireEvent.click(phrase("Copy"));
  const message = navigator.clipboard.writeText.mock.calls[0][0];
  expect(message).toContain("My question: My question");
  expect(message).toContain("Bubble: Edited bubble");
  expect(message).not.toContain("I'm asking about the phrase");
  await screen.findByRole("status");
});

test("auto-listen skips foreign-script-only regions but manual listening remains available", () => {
  vi.useFakeTimers();
  voice.autoListen = true;
  open({ cue: { id: "sfx", source: "ドドド", translation: "" } });
  tick(300);
  expect(voice.speak).not.toHaveBeenCalled();
  fireEvent.click(phrase("Listen"));
  expect(voice.speak).toHaveBeenCalledWith("ドドド");
});
