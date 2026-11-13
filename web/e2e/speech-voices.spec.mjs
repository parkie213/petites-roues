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
