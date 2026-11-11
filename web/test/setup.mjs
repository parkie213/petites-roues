import { afterEach } from "vitest";

/**
 * Shared setup for tests that render React (`// @vitest-environment jsdom`): the browser APIs
 * Mantine uses that jsdom lacks, and unmounting what each test rendered. Plain Node tests skip it.
 */
if (typeof globalThis.window !== "undefined") {
  const { cleanup } = await import("@testing-library/react");
  afterEach(cleanup);
  globalThis.window.matchMedia ??= (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent: () => false,
  });
  // Mantine's auto-sizing textarea measures again once web fonts load.
  globalThis.document.fonts ??= {
    ready: Promise.resolve(),
    addEventListener() {},
    removeEventListener() {},
  };
  globalThis.window.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}
