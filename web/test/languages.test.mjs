import test from "node:test";
import assert from "node:assert/strict";
import { languageName, isComposing } from "../src/player/languages.js";
import {
  validAlignment,
  phraseTranslation,
  translationContext,
} from "../src/player/alignment.js";
import { noteSegments } from "../src/player/learner-note.js";

test("study-language names are independent of the interface and notes languages", () => {
  assert.equal(languageName("de"), "German");
  assert.equal(languageName("ko"), "Korean");
  assert.equal(languageName("fr", "fr"), "français");
});

test("notes highlight backticked source expressions in any script", () => {
  assert.deepEqual(
    noteSegments("La particule `에` indique ici la destination."),
    [
      { text: "La particule ", highlighted: false },
      { text: "에", highlighted: true },
      { text: " indique ici la destination.", highlighted: false },
    ],
  );
});

test("German-English and Korean-French graphs retain reordered meaning links", () => {
  for (const [source, translation, sourcePhrases, translatedPhrases, links] of [
    [
      "Ich stehe auf.",
      "I get up.",
      ["Ich", "stehe", "auf."],
      ["I", "get up."],
      [
        { source: [0], translation: [0], note: "Person speaking" },
        { source: [1, 2], translation: [1], note: "Separable verb" },
      ],
    ],
    [
      "저는 사과를 먹어요.",
      "Je mange une pomme.",
      ["저는", "사과를", "먹어요."],
      ["Je", "mange", "une pomme."],
      [
        { source: [0], translation: [0], note: "Le locuteur" },
        { source: [1], translation: [2], note: "Le complément avec 를" },
        { source: [2], translation: [1], note: "먹다 : manger, forme polie" },
      ],
    ],
  ]) {
    const alignment = {
      sourceText: source,
      translationText: translation,
      source: sourcePhrases,
      translation: translatedPhrases,
      links,
    };
    const cue = { source, translation, alignment };
    assert.equal(validAlignment(cue, alignment), alignment);
    assert.equal(
      phraseTranslation(alignment, 1),
      translatedPhrases[links[1].translation[0]],
    );
    assert.equal(
      validAlignment({ ...cue, source: "Changed" }, alignment),
      null,
    );
  }
});

test("Korean composition and its commit keys belong to the input method", () => {
  assert.ok(isComposing({ isComposing: true, key: "Enter" }));
  assert.ok(isComposing({ nativeEvent: { isComposing: true }, code: "Space" }));
  assert.ok(isComposing({ keyCode: 229, key: "Enter" }));
  assert.ok(!isComposing({ key: "Enter" }));
});

test("the explored phrase is shown inside the whole translated line", () => {
  const alignment = {
    source: ["Oh, mon Dieu,", "c'est pas drôle", "de vieillir."],
    translation: ["Oh my God,", "it isn't fun", "to get old."],
    links: [
      { source: [0], translation: [0], note: "" },
      { source: [1], translation: [1], note: "" },
      { source: [2], translation: [2], note: "" },
    ],
  };
  assert.deepEqual(translationContext(alignment, 1), [
    { text: "Oh my God,", link: 0, active: false },
    { text: "it isn't fun", link: 1, active: true },
    { text: "to get old.", link: 2, active: false },
  ]);
});
