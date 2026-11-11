import { test, expect, looksLike } from "./api.mjs";

test("the library shelves the example's comic and series", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("2 videos · 1 comic")).toBeVisible();
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
