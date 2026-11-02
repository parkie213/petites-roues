import { defineConfig, searchForWorkspaceRoot } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  // The quiz share bundles the quiz workflow from the repository's agents/.
  server: {
    fs: { allow: [searchForWorkspaceRoot(process.cwd()), "../agents"] },
  },
});
