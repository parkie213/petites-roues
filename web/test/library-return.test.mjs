import { test } from "vitest";
import assert from "node:assert/strict";
import {
  libraryReturnPath,
  rememberLibraryPage,
} from "../src/shared/library-return.ts";

test("viewers return to the library page last shown, else the default view", () => {
  const store = new Map();
  globalThis.sessionStorage = {
    getItem: (key) => store.get(key) ?? null,
    setItem: (key, value) => store.set(key, value),
  };
  assert.equal(libraryReturnPath(), "/");
  rememberLibraryPage("/?view=posters&group=series%3Apeppa+pig");
  assert.equal(libraryReturnPath(), "/?view=posters&group=series%3Apeppa+pig");
  rememberLibraryPage("https://elsewhere.example/");
  assert.equal(libraryReturnPath(), "/", "only library pages are returned to");
  globalThis.sessionStorage = {
    getItem: () => {
      throw Error("blocked");
    },
    setItem: () => {
      throw Error("blocked");
    },
  };
  rememberLibraryPage("/?view=documents");
  assert.equal(libraryReturnPath(), "/");
  delete globalThis.sessionStorage;
});
