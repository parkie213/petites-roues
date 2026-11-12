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
  const editRegions = page.locator(".comic-region-edit-toggle");
  await expect(editRegions).toHaveAccessibleName("Edit mode");
  await expect(editRegions).toHaveAttribute("aria-pressed", "false");
  const editBounds = await editRegions.boundingBox();
  await editRegions.click();
  await expect(editRegions).toHaveAttribute("aria-pressed", "true");
  await expect(editRegions).toHaveAccessibleName("Exit edit mode");
  await expect(editRegions).toHaveCSS("background-color", "rgb(255, 213, 79)");
  expect(await editRegions.boundingBox()).toEqual(editBounds);
  await expect(page.getByText(/Region changes save automatically/)).toHaveCount(
    0,
  );
  await page.getByRole("button", { name: "Editing help" }).click();
  await expect(
    page.getByText(/Region changes save automatically/),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  const bubble = page.getByRole("button", {
    name: "Bubble 1: Le parapluie de Lulu",
  });
  await bubble.click();
  await expect(
    page.getByRole("button", { name: "Delete selected bubble" }),
  ).toBeVisible();
  const bounds = await bubble.boundingBox();
  const handles = bubble.locator(".comic-region-handle");
  await expect(handles).toHaveCount(4);
  for (const [index, [right, bottom]] of [
    [false, false],
    [true, false],
    [false, true],
    [true, true],
  ].entries()) {
    const handle = await handles.nth(index).boundingBox();
    expect(
      Math.abs(
        handle.x + handle.width / 2 - (bounds.x + (right ? bounds.width : 0)),
      ),
    ).toBeLessThan(4);
    expect(
      Math.abs(
        handle.y +
          handle.height / 2 -
          (bounds.y + (bottom ? bounds.height : 0)),
      ),
    ).toBeLessThan(4);
  }

  const editor = page.getByRole("complementary", { name: "Bubble editing" });
  await expect(editor).toBeVisible();
  await editor.getByRole("button", { name: "Edit text", exact: true }).click();
  const transcription = editor.getByRole("textbox");
  const original = await transcription.inputValue();
  await transcription.fill("A corrected transcription");
  await expect(editRegions).toBeDisabled();
  await expect(page.getByRole("button", { name: /^Next page/ })).toBeDisabled();
  await editor.getByRole("button", { name: "Cancel", exact: true }).click();
  await expect(
    editor.getByText(original, { exact: true }).first(),
  ).toBeVisible();
  await editRegions.click();
  await expect(editor).toHaveCount(0);
  await expect(editRegions).toHaveAttribute("aria-pressed", "false");
  expect(await editRegions.boundingBox()).toEqual(editBounds);
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
