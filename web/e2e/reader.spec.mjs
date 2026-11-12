import { test, expect, expectWrite, looksLike } from "./api.mjs";

const progress = "/api/titles/lulu-example-le-parapluie/comic/progress";

test("the comic opens on its cover, and its pages turn", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Lulu", exact: true }).click();
  await expectWrite(page, progress, { pageId: "cover" }, () =>
    page
      .getByRole("link", { name: /parapluie/i })
      .first()
      .click(),
  );

  await expect(
    page.getByRole("button", { name: "Bubble 1: Le parapluie de Lulu" }),
  ).toBeVisible();
  await expectWrite(page, progress, { pageId: "page-1" }, () =>
    page.getByRole("button", { name: /^Next page/ }).click(),
  );
  await expect(
    page.getByRole("button", { name: "Bubble 1: Oh non, il pleut !" }),
  ).toBeVisible();
  await expectWrite(page, progress, { pageId: "cover" }, () =>
    page.getByRole("button", { name: /^Previous page/ }).click(),
  );
  await expect(
    page.getByRole("button", { name: "Bubble 1: Le parapluie de Lulu" }),
  ).toBeVisible();
});

test("tapping a bubble explains it, and Back returns to the library", async ({
  page,
}) => {
  await expectWrite(page, progress, { pageId: "cover" }, () =>
    page.goto("/reader/lulu-example-le-parapluie/"),
  );
  await expectWrite(page, progress, { pageId: "page-1" }, () =>
    page.getByRole("button", { name: /^Next page/ }).click(),
  );

  await page
    .getByRole("button", { name: "Bubble 1: Oh non, il pleut !" })
    .click();
  await expect(page.getByText("Oh no, it's raining!").first()).toBeVisible();
  await looksLike(page, "reader-bubble");

  await page.getByRole("link", { name: "Back to library" }).click();
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeVisible();
});
