import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

export default defineConfig({
  plugins: [react()],
  root: fileURLToPath(new URL("./demo", import.meta.url)),
  base: "./",
  publicDir: false,
  build: {
    outDir: fileURLToPath(new URL("../site/dist/demo", import.meta.url)),
    emptyOutDir: true,
  },
});
