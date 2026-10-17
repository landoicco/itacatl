// The /vitest entry registers the matchers and their types on Vitest's expect
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeAll } from "vitest";
import i18n from "@/i18n";

// Tests assert on English text, whatever the default locale of the build
beforeAll(async () => {
  await i18n.changeLanguage("en");
});

// Clean virtual DOM after each test
afterEach(() => {
  cleanup();
});
