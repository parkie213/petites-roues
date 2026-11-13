import {
  test,
  expect,
  expectWrite,
  readJson,
  looksLike,
  RECORD,
} from "./api.mjs";

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
  await page.getByRole("tab", { name: "Find voices", exact: true }).click();
  await expect(page.getByLabel("Catalogue language")).toHaveValue("fr");
  await expect(page.getByText(/^Catalogue fetched /)).toBeVisible();
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
  await page.getByRole("tab", { name: "Installed", exact: true }).click();
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

test("Catalogue freshness stays visible when the provider is unavailable", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Read-aloud voices" }).click();
  await page.getByRole("tab", { name: "Find voices", exact: true }).click();
  const first = await readJson(page, "/api/speech/voices/catalogue");
  expect(first.voices.length).toBeGreaterThan(0);
  await expect(page.getByText(/^Catalogue fetched /)).toBeVisible();
  if (RECORD) await fetch("http://127.0.0.1:18879/outage", { method: "POST" });
  try {
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(
      page.getByText(
        "Could not refresh the catalogue. Showing the last available copy.",
      ),
    ).toBeVisible();
    const saved = await readJson(page, "/api/speech/voices/catalogue");
    expect(saved.voices).toEqual(first.voices);
    expect(saved.fetchedAt).toBe(first.fetchedAt);
    expect(saved.warning).toBeTruthy();
    await looksLike(page, "catalogue-offline");
  } finally {
    if (RECORD)
      await fetch("http://127.0.0.1:18879/restore", { method: "POST" });
  }
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByText(
      "Could not refresh the catalogue. Showing the last available copy.",
    ),
  ).not.toBeVisible();
  expect(
    (await readJson(page, "/api/speech/voices/catalogue")).warning,
  ).toBeNull();
});
