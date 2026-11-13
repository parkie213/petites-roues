import { defineConfig, devices } from "@playwright/test";

/** Exercises the built Pages artifact with a plain static server, without API fixtures. */
export default defineConfig({
  testDir: "demo-test",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  use: {
    baseURL: "http://127.0.0.1:18876",
    trace: "retain-on-failure",
    launchOptions: { args: ["--autoplay-policy=user-gesture-required"] },
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    {
      name: "mobile",
      use: { ...devices["iPhone 13"], defaultBrowserType: "chromium" },
    },
  ],
  webServer: {
    command:
      "python3 ../site/build.py && python3 -m http.server 18876 --bind 0.0.0.0 --directory ../site/dist",
    url: "http://127.0.0.1:18876/demo/",
    reuseExistingServer: false,
  },
});
