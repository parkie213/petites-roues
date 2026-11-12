import { test, expect, expectWrite, readJson, looksLike } from "./api.mjs";

// A successful response and a fresh GET are both required: visible pages are optimistic.
async function turn(page, pageId, action) {
  const expected = { pageId };
  expect(
    await expectWrite(
      page,
      { method: "PUT", path: progress, body: expected },
      action,
    ),
  ).toMatchObject(expected);
  expect(await readJson(page, progress)).toMatchObject(expected);
}

const progress = "/api/titles/lulu-example-le-parapluie/comic/progress";

test("the comic opens on its cover, and its pages turn", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Lulu", exact: true }).click();
  await turn(page, "cover", () =>
    page
      .getByRole("link", { name: /parapluie/i })
      .first()
      .click(),
  );

  await expect(
    page.getByRole("button", { name: "Bubble 1: Le parapluie de Lulu" }),
  ).toBeVisible();
  const editRegions = page.getByRole("button", { name: "Edit bubble regions" });
  await expect(editRegions).toHaveAttribute("aria-pressed", "false");
  await editRegions.click();
  await expect(editRegions).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByText(/Changes save automatically/)).toBeVisible();
  await editRegions.click();
  await expect(editRegions).toHaveAttribute("aria-pressed", "false");
  await turn(page, "page-1", () =>
    page.getByRole("button", { name: /^Next page/ }).click(),
  );
  await expect(
    page.getByRole("button", { name: "Bubble 1: Oh non, il pleut !" }),
  ).toBeVisible();
  await turn(page, "cover", () =>
    page.getByRole("button", { name: /^Previous page/ }).click(),
  );
  await expect(
    page.getByRole("button", { name: "Bubble 1: Le parapluie de Lulu" }),
  ).toBeVisible();
});

test("tapping a bubble explains it, and Back returns to the library", async ({
  page,
}) => {
  await turn(page, "cover", () =>
    page.goto("/reader/lulu-example-le-parapluie/"),
  );
  await turn(page, "page-1", () =>
    page.getByRole("button", { name: /^Next page/ }).click(),
  );

  await page
    .getByRole("button", { name: "Bubble 1: Oh non, il pleut !" })
    .click();
  await expect(page.getByText("Oh no, it's raining!").first()).toBeVisible();
  await looksLike(page, "reader-bubble");

  // Reload must resume the persisted page, rather than the optimistic state we just saw.
  await turn(page, "page-1", () => page.reload());
  await expect(
    page.getByRole("button", { name: "Bubble 1: Oh non, il pleut !" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Back to library" }).click();
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeVisible();
});
