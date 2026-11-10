import { defineConfig, devices } from "@playwright/test";

/**
 * Browser tests of whole journeys through the built app (`e2e/`). They replay API responses
 * recorded from the real Java server (`e2e/api.mjs`); `RECORD=1` records them again, against a
 * real server started with a throwaway library (`e2e/real-server.mjs`). The ports differ from
 * `npm run dev`'s, so the tests can run beside it.
 */
const RECORD = Boolean(process.env.RECORD);
const WEB_PORT = 18875;

export default defineConfig({
  testDir: "e2e",
  // Reference screenshots, made in Playwright's Docker image (`npm run e2e:screenshots`).
  snapshotPathTemplate: "{testDir}/screenshots/{testFileName}/{arg}{ext}",
  // Chromium sometimes smooths the same text a shade differently from run to run (a few dozen
  // pixels on the reader's toolbar); a moved or restyled element changes thousands.
  expect: { toHaveScreenshot: { maxDiffPixels: 100 } },
  // Recording shares one real server, so it runs one test at a time.
  fullyParallel: !RECORD,
  workers: RECORD ? 1 : undefined,
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  globalSetup: RECORD ? "./e2e/real-server.mjs" : undefined,
  use: {
    baseURL: `http://127.0.0.1:${WEB_PORT}`,
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        // Videos wait for a key press or click before playing, as on a site the browser hasn't
        // seen used, so a test decides where playback is and recordings don't vary with timing.
        launchOptions: { args: ["--autoplay-policy=user-gesture-required"] },
      },
    },
  ],
  webServer: {
    command: "npx vite build && node server.mjs",
    url: `http://127.0.0.1:${WEB_PORT}/`,
    env: { PORT: String(WEB_PORT), API_TARGET: "http://127.0.0.1:18877" },
    reuseExistingServer: !process.env.CI,
  },
});
