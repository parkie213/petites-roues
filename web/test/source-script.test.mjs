import { expect, test } from "vitest";
import { hasSourceScript } from "../src/comic/source-script.js";

test.each([
  ["ドドド", "fr", false],
  ["漢字", "fr", false],
  ["…?!", "fr", false],
  ["Écoute !", "fr-FR", true],
  ["ADDENTZION", "fr", true],
  ["ドドド", "ja", true],
  ["漢字", "ja", true],
  ["你好", "zh-CN", true],
  ["你好", "zh-TW", true],
  ["안녕", "ko", true],
  ["Привет", "ru", true],
])(
  "%s in %s is eligible for auto-listening: %s",
  (text, language, eligible) => {
    expect(hasSourceScript(text, language)).toBe(eligible);
  },
);
