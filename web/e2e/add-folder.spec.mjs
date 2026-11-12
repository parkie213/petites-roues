import path from "node:path";
import { test, expect, expectWrite, readJson } from "./api.mjs";

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

  const chosen = expectWrite(
    page,
    { method: "POST", path: "/api/files/choose", body: {} },
    async () => {
      const job = await expectWrite(
        page,
        {
          method: "POST",
          path: "/api/jobs",
          body: { kind: "add", localPath: folder },
        },
        () => page.getByRole("button", { name: "Add folder" }).first().click(),
      );
      expect(job).toMatchObject({ kind: "add" });
      expect(job.id).toBeTruthy();
    },
  );
  expect((await chosen).path.endsWith("/example/media-de")).toBe(true);

  await expect(page.getByText("3 videos · 1 comic")).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText("Folder added.", { exact: true })).toBeVisible();
  const repositories = await readJson(page, "/api/repositories");
  expect(
    repositories.some((repo) => repo.localPath.endsWith("/example/media-de")),
  ).toBe(true);
  const job = await readJson(page, "/api/jobs/latest");
  expect(job).toMatchObject({ kind: "add", state: "succeeded" });
  await page.reload();
  await expect(page.getByText("3 videos · 1 comic")).toBeVisible();
});
