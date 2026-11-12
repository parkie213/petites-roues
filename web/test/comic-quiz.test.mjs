import { expect, test, vi } from "vitest";
import { prepareChapterQuiz } from "../src/comic/quiz.js";

test("requests chapter identity and selected instructions without a document copy", async () => {
  const fetch = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ text: "# Prepared quiz\n", fileName: "chapter.md" }),
  });
  vi.stubGlobal("fetch", fetch);
  const signal = new AbortController().signal;
  const result = await prepareChapterQuiz(
    "a/b",
    "first-page",
    "# Rules",
    { id: "vocabulary", label: "Vocabulary", instructions: "Ask about words." },
    signal,
  );
  expect(result).toEqual({ text: "# Prepared quiz\n", fileName: "chapter.md" });
  expect(fetch.mock.calls[0][0]).toBe("/api/titles/a%2Fb/comic/quiz");
  expect(fetch.mock.calls[0][1].signal).toBe(signal);
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
    chapterPageId: "first-page",
    rules: "# Rules",
    type: { label: "Vocabulary", instructions: "Ask about words." },
  });
});

test("propagates preparation errors instead of constructing a local fallback", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      text: async () => '{"error":"Unknown chapter"}',
    }),
  );
  await expect(prepareChapterQuiz("comic", "gone", "", null)).rejects.toThrow(
    "Unknown chapter",
  );
});
