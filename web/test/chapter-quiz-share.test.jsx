// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { QuizShare } from "../src/player/components/QuizShare.jsx";

// jsdom has no layout scrolling; Mantine scrolls the selected chapter into view.
Element.prototype.scrollIntoView ??= function () {};

const doc = {
  title: "Comic",
  sourceLanguage: "fr",
  translationLanguage: "en",
  notesLanguage: "en",
};
const scopes = {
  label: "Chapter",
  initial: "0",
  options: [
    { value: "0", label: "First chapter" },
    { value: "1", label: "Second chapter" },
  ],
};
function mount(prepare) {
  localStorage.clear();
  render(
    <MantineProvider env="test">
      <QuizShare doc={doc} scopes={scopes} prepare={prepare} />
    </MantineProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: "Quiz me" }));
}
function deferred() {
  let resolve;
  const promise = new Promise((yes) => {
    resolve = yes;
  });
  return { promise, resolve };
}

test("exports only the current prepared selection and keeps clipboard work in the click", async () => {
  const first = deferred();
  const second = deferred();
  const prepare = vi
    .fn()
    .mockReturnValueOnce(first.promise)
    .mockReturnValueOnce(second.promise);
  const writeText = vi.fn().mockResolvedValue();
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: { writeText },
  });
  mount(prepare);
  await waitFor(() => expect(prepare).toHaveBeenCalledTimes(1));
  expect(
    screen.getByRole("button", { name: "Copy", exact: true }).disabled,
  ).toBe(true);
  fireEvent.click(screen.getByRole("combobox", { name: "Chapter" }));
  fireEvent.click(
    await screen.findByRole("option", { name: "Second chapter" }),
  );
  await waitFor(() => expect(prepare).toHaveBeenCalledTimes(2));
  expect(prepare.mock.calls[0][3].aborted).toBe(true);
  expect(prepare.mock.calls[1][2]).toBe("1");
  await act(async () =>
    second.resolve({ text: "# Second chapter", fileName: "second.md" }),
  );
  await act(async () =>
    first.resolve({ text: "# STALE first chapter", fileName: "first.md" }),
  );
  expect(writeText).not.toHaveBeenCalled();
  const copy = screen.getByRole("button", { name: "Copy", exact: true });
  expect(copy.disabled).toBe(false);
  fireEvent.click(copy);
  expect(writeText).toHaveBeenCalledWith("# Second chapter");
  await screen.findByRole("status");
});

test("failed preparation disables export and can be retried", async () => {
  const retry = deferred();
  const prepare = vi
    .fn()
    .mockRejectedValueOnce(new Error("Offline"))
    .mockReturnValueOnce(retry.promise);
  mount(prepare);
  await screen.findByRole("alert");
  expect(
    screen.getByRole("button", { name: "Download", exact: true }).disabled,
  ).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  await waitFor(() => expect(prepare).toHaveBeenCalledTimes(2));
  await act(async () =>
    retry.resolve({ text: "# Ready", fileName: "ready.md" }),
  );
  expect(
    screen.getByRole("button", { name: "Download", exact: true }).disabled,
  ).toBe(false);
});
