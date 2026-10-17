// The /vitest entry registers the matchers and their types on Vitest's expect
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, vi } from "vitest";
import i18n from "@/i18n";
import { server } from "./server";

beforeAll(async () => {
  // Tests assert on English text, whatever the default locale of the build
  await i18n.changeLanguage("en");
  // A request without a handler is a bug in the test, not something to ignore. MSW 3 calls it a
  // "frame", since it covers WebSocket connections too
  server.listen({ onUnhandledFrame: "error" });
});

afterEach(() => {
  // Clean virtual DOM and any handler a test overrode
  cleanup();
  server.resetHandlers();
});

afterAll(() => {
  server.close();
});

// jsdom does not implement scrolling, which the router's scroll restoration calls
window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
