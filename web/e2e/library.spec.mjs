import { test, expect, readJson, looksLike } from "./api.mjs";

// Card shape by shelf and card in the example library: only the clip has no poster.
const EXPECTED_SHAPES = [
  ["Comics", "Lulu", "portrait"],
  ["Series", "Peppa Pig", "portrait"],
  ["YouTube", "Une vidéo de démonstration", "landscape"],
];

test("the library shelves the example's comic and series", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("3 videos · 1 comic")).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Lulu", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeVisible();
  await looksLike(page, "library");

  // Folders keep their ancestors visible and open the study document at its own path.
  await page
    .getByRole("radiogroup", { name: "Library view" })
    .getByText("Repositories", { exact: true })
    .click();
  await page
    .getByRole("navigation", { name: "Repositories" })
    .getByRole("link", { name: /documents-fr/ })
    .click();
  for (const [parent, child] of [
    ["documents-fr", "series"],
    ["series", "peppa-pig"],
    ["peppa-pig", "s01e01"],
  ]) {
    await page
      .getByRole("navigation", { name: parent })
      .getByRole("link", { name: new RegExp(child) })
      .click();
  }
  await expect(
    page.getByRole("link", { name: /Play Les Flaques de Boue/ }),
  ).toBeVisible();
  await looksLike(page, "repositories");
});

test("a series opens to its episodes", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("link", { name: "Peppa Pig" }).click();

  await expect(
    page.getByRole("link", { name: /flaques de boue/i }).first(),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: /dinosaure/i }).first(),
  ).toBeVisible();
  await looksLike(page, "series");
});

test("the library flags what needs attention, and can show only that", async ({
  page,
}) => {
  await page.goto("/");

  // One Peppa Pig episode's video has no study document in the example; the count beside the
  // library's totals is a filter.
  const attention = page.getByRole("button", { name: "1 warning" });
  await attention.click();

  await expect(attention).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Lulu", exact: true }),
  ).toBeHidden();
});

test("shelf tabs list the shelves and show only the chosen shelf's cards", async ({
  page,
}) => {
  await page.goto("/");

  const tabs = page.getByRole("tablist", { name: "Shelves" });
  await expect(tabs.getByRole("tab")).toHaveText([
    "All3",
    "Comics1",
    "Series1",
    "YouTube1",
  ]);
  await expect(tabs.getByRole("tab", { name: /^All/ })).toHaveAttribute(
    "aria-selected",
    "true",
  );

  await tabs.getByRole("tab", { name: /^Series/ }).click();
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Lulu", exact: true }),
  ).toBeHidden();
  await expect(page.getByRole("heading", { name: "Comics" })).toBeHidden();

  await tabs.getByRole("tab", { name: /^Comics/ }).click();
  await expect(
    page.getByRole("link", { name: "Lulu", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeHidden();

  await tabs.getByRole("tab", { name: /^All/ }).click();
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Lulu", exact: true }),
  ).toBeVisible();
});

test("a video with no poster is a wide frame card whatever its shelf; a card with a poster keeps 2:3", async ({
  page,
}) => {
  await page.goto("/");

  const clip = page.getByRole("link", { name: "Une vidéo de démonstration" });
  const peppa = page.getByRole("link", { name: "Peppa Pig" });
  await expect(clip).toBeVisible();
  const ratio = async (card) => {
    const box = await card.locator(".poster-art").boundingBox();
    return box.width / box.height;
  };
  expect(await ratio(clip)).toBeCloseTo(16 / 9, 1);
  expect(await ratio(peppa)).toBeCloseTo(2 / 3, 1);

  // The clip has no poster: its card is a 480 x 270 frame the server grabbed, not a placeholder.
  const art = clip.locator("img.poster-art");
  await expect(art).toBeVisible();
  await expect
    .poll(() => art.evaluate((img) => [img.naturalWidth, img.naturalHeight]))
    .toEqual([480, 270]);
  // Peppa Pig's poster fills a portrait card, so its image is portrait too.
  await expect(peppa.locator("img.poster-art")).toBeVisible();

  // The server's own answer agrees, card by card: the shape follows the art, not the shelf.
  const { shelves } = await readJson(page, "/api/library/posters");
  expect(
    shelves.flatMap((shelf) =>
      shelf.groups.map((group) => [shelf.label, group.name, group.shape]),
    ),
  ).toEqual(EXPECTED_SHAPES);

  // Its tab narrows to the wide cards; search still finds them.
  await page
    .getByRole("tablist", { name: "Shelves" })
    .getByRole("tab", { name: /^YouTube/ })
    .click();
  await expect(peppa).toBeHidden();
  await expect(clip).toBeVisible();
  await looksLike(page, "landscape-shelf");
});
