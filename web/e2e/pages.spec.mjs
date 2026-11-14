import { test, expect, looksLike } from "./api.mjs";

test("Notes shows the example's note", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Notes" }).click();

  await expect(
    page.getByRole("heading", { name: "Verbs followed by an infinitive" }),
  ).toBeVisible();
  await expect(
    page.getByText("J'adore sauter dans les flaques.", { exact: false }),
  ).toBeVisible();
  await looksLike(page, "notes");
});

test("Practice shows the example's quiz, ready to continue in a chat", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Practice" }).click();

  const topics = page.getByRole("navigation", { name: "Choose a quiz" });
  await expect(topics.getByRole("link")).toHaveText([
    "A1 Conversation",
    "Verbs followed by an infinitive",
  ]);
  await topics
    .getByRole("link", { name: "Verbs followed by an infinitive" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Verbs followed by an infinitive" }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /Claude/ })).toBeVisible();
});
