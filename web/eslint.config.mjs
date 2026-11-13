import js from "@eslint/js";
import { defineConfig } from "eslint/config";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

/**
 * ESLint's recommended rules and the two classic React hooks rules: hooks are called in the same
 * order every render, and effects, memos and callbacks list everything they read. `npm run lint`;
 * CI runs it too.
 *
 * The plugin's React Compiler rules (refs, immutability, set-state-in-effect, purity) are left
 * off: they flag code the compiler could not optimise rather than bugs, and adopting them is its
 * own piece of work.
 *
 * TypeScript files add typescript-eslint's recommended rules; `npm run typecheck` checks their types.
 */
export default defineConfig([
  {
    ignores: [
      "dist/",
      "content/",
      "node_modules/",
      "test-results/",
      "playwright-report/",
    ],
  },
  js.configs.recommended,
  {
    plugins: { "react-hooks": reactHooks },
    rules: {
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "error",
      // `const { omitted, ...rest } = value` is how a property is dropped.
      "no-unused-vars": ["error", { ignoreRestSiblings: true }],
    },
  },
  {
    files: ["src/**/*.{js,jsx,ts,tsx}"],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
  },
  {
    files: ["**/*.{ts,tsx}"],
    extends: [tseslint.configs.recommended],
    rules: {
      "no-unused-vars": "off",
      "@typescript-eslint/no-unused-vars": [
        "error",
        { ignoreRestSiblings: true },
      ],
    },
  },
  {
    files: ["*.mjs", "test/**/*.mjs", "e2e/**/*.mjs", "demo-test/**/*.mjs"],
    languageOptions: { globals: globals.node },
  },
  {
    // Tests that render components run in jsdom, a simulated browser inside Node.
    files: ["test/**/*.jsx"],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
  },
]);
