import { test, vi } from "vitest";
import assert from "node:assert/strict";
import { playerDocument, request } from "../src/shared/api.js";
import { alignmentState } from "../src/player/alignment.js";

test("nested video responses retain zero timing, annotations and optional translation absence", () => {
  const wire = {
    id: "film",
    sourceLanguage: "fr",
    translationLanguage: "en",
    notesLanguage: "en",
    video: {
      cues: [
        {
          start: 0,
          end: 2,
          listeningCheck: "Listen again",
          line: { id: "cue", source: "Bonjour !", notes: ["A greeting."] },
        },
      ],
    },
  };
  const before = structuredClone(wire);
  const view = playerDocument(wire);
  assert.equal(view.type, "video");
  assert.equal(view.cues[0].start, 0);
  assert.equal(view.cues[0].id, "cue");
  assert.equal(view.cues[0].translation, undefined);
  assert.equal(view.cues[0].listeningCheck, "Listen again");
  assert.deepEqual(view.cues[0].notes, ["A greeting."]);
  assert.deepEqual(wire, before);
});

test("comic boxes come out of their pages, each knowing its page, order and lines", () => {
  const box = (id, lineId) => ({
    id,
    regions: [{ x: 0, y: 0, width: 1, height: 1 }],
    lines: [{ id: lineId, source: "Salut", translation: "Hi" }],
  });
  const wire = {
    id: "book",
    comic: {
      readingDirection: "rtl",
      pages: [
        { id: "p1", entry: "001.jpg", boxes: [box("a", "la"), box("b", "lb")] },
        { id: "p2", entry: "002.jpg", boxes: [box("c", "lc")] },
      ],
    },
  };
  const before = structuredClone(wire);
  const view = playerDocument(wire);
  assert.equal(view.cues, undefined);
  assert.equal(view.readingDirection, "rtl");
  assert.deepEqual(view.pages, [
    { id: "p1", entry: "001.jpg" },
    { id: "p2", entry: "002.jpg" },
  ]);
  assert.deepEqual(
    view.boxes.map((b) => [b.id, b.pageId, b.order]),
    [
      ["a", "p1", 1],
      ["b", "p1", 2],
      ["c", "p2", 1],
    ],
  );
  assert.equal(view.boxes[0].regions[0].width, 1);
  assert.deepEqual(view.boxes[1].lines, [
    {
      id: "lb",
      source: "Salut",
      translation: "Hi",
      sourceSeparator: " ",
      translationSeparator: " ",
      pageId: "p1",
      boxId: "b",
    },
  ]);
  assert.deepEqual(wire, before);
});

test("chunks of a language written without spaces join directly", () => {
  const view = playerDocument({
    sourceLanguage: "zh-Hans",
    translationLanguage: "en",
    video: {
      cues: [
        {
          start: 0,
          end: 1,
          line: {
            id: "c",
            source: "我们去公园。",
            translation: "We go to the park.",
            alignment: {
              source: ["我们", "去", "公园。"],
              translation: ["We", "go", "to the park."],
              links: [{ source: [0, 1, 2], translation: [0, 1, 2] }],
            },
          },
        },
      ],
    },
  });
  const [cue] = view.cues;
  assert.equal(cue.sourceSeparator, "");
  assert.equal(cue.translationSeparator, " ");
  assert.equal(alignmentState(cue), "ready");
  assert.equal(alignmentState({ ...cue, source: "我们 去 公园。" }), "missing");
});

test("request leaves local catalogue and review records outside the portable document model", async () => {
  const records = [{ cueIds: ["cue"], kind: "edit" }];
  vi.spyOn(globalThis, "fetch").mockImplementation(async () => ({
    ok: true,
    json: async () => records,
  }));
  assert.equal(await request("/review-queue"), records);
});

test("a line of a box awaiting text has an empty source for later text edits", () => {
  const document = playerDocument({
    comic: {
      pages: [
        {
          id: "page",
          entry: "001.jpg",
          boxes: [
            {
              id: "box",
              regions: [{ x: 0.1, y: 0.2, width: 0.3, height: 0.4 }],
              lines: [{ id: "pending" }],
            },
          ],
        },
      ],
    },
  });
  assert.equal(document.readingDirection, "ltr");
  assert.equal(document.boxes[0].id, "box");
  assert.equal(document.boxes[0].lines[0].source, "");
  assert.equal(document.boxes[0].regions.length, 1);
});
