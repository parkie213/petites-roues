import { test, expect } from "@playwright/test";

// This is browser-local persistence: there are no API writes or recordings in the static demo.
test("the landing page opens a one-comic library and a readable, resumable comic", async ({
  page,
}, info) => {
  const failures = [];
  page.on("pageerror", (error) => failures.push(error.message));
  page.on("response", (response) => {
    if (
      new URL(response.url()).origin === "http://127.0.0.1:18876" &&
      response.status() >= 400
    )
      failures.push(`${response.status()} ${response.url()}`);
  });
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.startsWith("/api/"))
      failures.push(`Unexpected API request: ${request.url()}`);
  });
  await page.goto("/");
  await expect(page.getByRole("link", { name: "GitHub" })).toHaveAttribute(
    "href",
    "https://github.com/parkie213/petites-roues",
  );
  await page.getByRole("link", { name: "Demo", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Library", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Comics" }).getByRole("link"),
  ).toHaveCount(1);
  await page.getByRole("link", { name: /Le parapluie de Lulu/ }).click();
  const image = page.getByRole("img", {
    name: "Le parapluie de Lulu, page 1",
    exact: true,
  });
  await expect(image).toBeVisible();
  await expect
    .poll(() => image.evaluate((img) => img.complete && img.naturalWidth > 0))
    .toBe(true);
  await expect(
    page.getByRole("button", { name: "Previous page (←)", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: /^Next page/ }).click();
  await expect(
    page.getByRole("img", {
      name: "Le parapluie de Lulu, page 2",
      exact: true,
    }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Bubble 1: Oh non, il pleut !" })
    .click();
  await expect(
    page.getByText("Oh no, it's raining!", { exact: false }),
  ).toBeVisible();
  const source = page.getByRole("button", { name: "Oh non,", exact: true });
  const translation = page.getByRole("button", { name: "Oh no,", exact: true });
  await expect(translation).toBeVisible();
  expect(
    await translation.evaluate(
      (el) => el.ownerDocument.defaultView.getComputedStyle(el).backgroundColor,
    ),
  ).toBe(
    await source.evaluate(
      (el) => el.ownerDocument.defaultView.getComputedStyle(el).backgroundColor,
    ),
  );
  await source.click();
  await expect(translation).toHaveAttribute("data-active", "true");
  await expect(page.getByText("Non = no.", { exact: true })).toBeVisible();
  await expect(
    page.getByText("Hover or click a phrase for its explanation.", {
      exact: true,
    }),
  ).toBeHidden();
  await page.screenshot({ path: info.outputPath("reader.png") });
  await expect
    .poll(() =>
      page.evaluate(() => JSON.parse(localStorage.getItem("demo:lulu:page"))),
    )
    .toBe(1);
  await page.reload();
  await expect(
    page.getByRole("img", {
      name: "Le parapluie de Lulu, page 2",
      exact: true,
    }),
  ).toBeVisible();
  for (const number of [3, 4]) {
    await page.getByRole("button", { name: /^Next page/ }).click();
    const nextImage = page.getByRole("img", {
      name: `Le parapluie de Lulu, page ${number}`,
      exact: true,
    });
    await expect
      .poll(() =>
        nextImage.evaluate((img) => img.complete && img.naturalWidth > 0),
      )
      .toBe(true);
  }
  await expect(page.getByRole("button", { name: /^Next page/ })).toBeDisabled();
  await page.getByRole("link", { name: "Back to library" }).click();
  await expect(
    page.getByRole("heading", { name: "Library", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("4 pages · Continue on page 4")).toBeVisible();
  await page.screenshot({ path: info.outputPath("library.png") });
  expect(failures).toEqual([]);
});
