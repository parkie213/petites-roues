import { test, expect, expectWrite, readJson, looksLike } from "./api.mjs";

test("Installed voices can be selected per language and restored after reload", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Read-aloud voices" }).click();
  const french = page.getByLabel("French", { exact: true });
  await expect(french).toHaveValue("");
  await expect(french.locator("option")).toHaveText([
    "Automatic (fr_FR-a.onnx)",
    "fr_FR-a.onnx",
    "fr_FR-b.onnx",
  ]);
  await looksLike(page, "speech-voices");
  const saved = await expectWrite(
    page,
    {
      method: "PUT",
      path: "/api/speech/voices/fr",
      body: { voice: "fr_FR-b.onnx" },
    },
    () => french.selectOption("fr_FR-b.onnx"),
  );
  expect(saved[0]).toMatchObject({
    language: "fr",
    selectedVoice: "fr_FR-b.onnx",
  });
  expect((await readJson(page, "/api/speech/voices"))[0].selectedVoice).toBe(
    "fr_FR-b.onnx",
  );
  await expect(french).toHaveValue("fr_FR-b.onnx");
  await page.reload();
  await page.getByRole("button", { name: "Read-aloud voices" }).click();
  await expect(french).toHaveValue("fr_FR-b.onnx");
  const automatic = await expectWrite(
    page,
    {
      method: "PUT",
      path: "/api/speech/voices/fr",
      body: { voice: null },
    },
    () => french.selectOption(""),
  );
  expect(automatic[0].selectedVoice).toBeNull();
  expect(
    (await readJson(page, "/api/speech/voices"))[0].selectedVoice,
  ).toBeNull();
  await expect(french).toHaveValue("");
});

test("Browse, compare, install and preview a voice without losing the choice", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Read-aloud voices" }).click();
  await page
    .getByRole("button", { name: "Browse voices", exact: true })
    .click();
  await expect(page.getByLabel("Catalogue language")).toHaveValue("fr");
  const voice = page.getByRole("region", { name: "comparison fr-FR medium" });
  await expect(voice).toBeVisible();
  await expect(
    voice.getByRole("link", { name: "Model card & license" }),
  ).toBeVisible();
  await page.getByLabel("Search voices").fill("unmatched");
  await expect(page.getByText("No matching voices.")).toBeVisible();
  await page.getByLabel("Search voices").fill("comparison");
  await expect(voice).toBeVisible();
  await looksLike(page, "voice-catalogue");
  await voice.getByRole("button", { name: "Listen to comparison" }).click();
  const audio = page.getByLabel("Voice comparison");
  await expect
    .poll(() => audio.evaluate((element) => element.currentTime))
    .toBeGreaterThan(0);
  const installed = await expectWrite(
    page,
    {
      method: "POST",
      path: "/api/speech/voices/catalogue/fr_FR-comparison-medium/install",
    },
    () => voice.getByRole("button", { name: "Install comparison" }).click(),
  );
  expect(installed[0].voices).toContain("fr_FR-comparison-medium.onnx");
  expect((await readJson(page, "/api/speech/voices"))[0].voices).toContain(
    "fr_FR-comparison-medium.onnx",
  );
  await expect(voice.getByText("Installed", { exact: true })).toBeVisible();
  await page
    .getByLabel("Text for installed voice previews")
    .fill("Bonjour ! Comment allez-vous ?");
  await voice.getByRole("button", { name: "Preview comparison" }).click();
  await expect(audio).toHaveAttribute(
    "src",
    /fr\/preview\?text=.*&voice=fr_FR-comparison-medium.onnx/,
  );
  await expect
    .poll(() => audio.evaluate((element) => element.currentTime))
    .toBeGreaterThan(0);
  await expect(page.getByRole("alert")).not.toBeVisible();
  expect(
    (await readJson(page, "/api/speech/voices"))[0].selectedVoice,
  ).toBeNull();
  const saved = await expectWrite(
    page,
    {
      method: "PUT",
      path: "/api/speech/voices/fr",
      body: { voice: "fr_FR-comparison-medium.onnx" },
    },
    () =>
      page
        .getByLabel("French", { exact: true })
        .selectOption("fr_FR-comparison-medium.onnx"),
  );
  expect(saved[0].selectedVoice).toBe("fr_FR-comparison-medium.onnx");
  await page.reload();
  await page.getByRole("button", { name: "Read-aloud voices" }).click();
  await expect(page.getByLabel("French", { exact: true })).toHaveValue(
    "fr_FR-comparison-medium.onnx",
  );
});
