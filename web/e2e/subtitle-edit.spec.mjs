import { test, expect, looksLike } from "./api.mjs";

test("moving a line's end moves the next line's start with it", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Peppa Pig" }).click();
  await page
    .getByRole("link", { name: /flaques de boue/i })
    .first()
    .click();
  await expect(page.getByText("0:00 / 1:02")).toBeVisible();

  // The first line ends where the second starts, so their boundary is linked by default.
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("e");
  const editor = page.getByRole("complementary", { name: "Subtitle editor" });
  await expect(editor.getByLabel("end time")).toHaveValue("0:06.920");
  await expect(editor.getByLabel("Next line follows")).toBeChecked();
  await looksLike(page, "subtitle-editor");
  await editor
    .getByRole("button", { name: "Nudge end later by 100 milliseconds" })
    .click();
  await editor.getByRole("button", { name: "Save changes" }).click();
  await expect(editor).toBeHidden();

  // The second line now starts where the first ends.
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("e");
  await expect(editor.getByLabel("start time")).toHaveValue("0:07.020");
});
