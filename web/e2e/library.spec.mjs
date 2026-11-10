import { test, expect } from "./api.mjs";

test("the library shelves the example's comic and series", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("2 videos · 1 comic")).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Lulu", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeVisible();
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
});

test("the library flags what needs attention, and can show only that", async ({
  page,
}) => {
  await page.goto("/");

  // One Peppa Pig episode's video has no study document in the example.
  const attention = page.getByRole("button", { name: "Needs attention · 1" });
  await attention.click();

  await expect(attention).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("link", { name: "Peppa Pig" })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Lulu", exact: true }),
  ).toBeHidden();
});
