// @vitest-environment jsdom
import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { StudyLanguagesProvider } from "../src/player/StudyLanguages.jsx";
import { VideoSubtitles } from "../src/player/components/VideoSubtitles.jsx";

const cue = {
  id: "line",
  source: "Un petit chat",
  translation: "A little cat indeed",
  alignment: {
    source: ["Un", "petit", "chat"],
    translation: ["A", "little", "cat", "indeed"],
    links: [
      { source: [0], translation: [0], note: "First phrase." },
      { source: [1, 2], translation: [1, 2], note: "Shared explanation." },
      { source: [], translation: [3] },
    ],
  },
};
const observers = [];
let room;
function open(overrides = {}) {
  const props = { cue, onActiveChange: vi.fn(), ...overrides };
  const onClick = vi.fn(),
    onKeyDown = vi.fn();
  const wrap = (next) => (
    <StudyLanguagesProvider
      document={{
        sourceLanguage: "fr",
        translationLanguage: "en",
        notesLanguage: "en",
      }}
    >
      <div onClick={onClick} onKeyDown={onKeyDown}>
        <div className="video-sentence-notes" />
        <VideoSubtitles {...next} />
      </div>
    </StudyLanguagesProvider>
  );
  const view = render(wrap(props));
  return {
    ...props,
    ...view,
    onClick,
    onKeyDown,
    rerender: (next) => view.rerender(wrap({ ...props, ...next })),
  };
}
const source = (name) => screen.getByRole("button", { name, exact: true });
const translation = () => screen.getByRole("group", { name: "Translation" });
const explanation = () =>
  screen.queryByRole("complementary", { name: "Phrase explanation" });
const tick = (ms) => act(() => vi.advanceTimersByTime(ms));

beforeEach(() => {
  vi.useFakeTimers();
  room = { width: 1000, height: 600, lineTop: 600 };
  observers.length = 0;
  vi.stubGlobal(
    "ResizeObserver",
    class {
      constructor(update) {
        this.update = update;
        this.observe = vi.fn();
        this.disconnect = vi.fn();
        observers.push(this);
      }
    },
  );
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(
    function () {
      if (this.classList.contains("video-subtitles"))
        return { top: 100, width: room.width, height: room.height };
      if (this.classList.contains("video-subtitle-line"))
        return { top: room.lineTop };
      if (this.classList.contains("video-sentence-notes")) return { top: 500 };
      if (this.classList.contains("video-subtitle-english"))
        return { height: 40 };
      return { top: 200, width: 300, height: 100 };
    },
  );
});
afterEach(() => vi.useRealTimers());

test("plain or invalid alignments still show the source and optional full translation", () => {
  const { rerender } = open({ cue: { ...cue, alignment: null } });
  expect(screen.getByText(cue.source).getAttribute("class")).toBe(
    "video-subtitle-plain",
  );
  expect(screen.queryByRole("button")).toBeNull();
  expect(screen.queryByRole("group", { name: "Translation" })).toBeNull();
  rerender({
    cue: { ...cue, source: "Edited source" },
    showTranslation: true,
    hideSource: true,
    lineOffset: 2,
  });
  expect(translation().textContent).toBe(cue.translation);
  expect(translation().getAttribute("lang")).toBe("en");
  const line = screen.getByText("Edited source").parentElement;
  expect(line.style.visibility).toBe("hidden");
  expect(line.style.bottom).toContain("6em");
  expect(line.getAttribute("lang")).toBe("fr");
});

test("hovering explains a phrase and crossing into its translation keeps help visible", () => {
  const { onActiveChange } = open();
  fireEvent.mouseEnter(source("Un"));
  expect(explanation().textContent).toBe("First phrase.");
  expect(within(translation()).getByText("A").getAttribute("data-active")).toBe(
    "true",
  );
  expect(source("Un").getAttribute("aria-describedby")).toBe(translation().id);
  expect(onActiveChange).toHaveBeenLastCalledWith("line", true);
  fireEvent.mouseLeave(source("Un"));
  tick(349);
  expect(explanation()).toBeTruthy();
  fireEvent.mouseEnter(translation().firstChild);
  tick(500);
  expect(explanation()).toBeTruthy();
  fireEvent.mouseLeave(translation().firstChild);
  tick(350);
  expect(explanation()).toBeNull();
  expect(screen.queryByRole("group", { name: "Translation" })).toBeNull();
  expect(onActiveChange).toHaveBeenLastCalledWith("line", false);
});

test("focus overrides hover, pinning overrides focus, and external definitions override both", () => {
  const { rerender } = open();
  fireEvent.mouseEnter(source("Un"));
  fireEvent.focus(source("petit"));
  expect(explanation().textContent).toBe("Shared explanation.");
  fireEvent.click(source("chat"));
  fireEvent.focus(source("Un"));
  expect(source("chat").getAttribute("data-active")).toBe("true");
  expect(source("chat").getAttribute("aria-pressed")).toBe("true");
  rerender({ definitionIndex: 0 });
  expect(explanation().textContent).toBe("First phrase.");
  expect(source("chat").getAttribute("aria-pressed")).toBe("false");
  rerender({ definitionIndex: null });
  expect(explanation()).toBeNull();
});

test.each(["Enter", " "])(
  "translated chunks select their first source phrase with %s, including shared links",
  (key) => {
    const { onClick } = open({ showTranslation: true });
    const chunk = within(translation()).getByRole("button", { name: "cat" });
    fireEvent.keyDown(chunk, { key });
    expect(source("petit").getAttribute("aria-pressed")).toBe("true");
    expect(explanation().textContent).toBe("Shared explanation.");
    expect(onClick).not.toHaveBeenCalled();
    fireEvent.click(source("chat"));
    expect(chunk.getAttribute("aria-pressed")).toBe("true");
    const unmatched = within(translation()).getByText("indeed");
    expect(unmatched.getAttribute("role")).toBeNull();
    fireEvent.mouseEnter(unmatched);
    fireEvent.click(unmatched);
    expect(source("chat").getAttribute("aria-pressed")).toBe("true");
  },
);

test("keyboard selection and Escape work inside the fullscreen overlay without reaching player shortcuts", async () => {
  vi.useRealTimers();
  const user = userEvent.setup();
  const { onClick, onKeyDown } = open();
  await user.tab();
  expect(document.activeElement).toBe(source("Un"));
  await user.keyboard("{Enter}");
  expect(source("Un").getAttribute("aria-pressed")).toBe("true");
  expect(onClick).not.toHaveBeenCalled();
  onKeyDown.mockClear();
  await user.keyboard("{Escape}");
  expect(explanation()).toBeNull();
  expect(document.activeElement).toBe(document.body);
  expect(onKeyDown).not.toHaveBeenCalled();
});

test("pinned help survives interaction with notes or phrases and closes on an outside press", () => {
  open();
  fireEvent.click(source("Un"));
  fireEvent.pointerDown(explanation());
  fireEvent.click(explanation());
  fireEvent.pointerDown(source("petit"));
  expect(source("Un").getAttribute("aria-pressed")).toBe("true");
  fireEvent.pointerDown(document.body);
  expect(source("Un").getAttribute("aria-pressed")).toBe("false");
  expect(explanation()).toBeNull();
});

test("help stays above subtitle layers and repositions when the viewport resizes", () => {
  const { unmount, onActiveChange } = open();
  fireEvent.mouseEnter(source("Un"));
  expect(translation().style.bottom).toBe("106px");
  expect(explanation().style.left).toBe("350px");
  expect(explanation().style.top).toBe("242px");
  expect(explanation().style.maxHeight).toBe("334px");
  room.width = 600;
  act(() => observers.at(-1).update());
  expect(explanation().style.left).toBe("150px");
  const note = explanation();
  room.lineTop = 130;
  act(() => observers.at(-1).update());
  expect(note.style.visibility).toBe("hidden");
  fireEvent.mouseLeave(source("Un"));
  unmount();
  expect(
    observers.every((observer) => observer.disconnect.mock.calls.length === 1),
  ).toBe(true);
  expect(onActiveChange).toHaveBeenLastCalledWith("line", false);
  expect(vi.getTimerCount()).toBe(0);
});
