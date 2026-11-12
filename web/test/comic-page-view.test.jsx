// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { ComicPageView } from "../src/comic/ComicPageView.tsx";

// Keep the real page regions and pointer routing. The study card is represented by a portal,
// like Mantine's popover, so entering it exercises the page/card hover boundary.
vi.mock("../src/comic/ComicBubblePopover.jsx", async () => {
  const { createPortal } = await import("react-dom");
  return {
    ComicBubblePopover: ({
      children,
      opened,
      number,
      onKeep,
      onDismiss,
      onPointerEnter,
      onPointerLeave,
    }) => (
      <>
        {children}
        {opened &&
          createPortal(
            <aside
              role="dialog"
              aria-label={`Card ${number}`}
              onPointerEnter={onPointerEnter}
              onPointerLeave={onPointerLeave}
            >
              <button onClick={onKeep}>Keep card</button>
              <button onClick={onDismiss}>Dismiss card</button>
            </aside>,
            globalThis.document.body,
          )}
      </>
    ),
  };
});

const firstRegion = { x: 0.1, y: 0.2, width: 0.3, height: 0.2 };
function box(id, pageId = "p1", regions = [firstRegion]) {
  return {
    id,
    pageId,
    order: 1,
    regions,
    lines: [{ id: `${id}-line`, source: id }],
  };
}
const boxes = [box("First"), box("Second")];
const pages = [{ id: "p1" }, { id: "p2" }];
function open(overrides = {}) {
  const props = {
    document: { title: "Comic", pages },
    pages: [pages[0]],
    api: "/api/comic",
    zoom: "page",
    scale: 100,
    boxes,
    rtl: false,
    selectedId: "First-line",
    onSelect: vi.fn(),
    onLoaded: vi.fn(),
    debug: false,
    cards: true,
    blockMode: "step",
    onBlockMode: vi.fn(),
    editing: false,
    annotate: false,
    onDraw: vi.fn(),
    onPlacement: vi.fn(),
    ...overrides,
  };
  const wrap = (next) => (
    <MantineProvider env="test">
      <ComicPageView
        document={next.document}
        pages={next.pages}
        api={next.api}
        zoom={next.zoom}
        scale={next.scale}
        boxes={next.boxes}
        rtl={next.rtl}
        onLoaded={next.onLoaded}
        debug={next.debug}
        study={{
          selectedId: next.selectedId,
          onSelect: next.onSelect,
          cards: next.cards,
          blockMode: next.blockMode,
          onBlockMode: next.onBlockMode,
        }}
        annotations={{
          enabled: next.annotate,
          locked: next.editing,
          onDraw: next.onDraw,
          onPlacement: next.onPlacement,
        }}
      />
    </MantineProvider>
  );
  const rendered = render(wrap(props));
  return {
    ...props,
    ...rendered,
    rerender: (next) => rendered.rerender(wrap({ ...props, ...next })),
  };
}
function load(name = "Comic, page 1") {
  const image = screen.getByRole("img", { name });
  Object.defineProperties(image, {
    naturalWidth: { configurable: true, value: 800 },
    naturalHeight: { configurable: true, value: 1000 },
  });
  fireEvent.load(image);
  const stage = image.parentElement;
  stage.getBoundingClientRect = () => ({
    left: 0,
    top: 0,
    width: 800,
    height: 1000,
  });
  return stage;
}
const first = () => screen.getByRole("button", { name: "Bubble 1: First" });
const second = () => screen.getByRole("button", { name: "Bubble 2: Second" });
const hover = (element) =>
  fireEvent.pointerEnter(element, { pointerType: "mouse" });
const leave = (element) =>
  fireEvent.pointerLeave(element, { pointerType: "mouse" });
const tick = (ms) => act(() => vi.advanceTimersByTime(ms));
function drag(target, start, end) {
  fireEvent.pointerDown(target, {
    button: 0,
    clientX: start[0],
    clientY: start[1],
  });
  fireEvent.pointerMove(window, { clientX: end[0], clientY: end[1] });
  fireEvent.pointerUp(window);
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {
        this.callback([{ contentRect: { width: 1000, height: 600 } }]);
      }
      disconnect() {}
      constructor(callback) {
        this.callback = callback;
      }
    },
  );
  vi.stubGlobal(
    "PointerEvent",
    class extends MouseEvent {
      constructor(type, init = {}) {
        super(type, init);
        this.pointerType = init.pointerType ?? "mouse";
      }
    },
  );
});
afterEach(() => vi.useRealTimers());

test("pages render their actual image dimensions and only the first loaded page records progress", () => {
  const { onLoaded } = open({
    pages,
    rtl: true,
    boxes: [box("First"), box("Second", "p2")],
  });
  expect(
    screen
      .getByRole("region", { name: "Comic spread" })
      .querySelector(".comic-spread").style.flexDirection,
  ).toBe("row-reverse");
  const right = load("Comic, page 2");
  expect(onLoaded).not.toHaveBeenCalled();
  expect(right.style.width).toBe("480px");
  expect(right.style.height).toBe("600px");
  const left = load();
  expect(onLoaded).toHaveBeenCalledOnce();
  expect(left.querySelector("button").style.left).toBe("10%");
  expect(screen.getAllByRole("button", { name: /Bubble/ })).toHaveLength(2);
});

test("image failure hides regions and retry fetches a new attempt before recording progress", () => {
  const { onLoaded } = open();
  const image = screen.getByRole("img", { name: "Comic, page 1" });
  expect(screen.getByLabelText("Loading page")).toBeTruthy();
  fireEvent.error(image);
  expect(screen.getByText("This page could not be displayed.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: /Bubble/ })).toBeNull();
  expect(onLoaded).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Retry page" }));
  expect(screen.getByRole("img").getAttribute("src")).toBe(
    "/api/comic/pages/p1?attempt=1",
  );
  load();
  expect(onLoaded).toHaveBeenCalledOnce();
});

test("changing zoom resets scrolling and recomputes image geometry", () => {
  const scroll = vi.spyOn(Element.prototype, "scrollTo");
  const { rerender } = open();
  const stage = load();
  expect(stage.style.width).toBe("480px");
  rerender({ zoom: "custom", scale: 150 });
  expect(stage.style.width).toBe("1500px");
  expect(stage.style.height).toBe("1875px");
  expect(scroll).toHaveBeenLastCalledWith({ top: 0, left: 0 });
});

test("hover switches cards only after a rest, and entering the card cancels the switch", () => {
  const { onSelect } = open();
  load();
  hover(first());
  expect(onSelect).toHaveBeenLastCalledWith("First-line");
  hover(second());
  tick(249);
  expect(screen.getByRole("dialog", { name: "Card 1" })).toBeTruthy();
  hover(screen.getByRole("dialog", { name: "Card 1" }));
  tick(251);
  expect(screen.queryByRole("dialog", { name: "Card 2" })).toBeNull();
  hover(second());
  tick(250);
  expect(screen.getByRole("dialog", { name: "Card 2" })).toBeTruthy();
  expect(onSelect).toHaveBeenLastCalledWith("Second-line");
});

test("leaving closes a hover card after a grace period, while a pinned card keeps its selection", () => {
  const { onSelect } = open();
  load();
  hover(first());
  leave(first());
  tick(179);
  expect(screen.getByRole("dialog")).toBeTruthy();
  tick(1);
  expect(screen.queryByRole("dialog")).toBeNull();
  fireEvent.click(first());
  hover(second());
  leave(second());
  tick(500);
  expect(screen.getByRole("dialog", { name: "Card 1" })).toBeTruthy();
  expect(onSelect).not.toHaveBeenCalledWith("Second-line");
  fireEvent.click(first());
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("touch waits for a click, Escape dismisses, and portal presses preserve the card", () => {
  open();
  const stage = load();
  fireEvent.pointerEnter(first(), { pointerType: "touch" });
  expect(screen.queryByRole("dialog")).toBeNull();
  fireEvent.click(first());
  fireEvent.pointerDown(screen.getByRole("dialog"));
  expect(screen.getByRole("dialog")).toBeTruthy();
  fireEvent.keyDown(window, { key: "Escape" });
  expect(screen.queryByRole("dialog")).toBeNull();
  fireEvent.click(first());
  fireEvent.pointerDown(stage);
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("keeping a hover card pins it, and editing closes it and disables region actions", () => {
  const { rerender } = open();
  load();
  hover(first());
  fireEvent.click(screen.getByRole("button", { name: "Keep card" }));
  hover(second());
  tick(500);
  expect(screen.getByRole("dialog", { name: "Card 1" })).toBeTruthy();
  rerender({ editing: true });
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(first().disabled).toBe(true);
});

test("study-panel hover selects immediately without rendering a card", () => {
  const { onSelect } = open({ cards: false });
  load();
  hover(first());
  hover(second());
  expect(onSelect.mock.calls).toEqual([["First-line"], ["Second-line"]]);
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("split regions select the current sentence but anchor only one block card", () => {
  const block = {
    ...box("Block", "p1", [firstRegion, { ...firstRegion, y: 0.6 }]),
    lines: [
      { id: "a", source: "One" },
      { id: "b", source: "Two" },
    ],
  };
  const { onSelect } = open({ boxes: [block], selectedId: "b" });
  load();
  const regions = screen.getAllByRole("button", {
    name: "Text block 1: 2 sentences",
  });
  fireEvent.click(regions[1]);
  expect(onSelect).toHaveBeenCalledWith("b");
  expect(screen.getAllByRole("dialog")).toHaveLength(1);
});

test("drawing normalizes coordinates, ignores tiny drags and sends the page id", () => {
  const { onDraw } = open({ annotate: true });
  const stage = load();
  drag(stage, [400, 700], [160, 500]);
  expect(onDraw).toHaveBeenCalledWith(
    { x: 0.2, y: 0.5, width: 0.3, height: 0.2 },
    "p1",
  );
  drag(stage, [100, 100], [101, 101]);
  expect(onDraw).toHaveBeenCalledOnce();
  expect(stage.querySelector(".comic-draft-box")).toBeNull();
});

test.each([
  { annotate: false, editing: false },
  { annotate: true, editing: true },
])("drawing and placement respect interaction locks (%j)", (mode) => {
  const { onDraw, onPlacement } = open(mode);
  const stage = load();
  drag(stage, [100, 100], [400, 400]);
  drag(first(), [100, 100], [400, 400]);
  expect(onDraw).not.toHaveBeenCalled();
  expect(onPlacement).not.toHaveBeenCalled();
});

test("moving and resizing selected boxes preview normalized geometry and save their placement", () => {
  const { onPlacement, onDraw } = open({ annotate: true });
  load();
  drag(first(), [80, 200], [160, 300]);
  expect(onPlacement).toHaveBeenLastCalledWith(boxes[0], [
    { x: 0.2, y: 0.3, width: 0.3, height: 0.2 },
  ]);
  const corner = first().querySelectorAll(".comic-region-handle")[3];
  drag(corner, [320, 400], [400, 500]);
  expect(onPlacement).toHaveBeenLastCalledWith(boxes[0], [
    { x: 0.1, y: 0.2, width: 0.4, height: 0.3 },
  ]);
  expect(onDraw).not.toHaveBeenCalled();
});

test.each(["Escape", "editing", "annotate", "unmount"])(
  "pending hover switches are cancelled by %s",
  (action) => {
    const { onSelect, rerender, unmount } = open();
    load();
    hover(first());
    hover(second());
    if (action === "Escape") fireEvent.keyDown(window, { key: "Escape" });
    else if (action === "unmount") unmount();
    else rerender({ [action]: true });
    tick(500);
    expect(onSelect.mock.calls).toEqual([["First-line"]]);
    expect(screen.queryByRole("dialog")).toBeNull();
  },
);

test.each(["pointercancel", "editing", "annotate", "unmount"])(
  "unfinished drawings are discarded on %s instead of saving after the page is gone or locked",
  (action) => {
    const { onDraw, rerender, unmount } = open({ annotate: true });
    const stage = load();
    fireEvent.pointerDown(stage, { button: 0, clientX: 100, clientY: 100 });
    fireEvent.pointerMove(window, { clientX: 400, clientY: 400 });
    expect(stage.querySelector(".comic-draft-box")).toBeTruthy();
    if (action === "pointercancel") fireEvent.pointerCancel(window);
    else if (action === "unmount") unmount();
    else rerender({ [action]: action === "editing" });
    fireEvent.pointerMove(window, { clientX: 600, clientY: 600 });
    fireEvent.pointerUp(window);
    expect(onDraw).not.toHaveBeenCalled();
    expect(
      screen.queryByRole("region")?.querySelector(".comic-draft-box"),
    ).toBeFalsy();
  },
);

test("a cancelled box move never publishes placement", () => {
  const { onPlacement } = open({ annotate: true });
  load();
  fireEvent.pointerDown(first(), { button: 0, clientX: 100, clientY: 200 });
  fireEvent.pointerMove(window, { clientX: 400, clientY: 400 });
  fireEvent.pointerCancel(window);
  fireEvent.pointerUp(window);
  expect(onPlacement).not.toHaveBeenCalled();
});

test("bare comics render their page images without study or annotation controls", () => {
  render(
    <MantineProvider env="test">
      <ComicPageView
        document={{ title: "Bare comic", pages }}
        pages={[pages[1]]}
        api="/api/raw"
        zoom="page"
        scale={100}
        boxes={[]}
        rtl={false}
      />
    </MantineProvider>,
  );
  const image = screen.getByRole("img", { name: "Bare comic, page 2" });
  expect(image.getAttribute("src")).toBe("/api/raw/pages/p2?attempt=0");
  load("Bare comic, page 2");
  expect(screen.queryByLabelText("Loading page")).toBeNull();
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.queryByRole("button")).toBeNull();
});

test.each([0, 1])(
  "moving split region %i preserves its sibling and preview",
  (index) => {
    const regions = [firstRegion, { x: 0.5, y: 0.5, width: 0.2, height: 0.2 }];
    const split = box("First", "p1", regions);
    const { onPlacement } = open({ annotate: true, boxes: [split] });
    load();
    const buttons = screen.getAllByRole("button", { name: "Bubble 1: First" });
    fireEvent.pointerDown(buttons[index], {
      button: 0,
      clientX: 80,
      clientY: 200,
    });
    fireEvent.pointerMove(window, { clientX: 160, clientY: 300 });
    expect(buttons[1 - index].style.left).toBe(
      `${regions[1 - index].x * 100}%`,
    );
    fireEvent.pointerUp(window);
    expect(onPlacement).toHaveBeenCalledWith(
      split,
      regions.map((region, i) =>
        i === index
          ? {
              ...region,
              x: Number((region.x + 0.1).toFixed(6)),
              y: Number((region.y + 0.1).toFixed(6)),
            }
          : region,
      ),
    );
  },
);

test("resizing a split bubble preserves its other region", () => {
  const other = { x: 0.5, y: 0.5, width: 0.2, height: 0.2 };
  const split = box("First", "p1", [firstRegion, other]);
  const { onPlacement } = open({ annotate: true, boxes: [split] });
  load();
  const button = screen.getAllByRole("button", { name: "Bubble 1: First" })[0];
  drag(
    button.querySelectorAll(".comic-region-handle")[3],
    [320, 400],
    [400, 500],
  );
  expect(onPlacement).toHaveBeenCalledWith(split, [
    { ...firstRegion, width: 0.4, height: 0.3 },
    other,
  ]);
});
