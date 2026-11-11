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
import { useRef, useState } from "react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { StudyLanguagesProvider } from "../src/player/StudyLanguages.jsx";
import { SubtitleEditPanel } from "../src/player/components/SubtitleEditPanel.jsx";

/**
 * The subtitle editor docked beside the video, against an API kept in memory and a <video> whose
 * playhead the test sets. The subtitle-edit journey covers timing; these cover the rest.
 */

const API = "/api/titles/peppa";

/**
 * A <video> that jsdom can't play: playing and pausing only flip its state and fire events, which
 * come a task later, as a browser queues them.
 */
function fakeVideo(duration = 60) {
  const video = document.createElement("video");
  let currentTime = 0;
  let paused = true;
  Object.defineProperties(video, {
    currentTime: {
      get: () => currentTime,
      set: (seconds) => {
        currentTime = seconds;
      },
    },
    duration: { get: () => duration },
    paused: { get: () => paused },
    seeking: { get: () => false },
  });
  video.play = vi.fn(async () => {
    paused = false;
    setTimeout(() => video.dispatchEvent(new Event("play")));
  });
  video.pause = vi.fn(() => {
    if (paused) return;
    paused = true;
    setTimeout(() => video.dispatchEvent(new Event("pause")));
  });
  return video;
}

/**
 * The title's lines held in memory, answering each edit with the whole document as the server
 * does. Every request is kept, with its parsed body.
 */
function fakeApi(lines) {
  let cues = lines.map((line) => ({ ...line }));
  let added = 0;
  const requests = [];
  const document = () => ({
    id: "peppa",
    title: "Peppa",
    sourceLanguage: "fr",
    translationLanguage: "en",
    notesLanguage: "en",
    video: {
      cues: cues.map(({ start, end, ...line }) => ({ start, end, line })),
    },
  });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options = {}) => {
      const method = options.method ?? "GET";
      const body = options.body ? JSON.parse(options.body) : undefined;
      requests.push({ method, url, body });
      const path = url.slice(API.length);
      const id = decodeURIComponent(path.split("/")[2] ?? "");
      if (path === "/timings") {
        for (const time of body)
          cues = cues.map((cue) =>
            cue.id === time.id
              ? { ...cue, start: time.start, end: time.end }
              : cue,
          );
      } else if (method === "POST" && path === "/cues") {
        cues = [...cues, { ...body, id: `new-${++added}` }].sort(
          (a, b) => a.start - b.start,
        );
      } else if (path.endsWith("/split")) {
        cues = cues.flatMap((cue) =>
          cue.id === id
            ? [
                { ...cue, id: `${id}-1`, end: body.at, source: body.first },
                { ...cue, id: `${id}-2`, start: body.at, source: body.second },
              ]
            : [cue],
        );
      } else if (path.endsWith("/comment")) {
        // A comment changes nothing a test reads.
      } else if (method === "DELETE") {
        cues = cues.filter((cue) => cue.id !== id);
      } else if (method === "PUT") {
        cues = cues.map((cue) =>
          cue.id === id
            ? {
                ...cue,
                source: body.source,
                translation: body.translation,
                start: body.start,
                end: body.end,
              }
            : cue,
        );
      }
      return Response.json(document());
    }),
  );
  return { requests, edits: () => requests.filter((r) => r.method !== "GET") };
}

const lines = [
  { id: "a", start: 1, end: 3, source: "Bonjour.", translation: "Hello." },
  {
    id: "b",
    start: 3,
    end: 6,
    source: "Bonjour tout le monde.",
    translation: "Hello everyone.",
  },
  { id: "c", start: 8, end: 10, source: "Au revoir.", translation: "Goodbye." },
];

/** The panel inside the player's bits it needs, keeping the cues it saves as Watch does. */
function Editor({ video, start, onClose, onPreview }) {
  const [cues, setCues] = useState(lines);
  const videoRef = useRef(video);
  const heldTime = useRef(-1);
  const closeRef = useRef(null);
  return (
    <MantineProvider>
      <StudyLanguagesProvider
        document={{ sourceLanguage: "fr", translationLanguage: "en" }}
      >
        <SubtitleEditPanel
          api={API}
          cue={start ? cues.find((cue) => cue.id === start) : null}
          cues={cues}
          video={videoRef}
          heldTime={heldTime}
          seek={(seconds) => {
            video.currentTime = seconds;
          }}
          play={() => video.play()}
          pause={() => video.pause()}
          onSaved={(saved) => setCues(saved.cues)}
          onClose={onClose}
          onPreview={onPreview}
          closeRef={closeRef}
        />
      </StudyLanguagesProvider>
    </MantineProvider>
  );
}

let video, api, onClose, onPreview;

beforeEach(() => {
  video = fakeVideo();
  api = fakeApi(lines);
  onClose = vi.fn();
  onPreview = vi.fn();
  // jsdom has no canvas; the timeline draws nothing without one.
  HTMLCanvasElement.prototype.getContext = () => null;
});

afterEach(() => vi.useRealTimers());

function open(start = "b") {
  render(
    <Editor
      video={video}
      start={start}
      onClose={onClose}
      onPreview={onPreview}
    />,
  );
  return {
    editor: screen.getByRole("complementary", { name: "Subtitle editor" }),
    french: () => screen.getByLabelText("French"),
    translation: () => screen.getByLabelText("Translation"),
    comment: () => screen.getByLabelText("Comment for review"),
    save: () => screen.getByRole("button", { name: "Save changes" }),
  };
}

test("saving text and a comment queues both, and the editor stays on the line", async () => {
  const panel = open();
  await userEvent.clear(panel.translation());
  await userEvent.type(panel.translation(), "Hi everyone.");
  await userEvent.type(panel.comment(), "Check the greeting");
  await userEvent.click(panel.save());

  await waitFor(() => expect(panel.comment().value).toBe(""));
  expect(api.edits().map((r) => `${r.method} ${r.url}`)).toEqual([
    `PUT ${API}/cues/b`,
    `POST ${API}/cues/b/comment`,
  ]);
  expect(api.edits()[0].body).toMatchObject({
    source: "Bonjour tout le monde.",
    translation: "Hi everyone.",
    start: 3,
    end: 6,
  });
  expect(api.edits()[1].body).toEqual({ text: "Check the greeting" });
  expect(panel.translation().value).toBe("Hi everyone.");
  expect(panel.save().disabled).toBe(true);
  expect(onClose).not.toHaveBeenCalled();
});

test("a comment alone is saved without changing the line", async () => {
  const panel = open();
  await userEvent.type(panel.comment(), "Is this right?");
  await userEvent.click(panel.save());
  await waitFor(() => expect(panel.comment().value).toBe(""));
  expect(api.edits().map((r) => `${r.method} ${r.url}`)).toEqual([
    `POST ${API}/cues/b/comment`,
  ]);
});

test("Ctrl+Enter saves and Escape closes", async () => {
  const panel = open();
  await userEvent.type(panel.translation(), "!");
  await userEvent.keyboard("{Control>}{Enter}{/Control}");
  await waitFor(() => expect(panel.save().disabled).toBe(true));
  expect(api.edits()).toHaveLength(1);

  await userEvent.keyboard("{Escape}");
  expect(onClose).toHaveBeenCalledOnce();
});

test("closing with unsaved changes asks first, and stays when told to", async () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  const panel = open();
  await userEvent.type(panel.translation(), "!");
  await userEvent.click(
    screen.getByRole("button", { name: "Close subtitle editor" }),
  );
  expect(confirm).toHaveBeenCalledWith("Discard unsaved subtitle changes?");
  expect(onClose).not.toHaveBeenCalled();

  confirm.mockReturnValue(true);
  await userEvent.click(screen.getByRole("button", { name: "Close" }));
  expect(onClose).toHaveBeenCalledOnce();
});

test("a line with no text can't be saved", async () => {
  const panel = open();
  await userEvent.clear(panel.french());
  await userEvent.click(panel.save());
  expect(screen.getByRole("alert").textContent).toBe(
    "Enter French text and valid Start / End times within the video.",
  );
  expect(api.edits()).toEqual([]);
});

test("adding at the playhead creates a line there, then edits it", async () => {
  video.currentTime = 6.5;
  const panel = open();
  await userEvent.click(
    screen.getByRole("button", { name: "Add at playhead" }),
  );
  expect(screen.getByRole("heading", { name: /Add subtitle/ })).toBeTruthy();
  // Two seconds long, unless the next line starts sooner.
  expect(screen.getByRole("button", { name: "start time" }).textContent).toBe(
    "0:06.500",
  );
  expect(screen.getByRole("button", { name: "end time" }).textContent).toBe(
    "0:08.000",
  );
  await userEvent.type(panel.french(), "Et voilà.");
  await userEvent.click(panel.save());

  await screen.findByRole("heading", { name: /Edit subtitle/ });
  expect(api.edits()).toEqual([
    {
      method: "POST",
      url: `${API}/cues`,
      body: expect.objectContaining({
        source: "Et voilà.",
        start: 6.5,
        end: 8,
      }),
    },
  ]);
  expect(panel.french().value).toBe("Et voilà.");
});

test("without a line at the playhead the editor opens on Add", () => {
  open(null);
  expect(screen.getByRole("heading", { name: /Add subtitle/ })).toBeTruthy();
  expect(screen.getByRole("button", { name: "Edit" }).disabled).toBe(true);
});

test("splitting at the text cursor and the playhead edits the first part", async () => {
  const panel = open();
  video.currentTime = 4.5;
  // The cursor after "Bonjour": React reads it from the focused field after a key.
  panel.french().focus();
  panel.french().setSelectionRange(7, 7);
  fireEvent.keyUp(panel.french());
  await userEvent.click(
    screen.getByRole("button", { name: /Split at cursor/ }),
  );

  await waitFor(() => expect(panel.french().value).toBe("Bonjour"));
  expect(api.edits()).toEqual([
    {
      method: "POST",
      url: `${API}/cues/b/split`,
      body: {
        at: 4.5,
        first: "Bonjour",
        second: "tout le monde.",
        start: 3,
        end: 6,
      },
    },
  ]);
  expect(screen.getByRole("button", { name: "end time" }).textContent).toBe(
    "0:04.500",
  );
});

test("a translation not yet saved has to be saved before splitting", async () => {
  const panel = open();
  video.currentTime = 4.5;
  await userEvent.type(panel.translation(), "!");
  await userEvent.click(
    screen.getByRole("button", { name: /Split at cursor/ }),
  );
  expect(screen.getByRole("alert").textContent).toBe(
    "Save your translation or comment before splitting.",
  );
  expect(api.edits()).toEqual([]);
});

test("deleting asks first, then edits the line after", async () => {
  const panel = open();
  await userEvent.click(screen.getByRole("button", { name: "Delete" }));
  await userEvent.click(screen.getByRole("button", { name: "Keep subtitle" }));
  expect(screen.queryByRole("button", { name: "Confirm delete" })).toBeNull();

  await userEvent.click(screen.getByRole("button", { name: "Delete" }));
  await userEvent.click(screen.getByRole("button", { name: "Confirm delete" }));
  await waitFor(() => expect(panel.french().value).toBe("Au revoir."));
  expect(api.edits()).toEqual([
    { method: "DELETE", url: `${API}/cues/b`, body: undefined },
  ]);
  expect(video.currentTime).toBe(8);
});

test("deleting the only line left closes the editor", async () => {
  fakeApi([lines[1]]);
  api = { edits: () => [] };
  render(
    <Editor video={video} start="b" onClose={onClose} onPreview={onPreview} />,
  );
  await userEvent.click(screen.getByRole("button", { name: "Delete" }));
  await userEvent.click(screen.getByRole("button", { name: "Confirm delete" }));
  await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
});

test("a preview plays the line and pauses at its end", async () => {
  open();
  await userEvent.click(screen.getByRole("button", { name: "Preview line" }));
  expect(video.currentTime).toBe(3);
  expect(video.play).toHaveBeenCalled();
  expect(onPreview).toHaveBeenLastCalledWith(true);

  // The panel watches each frame for the preview's end.
  act(() => {
    video.currentTime = 6.2;
  });
  await waitFor(() => expect(onPreview).toHaveBeenLastCalledWith(false));
  expect(video.paused).toBe(true);
  expect(video.currentTime).toBeCloseTo(5.999, 3);
});

test("the timing keys nudge the chosen end and set it to the playhead", async () => {
  open();
  const end = screen.getByRole("button", { name: "end time" });
  await userEvent.click(end);
  await userEvent.keyboard("{Shift>}{ArrowRight}{/Shift}");
  expect(end.textContent).toBe("0:06.100");
  video.currentTime = 5.25;
  await userEvent.keyboard("[[");
  expect(screen.getByRole("button", { name: "start time" }).textContent).toBe(
    "0:05.250",
  );
});
