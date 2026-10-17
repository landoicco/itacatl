import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import { defineConfig, globalIgnores } from "eslint/config";

// Dependency rules between layers (docs/architecture.md): app composes features, features use the
// shared layers, and nothing depends on a feature but app. A broken rule fails lint and CI.
const FEATURE_IMPORTS = {
  group: ["@/features/*", "@/app/*", "../*"],
  message:
    "A feature must not import another feature or the app. Move shared code to components/, api/, theme/ or i18n/.",
};
const SHARED_IMPORTS = {
  group: ["@/features/*", "@/app/*"],
  message: "Shared code must not depend on features or the app.",
};

export default defineConfig([
  globalIgnores(["dist"]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    files: ["src/features/**/*.{ts,tsx}"],
    rules: { "no-restricted-imports": ["error", { patterns: [FEATURE_IMPORTS] }] },
  },
  {
    files: ["src/{api,components,i18n,theme}/**/*.{ts,tsx}"],
    rules: { "no-restricted-imports": ["error", { patterns: [SHARED_IMPORTS] }] },
  },
]);
