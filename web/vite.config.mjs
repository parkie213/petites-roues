import { defineConfig, searchForWorkspaceRoot } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  // The quiz share bundles the quiz workflow from the repository's agents/.
  server: {
    fs: { allow: [searchForWorkspaceRoot(process.cwd()), "../agents"] },
  },
  // Vitest: plain modules and the Node server run in Node; a test that renders React opts into a
  // simulated browser with a `// @vitest-environment jsdom` comment. The Playwright journeys in
  // e2e/ are run separately.
  test: {
    include: ["test/**/*.test.{mjs,jsx}"],
    // Rendered components see Vitest's stand-ins for globals (fetch, Audio) undone after each test.
    unstubGlobals: true,
    environment: "node",
    restoreMocks: true,
    setupFiles: ["test/setup.mjs"],
  },
});
