import { test, expect, looksLike } from "./api.mjs";

test("moving a line's end moves the next line's start with it, and the timeline opens that line", async ({
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

  // The timeline shows six seconds either side of the playhead (2.5 s): pressing the second
  // line at 8.3 s puts the playhead there, saves the first line and opens the second, which now
  // starts where the first ends.
  const box = await timeline.boundingBox();
  await timeline.click({
    position: { x: ((8.3 - (2.5 - 6)) / 12) * box.width, y: 21 },
  });
  await expect(editor.getByLabel("French")).toHaveValue(/^Bien sûr/);
  await expect(page.getByText("0:08 / 1:02")).toBeVisible();
  await expect(editor.getByRole("button", { name: "start time" })).toHaveText(
    "0:07.020",
  );

  // The view is now 2.3–14.3 s: drag the line's end from 9.82 s to 10.32 s, a few pixels either way.
  const x = (seconds) => box.x + ((seconds - (8.3 - 6)) / 12) * box.width;
  await page.mouse.move(x(9.82), box.y + 21);
  await page.mouse.down();
  await page.mouse.move(x(10.1), box.y + 21);
  await page.mouse.move(x(10.32), box.y + 21);
  await page.mouse.up();
  await expect(editor.getByRole("button", { name: "end time" })).toHaveText(
    /^0:10\.[23]\d\d$/,
  );
});
