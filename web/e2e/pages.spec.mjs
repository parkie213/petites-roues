import { test, expect } from "./api.mjs";

test("Notes shows the example's note", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Notes" }).click();

  await expect(
    page.getByRole("heading", { name: "Verbs followed by an infinitive" }),
  ).toBeVisible();
  await expect(
    page.getByText("J'adore sauter dans les flaques.", { exact: false }),
  ).toBeVisible();
});

test("Quiz me shows the example's quiz, ready to continue in a chat", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Quiz me" }).click();

  await expect(
    page.getByRole("heading", { name: "Verbs followed by an infinitive" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /Claude/ })).toBeVisible();
});
