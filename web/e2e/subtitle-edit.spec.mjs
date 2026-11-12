import { test, expect, expectWrite, readJson, looksLike } from "./api.mjs";

test.use({ logMedia: true, pauseOnOpen: true });

const title = "/api/titles/peppa-pig-example-s01e01";

async function seek(page, action, expected) {
  const saved = await expectWrite(
    page,
    {
      method: "PUT",
      path: title + "/progress",
      body: (body) =>
        typeof body?.positionSeconds === "number" &&
        (expected === undefined ||
          Math.abs(body.positionSeconds - expected) < 0.03),
    },
    action,
  );
  if (expected !== undefined)
    expect(saved.positionSeconds).toBeCloseTo(expected, 1);
  return saved;
}

async function retime(page, action, id, end) {
  const saved = await expectWrite(
    page,
    {
      method: "POST",
      path: title + "/timings",
      body: (edits) =>
        edits.some((edit) => edit.id === id && Math.abs(edit.end - end) < 0.03),
    },
    action,
  );
  expect(saved).toMatchObject({ ok: true, lines: 9 });
  return (await readJson(page, title)).video.cues;
}

test("moving a line's end moves the next line's start with it, and the editor steps through lines", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Peppa Pig" }).click();
  await page
    .getByRole("link", { name: /flaques de boue/i })
    .first()
    .click();
  await expect(page.getByText("0:00 / 1:02")).toBeVisible();

  // The first line (2.5–6.92 s) ends where the second starts, so their boundary moves both.
  const original = (await readJson(page, title)).video.cues;
  await seek(page, () => page.keyboard.press("ArrowRight"), 2.5);
  await expect(page.getByText("0:02 / 1:02")).toBeVisible();
  await page.keyboard.press("e");
  const editor = page.getByRole("complementary", { name: "Subtitle editor" });
  const timeline = page.getByLabel(/^Subtitle timeline/);
  await expect(editor.getByRole("button", { name: "end time" })).toHaveText(
    "0:06.920",
  );
  await expect(timeline).toBeVisible();
  await looksLike(page, "subtitle-editor");
  await editor.getByRole("button", { name: "end time" }).click();
  const nudged = await retime(
    page,
    () => page.keyboard.press("Shift+ArrowRight"),
    original[0].line.id,
    7.02,
  );
  expect(nudged[0]).toMatchObject({ start: 2.5, end: 7.02 });
  expect(nudged[1].start).toBe(7.02);
  expect(nudged[1].end).toBe(original[1].end);
  await expect(editor.getByRole("button", { name: "end time" })).toHaveText(
    "0:07.020",
  );
  // Timing saves itself shortly after the last nudge.
  await expect(editor.getByText("Timing saved")).toBeVisible();

  // The timeline shows six seconds either side of the playhead (2.5 s): pressing the second
  // line at 8.3 s puts the playhead there and opens the second, which now starts where the
  // first ends.
  const box = await timeline.boundingBox();
  await seek(
    page,
    () =>
      timeline.click({
        position: { x: ((8.3 - (2.5 - 6)) / 12) * box.width, y: 21 },
      }),
    8.3,
  );
  await expect(editor.getByLabel("French")).toHaveValue(/^Bien sûr/);
  await expect(page.getByText("0:08 / 1:02")).toBeVisible();
  await expect(editor.getByRole("button", { name: "start time" })).toHaveText(
    "0:07.020",
  );

  // The view is now 2.3–14.3 s. The third line starts at 13.79 s, after a gap: dragging this
  // line's end onto it joins them, and dragging on moves both.
  const x = (seconds) => box.x + ((seconds - (8.3 - 6)) / 12) * box.width;
  const endTime = editor.getByRole("button", { name: "end time" });
  const dragged = await retime(
    page,
    async () => {
      await page.mouse.move(x(9.82), box.y + 21);
      await page.mouse.down();
      await page.mouse.move(x(12), box.y + 21);
      await page.mouse.move(x(13.79), box.y + 21);
      await page.mouse.move(x(14.1), box.y + 21);
      await page.mouse.up();
    },
    original[1].line.id,
    14.1,
  );
  expect(dragged[1].end).toBeCloseTo(14.1, 1);
  expect(dragged[2].start).toBe(dragged[1].end);
  expect(dragged[0]).toEqual(nudged[0]);
  await expect(endTime).toHaveText(/^0:14\.\d{3}$/);
  const end = await endTime.textContent();
  await expect(editor.getByText("Timing saved")).toBeVisible();

  // The arrows beside the editor's title step through the lines.
  await seek(
    page,
    () => editor.getByRole("button", { name: "Next line" }).click(),
    dragged[2].start,
  );
  await expect(editor.getByLabel("French")).toHaveValue(/^Peppa adore/);
  await expect(editor.getByRole("button", { name: "start time" })).toHaveText(
    end,
  );
  await seek(
    page,
    () => editor.getByRole("button", { name: "Previous line" }).click(),
    7.02,
  );
  await expect(editor.getByLabel("French")).toHaveValue(/^Bien sûr/);
  await expect(endTime).toHaveText(end);

  // Saving text keeps the editor open on the line.
  await editor
    .getByLabel("Translation")
    .fill("Of course. Off you go and have fun.");
  const translation = "Of course. Off you go and have fun.";
  const updated = await expectWrite(
    page,
    {
      method: "PUT",
      path: title + "/cues/" + original[1].line.id,
      body: { translation },
    },
    () => editor.getByRole("button", { name: "Save changes" }).click(),
  );
  expect(updated.video.cues[1].line.translation).toBe(translation);
  const persisted = (await readJson(page, title)).video.cues;
  expect(persisted[1].line.translation).toBe(translation);
  expect(persisted[1].line.source).toBe(original[1].line.source);
  expect(persisted[1].end).toBe(dragged[1].end);
  expect(persisted[2].start).toBe(dragged[2].start);
  await expect(
    editor.getByRole("button", { name: "Save changes" }),
  ).toBeDisabled();
  await expect(editor.getByLabel("French")).toHaveValue(/^Bien sûr/);

  // Pressing the timeline focuses it, and Space still plays and pauses there. Last, as playing
  // moves the view the drags above are placed in.
  await seek(
    page,
    () => timeline.click({ position: { x: box.width * 0.6, y: 50 } }),
    8.22,
  );
  const toolbar = page.getByLabel("Playback controls");
  await page.keyboard.press("Space");
  await expect(toolbar.getByRole("button", { name: "Pause" })).toBeVisible();
  await expect
    .poll(() => page.locator("video").evaluate((video) => video.currentTime))
    .toBeGreaterThan(8.4);
  const paused = await seek(page, () => page.keyboard.press("Space"));
  expect(paused.positionSeconds).toBeGreaterThan(8.4);
  await expect(toolbar.getByRole("button", { name: "Play" })).toBeVisible();
  // Finish at a deliberate position; the pause save itself depends on elapsed playback time.
  await seek(
    page,
    () => editor.getByRole("button", { name: "Next line" }).click(),
    dragged[2].start,
  );
  await seek(
    page,
    () => editor.getByRole("button", { name: "Previous line" }).click(),
    7.02,
  );
  expect(
    (await readJson(page, title + "/progress")).positionSeconds,
  ).toBeCloseTo(7.02, 1);
  await page.reload();
  await expect(page.getByText("0:07 / 1:02")).toBeVisible();
  await page.keyboard.press("e");
  await expect(
    page
      .getByRole("complementary", { name: "Subtitle editor" })
      .getByLabel("Translation"),
  ).toHaveValue(translation);
});
