// @vitest-environment jsdom
import { MantineProvider } from "@mantine/core";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { ListenButton } from "../src/comic/ListenButton.jsx";
import { ReadAloudProvider } from "../src/comic/ReadAloud.jsx";

/** A stand-in for the browser's Audio, which jsdom can't play. */
class FakeAudio {
  static playing = [];
  constructor(src) {
    this.src = src;
    this.paused = true;
  }
  async play() {
    this.paused = false;
    FakeAudio.playing.push(this);
  }
  pause() {
    this.paused = true;
  }
}

function renderButton(voice) {
  vi.stubGlobal("fetch", vi.fn(voice));
  vi.stubGlobal("Audio", FakeAudio);
  URL.createObjectURL = () => "blob:voice";
  URL.revokeObjectURL = () => {};
  FakeAudio.playing = [];
  render(
    <MantineProvider>
      <ReadAloudProvider url="/api/voice">
        <ListenButton text="Oh non, il pleut !" />
      </ReadAloudProvider>
    </MantineProvider>,
  );
}

test("Listen reads the text in the server's voice, and pressing it again stops it", async () => {
  renderButton(async () => new Response(new Blob(["audio"])));
  const user = userEvent.setup();

  await user.click(screen.getByRole("button", { name: "Listen" }));

  const stop = await screen.findByRole("button", {
    name: "Stop reading aloud",
  });
  expect(stop.getAttribute("aria-pressed")).toBe("true");
  expect(fetch).toHaveBeenCalledWith(
    "/api/voice?text=Oh%20non%2C%20il%20pleut%20!",
    expect.anything(),
  );
  expect(FakeAudio.playing).toHaveLength(1);

  await user.click(stop);

  expect(screen.getByRole("button", { name: "Listen" })).toBeTruthy();
  expect(FakeAudio.playing[0].paused).toBe(true);
});

test("a voice that fails explains why when pointed at", async () => {
  renderButton(
    async () =>
      new Response(JSON.stringify({ error: "No French voice installed" }), {
        status: 503,
      }),
  );
  const user = userEvent.setup();

  await user.click(screen.getByRole("button", { name: "Listen" }));
  await user.hover(screen.getByRole("button", { name: "Listen" }));

  expect(await screen.findByText("No French voice installed")).toBeTruthy();
  expect(FakeAudio.playing).toHaveLength(0);
});
