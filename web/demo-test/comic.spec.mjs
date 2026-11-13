import { readFileSync } from "node:fs";
import { test as base, expect } from "@playwright/test";
import { expectWrite, readJson } from "../e2e/api.mjs";
import { checkRequests } from "../e2e/requests.mjs";

const test = base.extend({
  page: async ({ page }, use) => checkRequests(page, () => use(page)),
});
const api = "/api/titles/lulu-example-le-parapluie";
const comic = api + "/comic";
const reader =
  "/demo/?route=" + encodeURIComponent("/reader/lulu-example-le-parapluie/");

async function turn(page, pageId, action) {
  const saved = await expectWrite(
    page,
    { method: "PUT", path: comic + "/progress", body: { pageId } },
    action,
  );
  expect(saved.pageId).toBe(pageId);
  expect((await readJson(page, comic + "/progress")).pageId).toBe(pageId);
}
async function open(page) {
  await turn(page, "cover", () => page.goto(reader));
  await expect(
    page.getByRole("heading", { name: "Le parapluie de Lulu" }),
  ).toBeVisible();
}

test("the real library and reader work with canned data and persisted progress", async ({
  page,
}, info) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/");
  await expect(page.getByRole("link", { name: "GitHub" })).toHaveAttribute(
    "href",
    "https://github.com/parkie213/petites-roues",
  );
  await page.getByRole("link", { name: "Demo", exact: true }).click();
  await expect(
    page.getByRole("navigation", { name: "Main navigation" }),
  ).toBeVisible();
  for (const name of [
    "Add folder",
    "Connect a phone",
    "Read-aloud voices",
    "Refresh library",
  ])
    await expect(
      page.getByRole("button", { name, exact: true }),
    ).toBeDisabled();
  const library = await readJson(page, "/api/library");
  expect(library.repositories).toHaveLength(1);
  expect(library.repositories[0].entries.map((entry) => entry.id)).toEqual([
    "lulu-example-le-parapluie",
  ]);
  await expect(page.locator(".poster-card")).toHaveCount(1);
  await page
    .getByRole("textbox", { name: "Search your library" })
    .fill("no such comic");
  await expect(page.locator(".poster-card")).toHaveCount(0);
  await page.getByRole("textbox", { name: "Search your library" }).fill("Lulu");
  await expect(page.locator(".poster-card")).toHaveCount(1);
  await page.screenshot({ path: info.outputPath("library.png") });
  await turn(page, "cover", () =>
    page
      .getByRole("link", { name: "Le parapluie de Lulu", exact: true })
      .click(),
  );
  await expect(
    page.getByRole("button", { name: "Edit mode", exact: true }),
  ).toBeDisabled();
  await turn(page, "page-1", () =>
    page.getByRole("button", { name: /^Next page/ }).click(),
  );
  await page
    .getByRole("button", { name: "Bubble 1: Oh non, il pleut !" })
    .click();
  const source = page.getByRole("button", { name: "Oh non,", exact: true });
  const translation = page.getByRole("button", { name: "Oh no,", exact: true });
  await expect(translation).toBeVisible();
  expect(
    await translation.evaluate((el) => getComputedStyle(el).backgroundColor),
  ).toBe(await source.evaluate((el) => getComputedStyle(el).backgroundColor));
  await source.click();
  await expect(translation).toHaveAttribute("data-active", "true");
  await expect(page.getByText("Non = no.", { exact: true })).toBeVisible();
  await page.screenshot({ path: info.outputPath("reader.png") });
  await turn(page, "page-1", () => page.reload());
  await expect(
    page.getByRole("img", {
      name: "Le parapluie de Lulu, page 2",
      exact: true,
    }),
  ).toBeVisible();
  for (const [id, number] of [
    ["page-2", 3],
    ["page-3", 4],
  ]) {
    await turn(page, id, () =>
      page.getByRole("button", { name: /^Next page/ }).click(),
    );
    const image = page.getByRole("img", {
      name: `Le parapluie de Lulu, page ${number}`,
      exact: true,
    });
    await expect
      .poll(() => image.evaluate((img) => img.complete && img.naturalWidth > 0))
      .toBe(true);
  }
  await expect(page.getByRole("button", { name: /^Next page/ })).toBeDisabled();
  await page.getByRole("link", { name: "Back to library" }).click();
  await expect(
    page.getByRole("textbox", { name: "Search your library" }),
  ).toBeVisible();
  await expect(
    page.getByRole("progressbar", { name: "Progress", exact: true }),
  ).toHaveAttribute("aria-valuenow", "100");
  expect(errors).toEqual([]);
});

test("the actual library views and navigation stay inside the demo", async ({
  page,
  isMobile,
}) => {
  await page.goto("/demo/");
  await page.getByText("Documents", { exact: true }).click();
  await expect(page.getByRole("table")).toContainText("Le parapluie de Lulu");
  await turn(page, "cover", () =>
    page
      .getByRole("link", { name: "Le parapluie de Lulu", exact: true })
      .click(),
  );
  await page.getByRole("link", { name: "Back to library" }).click();
  await expect(
    page.getByRole("radio", { name: "Documents", exact: true }),
  ).toBeChecked();
  await page.getByText("Repositories", { exact: true }).click();
  await page.getByRole("link", { name: /^Demo/ }).click();
  await page.getByRole("button", { name: "Options for Demo" }).click();
  await expect(
    page.getByRole("menuitem", { name: "Refresh folder" }),
  ).toBeDisabled();
  await expect(
    page.getByRole("menuitem", { name: "Remove from library…" }),
  ).toBeDisabled();
  await page.keyboard.press("Escape");
  await page.getByRole("link", { name: "Notes", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "Notes", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  if (isMobile)
    await page.getByRole("button", { name: "Browse notes" }).click();
  await expect(
    page.getByRole("link", { name: "Verbs followed by an infinitive" }),
  ).toBeVisible();
  const notes = await readJson(page, "/api/notes");
  expect(notes.map((note) => note.id)).toEqual(["verb-plus-infinitive"]);
  await page.reload();
  await expect(
    page.getByRole("link", { name: "Notes", exact: true }),
  ).toHaveAttribute("aria-current", "page");
  await page.getByRole("link", { name: "Library", exact: true }).click();
  await expect(page.getByRole("button", { name: "Add folder" })).toBeDisabled();
  expect(new URL(page.url()).pathname).toBe("/demo/");
});

test("the real speech API plays saved bubbles, phrases, R replay and listening mode", async ({
  page,
}) => {
  await page.addInitScript(() => {
    window.demoPlayback = [];
    const play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      this.addEventListener(
        "playing",
        () => window.demoPlayback.push(this.duration),
        { once: true },
      );
      return play.call(this);
    };
  });
  await open(page);
  await turn(page, "page-1", () =>
    page.getByRole("button", { name: /^Next page/ }).click(),
  );
  await page
    .getByRole("button", { name: "Bubble 1: Oh non, il pleut !" })
    .click();
  const recordings = JSON.parse(
    readFileSync(
      new URL("../../site/demo-audio/manifest.json", import.meta.url),
      "utf8",
    ),
  );
  const playClip = async (text, action, count) => {
    const responsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === api + "/speech" &&
        url.searchParams.get("text") === text
      );
    });
    await action();
    const response = await responsePromise;
    expect(response.ok()).toBe(true);
    expect(await response.finished()).toBeNull();
    expect(await response.body()).toEqual(
      readFileSync(
        new URL(
          `../../site/demo-audio/${recordings.clips[text]}`,
          import.meta.url,
        ),
      ),
    );
    await expect
      .poll(() => page.evaluate(() => window.demoPlayback.length))
      .toBe(count);
    expect(
      await page.evaluate(() => window.demoPlayback.at(-1)),
    ).toBeGreaterThan(0);
  };
  await playClip(
    "Oh non, il pleut !",
    () => page.getByRole("button", { name: "Listen", exact: true }).click(),
    1,
  );
  await page.getByRole("button", { name: "Stop reading aloud" }).click();
  await page.getByRole("button", { name: "il pleut !", exact: true }).click();
  await playClip(
    "il pleut !",
    () => page.getByRole("button", { name: "Listen", exact: true }).click(),
    2,
  );
  await page.getByRole("button", { name: "Stop reading aloud" }).click();
  await playClip("Oh non, il pleut !", () => page.keyboard.press("r"), 3);
  await page.keyboard.press("r");
  await page
    .getByRole("button", { name: "Listen on hover", exact: true })
    .click();
  await turn(page, "page-2", () =>
    page.getByRole("button", { name: /^Next page/ }).click(),
  );
  const document = await readJson(page, comic);
  const line = document.comic.pages.find((p) => p.id === "page-2").boxes[0]
    .lines[0];
  await playClip(
    line.source,
    () => page.getByRole("button", { name: /^Bubble 1:/ }).click(),
    4,
  );
  await turn(page, "page-2", () => page.reload());
  await expect(
    page.getByRole("button", { name: "Stop listening on hover" }),
  ).toHaveAttribute("aria-pressed", "true");
});
