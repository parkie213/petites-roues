// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import {
  act,
  render,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { PlaybackToolbar } from "../src/player/components/PlaybackToolbar.tsx";
import { WatchBar } from "../src/player/components/WatchBar.tsx";
import { useBareVideo } from "../src/player/useBareVideo.ts";
import { usePhraseHelp } from "../src/player/usePhraseHelp.ts";

const inMantine = (ui) => render(<MantineProvider>{ui}</MantineProvider>);

test("the bar offers Quiz me and line fixes for a title", async () => {
  const onQuiz = vi.fn();
  const onEditLine = vi.fn();
  inMantine(
    <WatchBar
      title="Peppa Pig · S01 E01"
      warnings={[]}
      returnPath="/#/library"
      study={{ quizOpen: false, onQuiz, onEditLine }}
    />,
  );
  expect(screen.getByRole("link", { name: "Back to library" })).toHaveProperty(
    "hash",
    "#/library",
  );
  await userEvent.click(screen.getByRole("button", { name: "Quiz me" }));
  expect(onQuiz).toHaveBeenCalledOnce();
  await userEvent.click(screen.getByRole("button", { name: "More options" }));
  // The menu opens with a transition.
  await userEvent.click(
    await screen.findByRole("menuitem", {
      name: "Fix or comment on this line",
    }),
  );
  expect(onEditLine).toHaveBeenCalledOnce();
});

test("a video without a document shows its warnings and no study tools", () => {
  inMantine(
    <WatchBar
      title="Video"
      warnings={["No playable audio"]}
      returnPath="/"
      study={null}
    />,
  );
  expect(screen.getByText("No playable audio")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Quiz me" })).toBeNull();
  expect(screen.queryByRole("button", { name: "More options" })).toBeNull();
});

function renderToolbar(props) {
  const video = document.createElement("video");
  const onToggle = vi.fn();
  inMantine(
    <PlaybackToolbar
      videoRef={{ current: video }}
      containerRef={{ current: document.body }}
      time={0}
      playing={false}
      onToggle={onToggle}
      onSeek={() => {}}
      autoPause={null}
      status=""
      onError={() => {}}
      {...props}
    />,
  );
  return onToggle;
}

test("the toolbar's play button names what it will do", async () => {
  const onToggle = renderToolbar({ playing: true });
  await userEvent.click(screen.getByRole("button", { name: "Pause" }));
  expect(onToggle).toHaveBeenCalledOnce();
});

test("auto-pause is offered only where there are lines to pause after", async () => {
  const onChange = vi.fn();
  renderToolbar({ autoPause: { checked: false, onChange } });
  await userEvent.click(screen.getByRole("switch", { name: "Auto-pause" }));
  expect(onChange).toHaveBeenCalledWith(true);
});

test("a video without lines has no auto-pause, and the status is said", () => {
  renderToolbar({ status: "The browser blocked playback" });
  expect(screen.queryByRole("switch", { name: "Auto-pause" })).toBeNull();
  expect(screen.getByRole("status").textContent).toBe(
    "The browser blocked playback",
  );
});

test("phrase help counts as open while any subtitle has it open", () => {
  const { result } = renderHook(() => usePhraseHelp());
  expect(result.current.phraseHelpOpen).toBe(false);
  act(() => result.current.setPhraseHelp("a", true));
  act(() => result.current.setPhraseHelp("b", true));
  act(() => result.current.setPhraseHelp("a", false));
  expect(result.current.phraseHelpOpen).toBe(true);
  act(() => result.current.setPhraseHelp("b", false));
  expect(result.current.phraseHelpOpen).toBe(false);
});

test("a video without a document is named from its path, with its warnings", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () =>
      Response.json({
        repositories: [
          {
            media: [
              {
                fingerprint: "f1",
                series: "Peppa Pig",
                title: "s01e03",
                warnings: ["No playable audio"],
              },
            ],
          },
        ],
      }),
    ),
  );
  const { result } = renderHook(() => useBareVideo("f1"));
  await waitFor(() => expect(result.current.media).not.toBeNull());
  expect(result.current.media).toEqual({
    title: "Peppa Pig · s01e03",
    cues: [],
    warnings: ["No playable audio"],
  });
  expect(document.title).toBe("Petites Roues · Peppa Pig · s01e03");
});

test("a library that cannot be read is the bare video's error", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response("Down for maintenance", { status: 503 })),
  );
  const { result } = renderHook(() => useBareVideo("f1"));
  await waitFor(() => expect(result.current.error).not.toBe(""));
  expect(result.current.media).toBeNull();
});
