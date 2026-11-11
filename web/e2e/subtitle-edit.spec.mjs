import { test, expect, looksLike } from "./api.mjs";

test.use({ logMedia: true });

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
  await page.keyboard.press("ArrowRight");
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
  await page.keyboard.press("Shift+ArrowRight");
  await expect(editor.getByRole("button", { name: "end time" })).toHaveText(
    "0:07.020",
  );
  // Timing saves itself shortly after the last nudge.
  await expect(editor.getByText("Timing saved")).toBeVisible();

  // The timeline shows six seconds either side of the playhead (2.5 s): pressing the second
  // line at 8.3 s puts the playhead there and opens the second, which now starts where the
  // first ends.
  const box = await timeline.boundingBox();
  await timeline.click({
    position: { x: ((8.3 - (2.5 - 6)) / 12) * box.width, y: 21 },
  });
  await expect(editor.getByLabel("French")).toHaveValue(/^Bien sûr/);
  await expect(page.getByText("0:08 / 1:02")).toBeVisible();
  await expect(editor.getByRole("button", { name: "start time" })).toHaveText(
    "0:07.020",
  );

  // The view is now 2.3–14.3 s. The third line starts at 13.79 s, after a gap: dragging this
  // line's end onto it joins them, and dragging on moves both.
  const x = (seconds) => box.x + ((seconds - (8.3 - 6)) / 12) * box.width;
  const endTime = editor.getByRole("button", { name: "end time" });
  await page.mouse.move(x(9.82), box.y + 21);
  await page.mouse.down();
  await page.mouse.move(x(12), box.y + 21);
  await page.mouse.move(x(13.79), box.y + 21);
  await page.mouse.move(x(14.1), box.y + 21);
  await page.mouse.up();
  await expect(endTime).toHaveText(/^0:14\.\d{3}$/);
  const end = await endTime.textContent();
  await expect(editor.getByText("Timing saved")).toBeVisible();

  // The arrows beside the editor's title step through the lines.
  await editor.getByRole("button", { name: "Next line" }).click();
  await expect(editor.getByLabel("French")).toHaveValue(/^Peppa adore/);
  await expect(editor.getByRole("button", { name: "start time" })).toHaveText(
    end,
  );
  await editor.getByRole("button", { name: "Previous line" }).click();
  await expect(editor.getByLabel("French")).toHaveValue(/^Bien sûr/);
  await expect(endTime).toHaveText(end);

  // Saving text keeps the editor open on the line.
  await editor
    .getByLabel("Translation")
    .fill("Of course. Off you go and have fun.");
  await editor.getByRole("button", { name: "Save changes" }).click();
  await expect(
    editor.getByRole("button", { name: "Save changes" }),
  ).toBeDisabled();
  await expect(editor.getByLabel("French")).toHaveValue(/^Bien sûr/);

  // Pressing the timeline focuses it, and Space still plays and pauses there. Last, as playing
  // moves the view the drags above are placed in.
  await timeline.click({ position: { x: box.width / 2, y: 50 } });
  const toolbar = page.getByLabel("Playback controls");
  await page.keyboard.press("Space");
  await expect(toolbar.getByRole("button", { name: "Pause" })).toBeVisible();
  await page.keyboard.press("Space");
  await expect(toolbar.getByRole("button", { name: "Play" })).toBeVisible();
});
