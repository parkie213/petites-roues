import { test, expect, expectWrite, readJson } from "./api.mjs";

const entries = (library) =>
  library.repositories
    .flatMap((repo) => repo.entries.map((entry) => entry.id))
    .sort();

test("Reset library rebuilds the library from its folders", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.getByText("2 videos · 1 comic")).toBeVisible();
  const before = await readJson(page, "/api/library");
  expect(entries(before).length).toBeGreaterThan(0);

  // Nothing is pending, so the item is enabled; choosing it only asks.
  await page.getByRole("button", { name: "More library actions" }).click();
  await page.getByRole("menuitem", { name: "Reset library" }).click();
  const dialog = page.getByRole("dialog", { name: "Reset library?" });
  await expect(dialog).toContainText("Reading and playback positions");
  await expect(dialog).toContainText("The registered folders");

  const job = await expectWrite(
    page,
    { method: "POST", path: "/api/jobs", body: { kind: "reset" } },
    () => dialog.getByRole("button", { name: "Reset library" }).click(),
  );
  expect(job).toMatchObject({ kind: "reset" });
  expect(job.id).toBeTruthy();

  await expect(
    page.getByText("Library reset and rebuilt.", { exact: true }),
  ).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("2 videos · 1 comic")).toBeVisible();
  expect(await readJson(page, "/api/jobs/latest")).toMatchObject({
    kind: "reset",
    state: "succeeded",
  });
  const after = await readJson(page, "/api/library");
  expect(entries(after)).toEqual(entries(before));
  expect(after.repositories.map((repo) => repo.repository.id).sort()).toEqual(
    before.repositories.map((repo) => repo.repository.id).sort(),
  );
  await page.reload();
  await expect(page.getByText("2 videos · 1 comic")).toBeVisible();
});
