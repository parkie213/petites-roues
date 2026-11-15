import { readFile } from "node:fs/promises";
import { test, expect, expectWrite, readJson, looksLike } from "./api.mjs";

test.use({ pauseOnOpen: true });

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
  const progress = "/api/titles/peppa-pig-example-s01e01/progress";
  expect(
    await expectWrite(
      page,
      {
        method: "PUT",
        path: progress,
        body: { positionSeconds: 2.5 },
      },
      () => page.keyboard.press("ArrowRight"),
    ),
  ).toMatchObject({ positionSeconds: 2.5 });
  await expect(
    page.getByRole("button", { name: "Il ne pleut plus" }),
  ).toBeVisible();
  // Wait for this seek's save before leaving the player. Otherwise Back can abandon its
  // response, making the recorded PUT appear or disappear depending on navigation timing.
  expect(
    await expectWrite(
      page,
      {
        method: "PUT",
        path: progress,
        body: { positionSeconds: 6.92 },
      },
      () => page.keyboard.press("ArrowRight"),
    ),
  ).toMatchObject({ positionSeconds: 6.92 });
  expect((await readJson(page, progress)).positionSeconds).toBeCloseTo(6.92, 1);
  await expect(page.getByRole("button", { name: "Bien sûr." })).toBeVisible();
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

test("Quiz me opens on the desktop's default, narrows to a time range and exports that brief", async ({
  page,
}) => {
  const quiz = "/api/titles/peppa-pig-example-s01e01/quiz";
  await page.goto("/");
  await page.getByRole("link", { name: "Peppa Pig" }).click();
  await page
    .getByRole("link", { name: /flaques de boue/i })
    .first()
    .click();
  await expect(page.getByText("0:00 / 1:02")).toBeVisible();

  // The page sends no scope, so the desktop picks one: a one-minute episode is quizzed whole.
  await page.mouse.move(640, 10); // the top bar fades until the pointer is at the edge
  await page.getByRole("button", { name: "Quiz me" }).click();
  const panel = page.getByRole("complementary", { name: "Video quiz" });
  await expect(panel.getByText("9 subtitle lines · Whole title")).toBeVisible();
  const whole = await readJson(page, `${quiz}?position=0`);
  expect(whole).toMatchObject({
    type: "to-source",
    scope: { kind: "whole" },
    valid: true,
    lineCount: 9,
    large: false,
    largeAbove: 400,
  });
  expect(whole.types.map((type) => type.label)).toContain(
    "Translate into French",
  );
  expect(whole.text).toContain("## Whole title");

  // Each edit is awaited, so the recording holds exactly one request per step.
  const answered = (action) =>
    Promise.all([
      page.waitForResponse(
        (response) =>
          response.url().includes("/quiz?") &&
          response.request().method() === "GET",
      ),
      action(),
    ]);
  await answered(() => panel.getByText("Time range", { exact: true }).click());
  await answered(() =>
    panel.getByRole("textbox", { name: "Start" }).fill("0:10"),
  );
  await answered(() =>
    panel.getByRole("textbox", { name: "End" }).fill("0:30"),
  );
  await expect(panel.getByText("3 subtitle lines · 0:10 – 0:30")).toBeVisible();
  const range = await readJson(
    page,
    `${quiz}?position=0&scope=range&start=0:10&end=0:30`,
  );
  expect(range).toMatchObject({
    scope: { kind: "range", start: "0:10", end: "0:30" },
    valid: true,
    lineCount: 3,
    fileName: expect.stringContaining("0-10-to-0-30"),
  });
  expect(range.text).toContain("## Lines in time range (0:10–0:30)");
  expect(range.text).toContain("Peppa adore sauter");
  expect(range.text).not.toContain("Il ne pleut plus");

  // An end before the start is reported, not exported.
  await answered(() =>
    panel.getByRole("textbox", { name: "End" }).fill("0:05"),
  );
  await expect(panel.getByRole("alert")).toContainText("m:ss or h:mm:ss");
  await expect(panel.getByRole("button", { name: /ChatGPT/ })).toBeDisabled();
  await answered(() =>
    panel.getByRole("textbox", { name: "End" }).fill("0:30"),
  );
  await expect(panel.getByText("3 subtitle lines · 0:10 – 0:30")).toBeVisible();
  await looksLike(page, "quiz-panel");

  // The downloaded file is the reply's brief for this selection.
  const download = page.waitForEvent("download");
  await panel.getByRole("button", { name: "Download .md" }).click();
  const file = await download;
  expect(file.suggestedFilename()).toBe(range.fileName);
  expect(await readFile(await file.path(), "utf8")).toBe(range.text);
});
