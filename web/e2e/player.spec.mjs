import { test, expect } from "./api.mjs";

test("an episode steps through its subtitles, and Back returns to its series", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Peppa Pig" }).click();
  await page
    .getByRole("link", { name: /flaques de boue/i })
    .first()
    .click();

  await expect(
    page.getByText("Les Flaques de Boue - one-minute example"),
  ).toBeVisible();
  await expect(page.getByText("0:00 / 1:02")).toBeVisible();

  // → steps to the next line's start, whose phrases show as buttons.
  await page.keyboard.press("ArrowRight");
  await expect(
    page.getByRole("button", { name: "Il ne pleut plus" }),
  ).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("button", { name: "Bien sûr." })).toBeVisible();
  await page.keyboard.press("t");
  const translation = page.getByRole("group", { name: "Translation" });
  await expect(
    translation.getByRole("button", { name: "Of course." }),
  ).toBeVisible();

  // The controls fade while watching; pointing at the top edge brings them back.
  await page.mouse.move(640, 10);
  await page.getByRole("link", { name: "Back to library" }).click();
  await expect(
    page.getByRole("link", { name: /dinosaure/i }).first(),
  ).toBeVisible();
});
