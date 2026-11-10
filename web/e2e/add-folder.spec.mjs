import path from "node:path";
import { test, expect } from "./api.mjs";

const folder = path.resolve(import.meta.dirname, "../../example/media-de");

test("Add folder adds the chosen folder's titles to the library", async ({
  page,
}) => {
  // The server would open this computer's own folder dialog; the test chooses for it.
  await page.route("**/api/files/choose", (route) =>
    route.fulfill({ json: { path: folder } }),
  );
  await page.goto("/");
  await expect(page.getByText("2 videos · 1 comic")).toBeVisible();

  await page.getByRole("button", { name: "Add folder" }).first().click();

  await expect(page.getByText("3 videos · 1 comic")).toBeVisible({
    timeout: 30_000,
  });
});
