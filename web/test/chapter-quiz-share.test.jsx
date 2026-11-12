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
function mount(prepare, overrides = {}) {
  localStorage.clear();
  const view = render(
    <MantineProvider env="test">
      <QuizShare doc={doc} scopes={scopes} prepare={prepare} {...overrides} />
    </MantineProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: "Quiz me" }));
  return view;
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

beforeEach(() => {
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: { writeText: vi.fn().mockResolvedValue() },
  });
});

test("closing aborts preparation and reopening requires a fresh result", async () => {
  const pending = deferred();
  const prepare = vi
    .fn()
    .mockResolvedValueOnce({ text: "Old quiz", fileName: "old.md" })
    .mockReturnValueOnce(pending.promise);
  mount(prepare);
  const copy = () => screen.getByRole("button", { name: "Copy", exact: true });
  await waitFor(() => expect(copy().disabled).toBe(false));
  fireEvent.keyDown(document.body, { key: "Escape" });
  expect(prepare.mock.calls[0][3].aborted).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Quiz me" }));
  expect(copy().disabled).toBe(true);
  await waitFor(() => expect(prepare).toHaveBeenCalledTimes(2));
  await act(async () =>
    pending.resolve({ text: "New quiz", fileName: "new.md" }),
  );
  fireEvent.click(copy());
  expect(navigator.clipboard.writeText).toHaveBeenCalledWith("New quiz");
});

test("changing type aborts the old quiz and unmount aborts the replacement", async () => {
  const prepare = vi.fn(() => new Promise(() => {}));
  const { unmount } = mount(prepare);
  await waitFor(() => expect(prepare).toHaveBeenCalledOnce());
  fireEvent.click(screen.getByRole("radio", { name: "Comprehension" }));
  await waitFor(() => expect(prepare).toHaveBeenCalledTimes(2));
  expect(prepare.mock.calls[0][3].aborted).toBe(true);
  expect(prepare.mock.calls[1][1].id).toBe("comprehension");
  unmount();
  expect(prepare.mock.calls[1][3].aborted).toBe(true);
});

test("a synchronous preparation error can be retried", async () => {
  const prepare = vi
    .fn()
    .mockImplementationOnce(() => {
      throw Error("Offline");
    })
    .mockResolvedValue({ text: "Recovered", fileName: "ready.md" });
  mount(prepare);
  await screen.findByRole("alert");
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "Copy", exact: true }).disabled,
    ).toBe(false),
  );
  expect(prepare).toHaveBeenCalledTimes(2);
});

test("copy and chat opening happen in the click even while the clipboard promise is pending", async () => {
  const clipboard = deferred();
  navigator.clipboard.writeText.mockReturnValue(clipboard.promise);
  const open = vi.spyOn(window, "open").mockImplementation(() => null);
  mount(undefined, {
    withTypes: false,
    brief: () => "General quiz",
    fileName: () => "general.md",
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Copy & open ChatGPT ↗" }),
  );
  expect(navigator.clipboard.writeText).toHaveBeenCalledWith("General quiz");
  expect(open).toHaveBeenCalledWith(
    "https://chatgpt.com/",
    "_blank",
    "noopener",
  );
  await act(async () => clipboard.resolve());
  expect((await screen.findByRole("status")).textContent).toContain(
    "Copied: paste it into the new ChatGPT chat",
  );
});

test("clipboard refusal is visible without changing the chosen quiz", async () => {
  navigator.clipboard.writeText.mockRejectedValue(Error("Denied"));
  mount(undefined, {
    withTypes: false,
    brief: () => "Quiz",
    fileName: () => "quiz.md",
  });
  fireEvent.click(screen.getByRole("button", { name: "Copy", exact: true }));
  expect((await screen.findByRole("status")).textContent).toBe(
    "Could not copy: your browser blocked the clipboard.",
  );
  expect(screen.getByRole("combobox", { name: "Chapter" }).value).toBe(
    "First chapter",
  );
});

test("download uses the prepared text and filename and releases its object URL", async () => {
  const create = vi.fn(() => "blob:quiz");
  const revoke = vi.fn();
  vi.stubGlobal(
    "URL",
    class extends URL {
      static createObjectURL = create;
      static revokeObjectURL = revoke;
    },
  );
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(function () {
      expect(this.download).toBe("ready.md");
      expect(this.getAttribute("href")).toBe("blob:quiz");
    });
  mount(
    vi.fn().mockResolvedValue({ text: "Prepared quiz", fileName: "ready.md" }),
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Download" }).disabled).toBe(
      false,
    ),
  );
  fireEvent.click(screen.getByRole("button", { name: "Download" }));
  expect(create.mock.calls[0][0].type).toBe("text/markdown");
  expect(click).toHaveBeenCalledOnce();
  const text = await new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.readAsText(create.mock.calls[0][0]);
  });
  expect(text).toBe("Prepared quiz");
  await waitFor(() => expect(revoke).toHaveBeenCalledWith("blob:quiz"), {
    timeout: 1500,
  });
});

test.each([true, false])(
  "native sharing uses a file when supported, otherwise text (file support: %s)",
  (files) => {
    const share = vi.fn().mockResolvedValue();
    Object.defineProperties(navigator, {
      share: { configurable: true, value: share },
      canShare: { configurable: true, value: () => files },
    });
    mount(undefined, {
      withTypes: false,
      brief: () => "Shared quiz",
      fileName: () => "share.md",
    });
    fireEvent.click(screen.getByRole("button", { name: "Share…" }));
    expect(share).toHaveBeenCalledOnce();
    const data = share.mock.calls[0][0];
    if (files) {
      expect(data.files[0].name).toBe("share.md");
      expect(data.text).toBe("Quiz me on Comic using the attached file.");
    } else expect(data).toEqual({ text: "Shared quiz" });
  },
);
