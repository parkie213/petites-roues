import { readFileSync } from "node:fs";
import { test, expect } from "@playwright/test";

// This is browser-local persistence: there are no API writes or recordings in the static demo.
test("the landing page opens a one-comic library and a readable, resumable comic", async ({
  page,
}, info) => {
  const failures = [];
  page.on("pageerror", (error) => failures.push(error.message));
  page.on("response", (response) => {
    if (
      new URL(response.url()).origin === "http://127.0.0.1:18876" &&
      response.status() >= 400
    )
      failures.push(`${response.status()} ${response.url()}`);
  });
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.startsWith("/api/"))
      failures.push(`Unexpected API request: ${request.url()}`);
  });
  await page.goto("/");
  await expect(page.getByRole("link", { name: "GitHub" })).toHaveAttribute(
    "href",
    "https://github.com/parkie213/petites-roues",
  );
  await page.getByRole("link", { name: "Demo", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Library", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Comics" }).getByRole("link"),
  ).toHaveCount(1);
  await page.getByRole("link", { name: /Le parapluie de Lulu/ }).click();
  const image = page.getByRole("img", {
    name: "Le parapluie de Lulu, page 1",
    exact: true,
  });
  await expect(image).toBeVisible();
  await expect
    .poll(() => image.evaluate((img) => img.complete && img.naturalWidth > 0))
    .toBe(true);
  await expect(
    page.getByRole("button", { name: "Previous page (←)", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: /^Next page/ }).click();
  await expect(
    page.getByRole("img", {
      name: "Le parapluie de Lulu, page 2",
      exact: true,
    }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Bubble 1: Oh non, il pleut !" })
    .click();
  await expect(
    page.getByText("Oh no, it's raining!", { exact: false }),
  ).toBeVisible();
  const source = page.getByRole("button", { name: "Oh non,", exact: true });
  const translation = page.getByRole("button", { name: "Oh no,", exact: true });
  await expect(translation).toBeVisible();
  expect(
    await translation.evaluate(
      (el) => el.ownerDocument.defaultView.getComputedStyle(el).backgroundColor,
    ),
  ).toBe(
    await source.evaluate(
      (el) => el.ownerDocument.defaultView.getComputedStyle(el).backgroundColor,
    ),
  );
  await source.click();
  await expect(translation).toHaveAttribute("data-active", "true");
  await expect(page.getByText("Non = no.", { exact: true })).toBeVisible();
  await expect(
    page.getByText("Hover or click a phrase for its explanation.", {
      exact: true,
    }),
  ).toBeHidden();
  await page.screenshot({ path: info.outputPath("reader.png") });
  await expect
    .poll(() =>
      page.evaluate(() => JSON.parse(localStorage.getItem("demo:lulu:page"))),
    )
    .toBe(1);
  await page.reload();
  await expect(
    page.getByRole("img", {
      name: "Le parapluie de Lulu, page 2",
      exact: true,
    }),
  ).toBeVisible();
  for (const number of [3, 4]) {
    await page.getByRole("button", { name: /^Next page/ }).click();
    const nextImage = page.getByRole("img", {
      name: `Le parapluie de Lulu, page ${number}`,
      exact: true,
    });
    await expect
      .poll(() =>
        nextImage.evaluate((img) => img.complete && img.naturalWidth > 0),
      )
      .toBe(true);
  }
  await expect(page.getByRole("button", { name: /^Next page/ })).toBeDisabled();
  await page.getByRole("link", { name: "Back to library" }).click();
  await expect(
    page.getByRole("heading", { name: "Library", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("4 pages · Continue on page 4")).toBeVisible();
  await page.screenshot({ path: info.outputPath("library.png") });
  expect(failures).toEqual([]);
});

test("saved recordings play bubbles, pinned phrases, replay and listen-on-hover", async ({
  page,
}) => {
  await page.addInitScript(() => {
    window.demoPlayback = [];
    const play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      this.addEventListener(
        "playing",
        () =>
          window.demoPlayback.push({ playing: true, duration: this.duration }),
        { once: true },
      );
      return play.call(this);
    };
  });
  await page.goto("/demo/#read");
  await page.getByRole("button", { name: /^Next page/ }).click();
  await page
    .getByRole("button", { name: "Bubble 1: Oh non, il pleut !" })
    .click();
  const playClip = async (action, count) => {
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname.startsWith("/demo/audio/"),
    );
    await action();
    const response = await responsePromise;
    expect(response.status()).toBeLessThan(400);
    expect(await response.finished()).toBeNull();
    expect((await response.body()).length).toBeGreaterThan(1000);
    await expect
      .poll(() => page.evaluate(() => window.demoPlayback.length))
      .toBe(count);
    expect(
      await page.evaluate(() => window.demoPlayback.at(-1).duration),
    ).toBeGreaterThan(0);
    return response.url();
  };
  const bubble = await playClip(
    () => page.getByRole("button", { name: "Listen", exact: true }).click(),
    1,
  );
  await page.getByRole("button", { name: "Stop reading aloud" }).click();
  await expect(
    page.getByRole("button", { name: "Listen", exact: true }),
  ).toHaveAttribute("aria-pressed", "false");
  await page.getByRole("button", { name: "il pleut !", exact: true }).click();
  const phrase = await playClip(
    () => page.getByRole("button", { name: "Listen", exact: true }).click(),
    2,
  );
  expect(phrase).not.toBe(bubble);
  const { clips } = JSON.parse(
    readFileSync(
      new URL("../../site/demo-audio/manifest.json", import.meta.url),
      "utf8",
    ),
  );
  expect(new URL(bubble).pathname).toBe(
    `/demo/audio/${clips["Oh non, il pleut !"]}`,
  );
  expect(new URL(phrase).pathname).toBe(`/demo/audio/${clips["il pleut !"]}`);
  await page.getByRole("button", { name: "Stop reading aloud" }).click();
  const replay = await playClip(() => page.keyboard.press("r"), 3);
  expect(replay).toBe(bubble);
  await page.keyboard.press("r");
  await page
    .getByRole("button", { name: "Listen on hover", exact: true })
    .click();
  // A fresh card mounts after the page turn, as it does when a learner opens another bubble.
  await page.getByRole("button", { name: /^Next page/ }).click();
  await playClip(
    () => page.getByRole("button", { name: /^Bubble 1:/ }).click(),
    4,
  );
  await expect
    .poll(() =>
      page.evaluate(() => JSON.parse(localStorage.getItem("demo:auto-listen"))),
    )
    .toBe(true);
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Stop listening on hover" }),
  ).toHaveAttribute("aria-pressed", "true");
});
