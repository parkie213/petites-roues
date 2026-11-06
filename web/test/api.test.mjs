import test from "node:test";
import assert from "node:assert/strict";
import { playerDocument, request } from "../src/shared/api.js";

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

test("comic boxes keep their own id and placement, and their lines know their box", () => {
  const wire = {
    id: "book",
    comic: {
      readingDirection: "rtl",
      pages: [{ id: "p1", entry: "001.jpg" }],
      boxes: [
        {
          id: "box",
          pageId: "p1",
          order: 1,
          regions: [{ x: 0, y: 0, width: 1, height: 1 }],
          lines: [{ id: "bubble", source: "Salut", translation: "Hi" }],
        },
      ],
    },
  };
  const view = playerDocument(wire);
  const [box] = view.boxes;
  assert.equal(view.cues, undefined);
  assert.equal(view.readingDirection, "rtl");
  assert.equal(box.id, "box");
  assert.equal(box.pageId, "p1");
  assert.equal(box.regions[0].width, 1);
  assert.deepEqual(box.lines, [
    {
      id: "bubble",
      source: "Salut",
      translation: "Hi",
      pageId: "p1",
      boxId: "box",
    },
  ]);
});

test("request leaves local catalogue and review records outside the portable document model", async (t) => {
  const records = [{ cueIds: ["cue"], kind: "edit" }];
  t.mock.method(globalThis, "fetch", async () => ({
    ok: true,
    json: async () => records,
  }));
  assert.equal(await request("/review-queue"), records);
});

test("a line of a box awaiting text has an empty source for later text edits", () => {
  const document = playerDocument({
    comic: {
      boxes: [
        {
          id: "box",
          pageId: "page",
          order: 1,
          regions: [{ x: 0.1, y: 0.2, width: 0.3, height: 0.4 }],
          lines: [{ id: "pending" }],
        },
      ],
    },
  });
  assert.equal(document.readingDirection, "ltr");
  assert.equal(document.boxes[0].id, "box");
  assert.equal(document.boxes[0].lines[0].source, "");
  assert.equal(document.boxes[0].regions.length, 1);
});
