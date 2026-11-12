import { test, expect, expectWrite, readJson } from "./api.mjs";

const api = "/api/titles/lulu-example-le-parapluie/comic";
const firstLine = (document) => document.comic.pages[0].boxes[0].lines[0];

async function openCover(page, action) {
  expect(
    await expectWrite(
      page,
      {
        method: "PUT",
        path: api + "/progress",
        body: { pageId: "cover" },
      },
      action,
    ),
  ).toMatchObject({ pageId: "cover" });
  expect(await readJson(page, api + "/progress")).toMatchObject({
    pageId: "cover",
  });
}

test("edit mode saves a transcription correction and restores it after reload", async ({
  page,
}) => {
  await openCover(page, () => page.goto("/reader/lulu-example-le-parapluie/"));
  const original = firstLine(await readJson(page, api));
  await page.getByRole("button", { name: "Edit mode", exact: true }).click();
  const panel = page.getByRole("complementary", { name: "Bubble editing" });
  await panel.getByRole("button", { name: "Edit text", exact: true }).click();
  const source = "Le parapluie de Lulu !";
  await panel.getByRole("textbox", { name: "French text" }).fill(source);
  const saved = await expectWrite(
    page,
    {
      method: "PUT",
      path: api + "/lines/" + original.id,
      body: { expectedSource: original.source, source },
    },
    () => panel.getByRole("button", { name: "Save text", exact: true }).click(),
  );
  expect(firstLine(saved)).toMatchObject({ source, notes: [] });
  expect(firstLine(saved).translation).toBeFalsy();
  expect(firstLine(saved).alignment).toBeFalsy();
  expect(firstLine(await readJson(page, api)).source).toBe(source);
  await expect(
    panel.getByRole("button", { name: "Edit text", exact: true }),
  ).toBeVisible();
  await openCover(page, () => page.reload());
  await page.getByRole("button", { name: "Edit mode", exact: true }).click();
  await expect(panel.getByText(source, { exact: true })).toBeVisible();
});
