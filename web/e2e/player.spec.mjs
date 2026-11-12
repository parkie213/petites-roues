import { test, expect, looksLike } from "./api.mjs";

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
  // Wait for this seek's save before leaving the player. Otherwise Back can abandon its
  // response, making the recorded PUT appear or disappear depending on navigation timing.
  const progressSaved = page.waitForResponse(
    (response) =>
      response
        .url()
        .endsWith("/api/titles/peppa-pig-example-s01e01/progress") &&
      response.request().method() === "PUT" &&
      response.request().postDataJSON().positionSeconds === 6.92,
  );
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("button", { name: "Bien sûr." })).toBeVisible();
  const saved = await progressSaved;
  expect(saved.ok()).toBe(true);
  await saved.finished();
  // Keys work from a subtitle phrase too, as after reading its note.
  await page.getByRole("button", { name: "Bien sûr." }).focus();
  await page.keyboard.press("t");
  const translation = page.getByRole("group", { name: "Translation" });
  await expect(
    translation.getByRole("button", { name: "Of course." }),
  ).toBeVisible();

  // The key left the controls hidden, so they don't cover the video. Read at once, as the controls
  // would fade again by themselves.
  expect(
    await page.locator(".video-player").getAttribute("data-controls-visible"),
  ).toBeNull();
  await looksLike(page, "player");

  // Pointing at the top edge brings the controls back.
  await page.mouse.move(640, 10);
  await page.getByRole("link", { name: "Back to library" }).click();
  await expect(
    page.getByRole("link", { name: /dinosaure/i }).first(),
  ).toBeVisible();
});
