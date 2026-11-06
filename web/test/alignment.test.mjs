import test from "node:test";
import assert from "node:assert/strict";
import {
  phraseTranslation,
  validAlignment,
  alignmentState,
  pendingReviewCount,
} from "../src/player/alignment.js";

const cue = {
  source: "Ils ont hâte de nous rencontrer.",
  translation: "They can't wait to meet us.",
};
const alignment = {
  source: ["Ils", "ont hâte de", "nous", "rencontrer."],
  translation: ["They", "can't wait to", "meet", "us."],
  links: [
    { source: [0], translation: [0], note: "They." },
    { source: [1], translation: [1], note: "An expression of eagerness." },
    { source: [2], translation: [3], note: "Object pronouns change position." },
    { source: [3], translation: [2], note: "Meet." },
  ],
};
test("a line has a ready breakdown or is missing one; only queued edits are pending", () => {
  assert.equal(alignmentState(cue), "missing");
  assert.equal(alignmentState({ ...cue, alignment }), "ready");
  // A breakdown that no longer spells out the line is never shown.
  assert.equal(
    alignmentState({ ...cue, source: "Autre chose.", alignment }),
    "missing",
  );
  const changes = [{ sequence: 1, cueIds: ["one"], reviewedAt: null }];
  assert.equal(pendingReviewCount(changes), 1);
  assert.equal(pendingReviewCount([{ ...changes[0], reviewedAt: "done" }]), 0);
});
test("crossing phrase links preserve the complete two sentences", () => {
  assert.equal(validAlignment(cue, alignment), alignment);
});
test("a subtitle or translation edit invalidates stale links", () => {
  assert.equal(
    validAlignment(
      { ...cue, source: "Ils veulent nous rencontrer." },
      alignment,
    ),
    null,
  );
  assert.equal(
    validAlignment({ ...cue, translation: "They want to meet us." }, alignment),
    null,
  );
});
test("invalid references and uncovered phrases cannot display misleading connections", () => {
  assert.equal(
    validAlignment(cue, { ...alignment, links: alignment.links.slice(0, 3) }),
    null,
  );
  const links = structuredClone(alignment.links);
  links[0].source = [4];
  assert.equal(validAlignment(cue, { ...alignment, links }), null);
  assert.equal(
    validAlignment(cue, {
      ...alignment,
      translation: ["A different sentence"],
    }),
    null,
  );
});
test("many-to-many links and grammatical words without a separate counterpart are representable", () => {
  const links = [
    { source: [0], translation: [0], note: "Person." },
    {
      source: [1, 3],
      translation: [1, 2],
      note: "An expression plus its action.",
    },
    { source: [2], translation: [3], note: "Person met." },
  ];
  assert(validAlignment(cue, { ...alignment, links }));
  const c = { source: "Il dort.", translation: "Sleeping." };
  assert(
    validAlignment(c, {
      source: ["Il", "dort."],
      translation: ["Sleeping."],
      links: [
        {
          source: [0],
          translation: [],
          note: "Subject omitted in this English fragment.",
        },
        { source: [1], translation: [0], note: "Sleeping." },
      ],
    }),
  );
});

test("subtitle translations preserve order, shared links and gaps", () => {
  assert.equal(phraseTranslation(alignment, 2), "us.");
  const shared = {
    translation: ["one", "two", "three", "four"],
    links: [
      { source: [0, 1], translation: [3, 0, 1] },
      { source: [0], translation: [1] },
      { source: [2], translation: [] },
    ],
  };
  assert.equal(phraseTranslation(shared, 0), "one two … four");
  assert.equal(phraseTranslation(shared, 1), "one two … four");
  assert.equal(phraseTranslation(shared, 2), "");
});

test("a breakdown awaiting the notes stage is shown without link notes", () => {
  const unexplained = {
    ...alignment,
    links: alignment.links.map(({ source, translation }, i) =>
      i === 0 ? { source, translation, note: "" } : { source, translation },
    ),
  };
  assert.equal(validAlignment(cue, unexplained), unexplained);
  assert.equal(alignmentState({ ...cue, alignment: unexplained }), "ready");
  const malformed = {
    ...alignment,
    links: [{ ...alignment.links[0], note: 3 }, ...alignment.links.slice(1)],
  };
  assert.equal(validAlignment(cue, malformed), null);
});
