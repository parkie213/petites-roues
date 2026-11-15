// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { VideoQuizPanel } from "../src/player/components/VideoQuizPanel.jsx";
import { prepareVideoQuiz } from "../src/player/video-quiz.js";

vi.mock("../src/player/video-quiz.js", () => ({ prepareVideoQuiz: vi.fn() }));

const types = [
  { id: "to-source", label: "Translate into French" },
  { id: "tenses", label: "Verb tenses" },
];
const reply = (fields = {}) => ({
  types,
  type: "to-source",
  scope: { kind: "range", start: "50:00", end: "60:00" },
  valid: true,
  lineCount: 212,
  large: false,
  largeAbove: 400,
  text: "# Subtitle quiz: Le Film\n",
  fileName: "quiz-le-film.md",
  ...fields,
});
const deferred = () => {
  let resolve;
  const promise = new Promise((yes) => (resolve = yes));
  return { promise, resolve };
};
const video = { current: { currentTime: 3600.4, duration: 5400 } };

function mount() {
  return render(
    <MantineProvider env="test">
      <VideoQuizPanel id="film" video={video} time={3600} onClose={() => {}} />
    </MantineProvider>,
  );
}
const chatGpt = () => screen.getByRole("button", { name: /ChatGPT/ });
const switchToRange = () =>
  fireEvent.click(screen.getByRole("radio", { name: "Time range" }));

beforeEach(() => {
  localStorage.clear();
  prepareVideoQuiz.mockReset();
  video.current.currentTime = 3600.4;
});

test("asks first with no scope so the desktop's default applies, and shows it", async () => {
  prepareVideoQuiz.mockResolvedValue(reply());
  mount();
  expect((await screen.findByText(/212 subtitle lines/)).textContent).toContain(
    "50:00 – 60:00",
  );
  expect(prepareVideoQuiz).toHaveBeenCalledTimes(1);
  expect(prepareVideoQuiz).toHaveBeenCalledWith("film", {
    position: 3600.4,
    duration: 5400,
    type: "to-source",
  });
  expect(screen.getByRole("radio", { name: "Time range" }).checked).toBe(true);
  expect(screen.getByLabelText("Start").value).toBe("50:00");
  expect(screen.getByLabelText("End").value).toBe("60:00");
  expect(screen.getByRole("button", { name: "Verb tenses" })).toBeTruthy();
  await waitFor(() => expect(chatGpt().disabled).toBe(false));
});

test("exports stay disabled until the reply for the current selection arrives", async () => {
  prepareVideoQuiz.mockResolvedValueOnce(reply());
  mount();
  await waitFor(() => expect(chatGpt().disabled).toBe(false));
  const second = deferred();
  prepareVideoQuiz.mockReturnValueOnce(second.promise);
  fireEvent.click(screen.getByRole("button", { name: "Verb tenses" }));
  expect(chatGpt().disabled).toBe(true);
  second.resolve(reply({ type: "tenses", text: "# tenses\n" }));
  await waitFor(() => expect(chatGpt().disabled).toBe(false));
  expect(prepareVideoQuiz).toHaveBeenLastCalledWith(
    "film",
    expect.objectContaining({ type: "tenses", position: 3600.4 }),
  );
});

test("a reply for an older selection is ignored", async () => {
  prepareVideoQuiz.mockResolvedValueOnce(reply());
  mount();
  await screen.findByText(/212 subtitle lines/);
  const old = deferred();
  const current = deferred();
  prepareVideoQuiz
    .mockReturnValueOnce(old.promise)
    .mockReturnValueOnce(current.promise);
  fireEvent.click(screen.getByRole("button", { name: "Verb tenses" }));
  await waitFor(() => expect(prepareVideoQuiz).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByRole("radio", { name: "Whole title" }));
  await waitFor(() => expect(prepareVideoQuiz).toHaveBeenCalledTimes(3));
  current.resolve(
    reply({
      type: "tenses",
      scope: { kind: "whole", start: "0:00", end: "90:00" },
      lineCount: 900,
      large: true,
    }),
  );
  await screen.findByText(/900 subtitle lines/);
  old.resolve(reply({ lineCount: 5, text: "old" }));
  await new Promise((done) => setTimeout(done, 20));
  expect(screen.queryByText(/5 subtitle lines/)).toBeNull();
  expect(screen.getByText(/900 subtitle lines/)).toBeTruthy();
});

test("a long brief gets a warning", async () => {
  prepareVideoQuiz.mockResolvedValue(reply({ lineCount: 901, large: true }));
  mount();
  expect(await screen.findByText(/More than 400 lines/)).toBeTruthy();
  await waitFor(() => expect(chatGpt().disabled).toBe(false));
});

test("an unreadable range shows an alert and cannot be exported", async () => {
  prepareVideoQuiz.mockResolvedValueOnce(reply());
  mount();
  await screen.findByText(/212 subtitle lines/);
  prepareVideoQuiz.mockResolvedValue(
    reply({ valid: false, lineCount: 0, text: null, fileName: null }),
  );
  fireEvent.change(screen.getByLabelText("End"), { target: { value: "x" } });
  expect(chatGpt().disabled).toBe(true);
  expect((await screen.findByRole("alert")).textContent).toContain("m:ss");
  await waitFor(() =>
    expect(prepareVideoQuiz).toHaveBeenLastCalledWith(
      "film",
      expect.objectContaining({ end: "x" }),
    ),
  );
  expect(chatGpt().disabled).toBe(true);
});

test("typing is debounced and the playhead buttons round the end up", async () => {
  prepareVideoQuiz.mockResolvedValue(reply());
  mount();
  await screen.findByText(/212 subtitle lines/);
  const start = screen.getByLabelText("Start");
  fireEvent.change(start, { target: { value: "1" } });
  fireEvent.change(start, { target: { value: "1:" } });
  fireEvent.change(start, { target: { value: "1:05" } });
  expect(prepareVideoQuiz).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(prepareVideoQuiz).toHaveBeenCalledTimes(2));
  expect(prepareVideoQuiz).toHaveBeenLastCalledWith(
    "film",
    expect.objectContaining({ start: "1:05" }),
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Set quiz start to playhead" }),
  );
  expect(start.value).toBe("1:00:00");
  fireEvent.click(
    screen.getByRole("button", { name: "Set quiz end to playhead" }),
  );
  expect(screen.getByLabelText("End").value).toBe("1:00:01");
});

test("a failed request is reported", async () => {
  prepareVideoQuiz.mockRejectedValue(new Error("offline"));
  mount();
  expect((await screen.findByRole("alert")).textContent).toContain("offline");
  expect(chatGpt().disabled).toBe(true);
});

test("whole title keeps the typed range for switching back", async () => {
  prepareVideoQuiz.mockResolvedValue(reply());
  mount();
  await screen.findByText(/212 subtitle lines/);
  fireEvent.click(screen.getByRole("radio", { name: "Whole title" }));
  await waitFor(() =>
    expect(prepareVideoQuiz).toHaveBeenLastCalledWith(
      "film",
      expect.objectContaining({ scope: "whole" }),
    ),
  );
  switchToRange();
  expect(screen.getByLabelText("Start").value).toBe("50:00");
});
