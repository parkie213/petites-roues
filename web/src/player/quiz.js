/**
 * A self-contained Markdown brief for running the subtitle quiz in a chat app, matching the phone
 * player's: the quiz rules (from the repository's agents/) plus the lines the learner has just
 * watched with their translations, so the quiz uses the film's real text.
 */
import { languageName } from "./languages.js";

/**
 * The quiz types offered by Quiz me. Each one's instructions are the section of
 * agents/workflows/quiz-types.md under [heading]; [label] names it for the learner.
 */
export const QUIZ_TYPES = [
  {
    id: "to-source",
    heading: "Translate into the source language",
    label: (doc) => `Translate into ${languageName(doc.sourceLanguage)}`,
  },
  {
    id: "to-translation",
    heading: "Translate into the translation language",
    label: (doc) => `Translate into ${languageName(doc.translationLanguage)}`,
  },
  {
    id: "comprehension",
    heading: "Comprehension",
    label: () => "Comprehension",
  },
  { id: "vocabulary", heading: "Vocabulary", label: () => "Vocabulary" },
  { id: "tenses", heading: "Verb tenses", label: () => "Verb tenses" },
  { id: "mixed", heading: "Mixed", label: () => "Mixed" },
];

/**
 * The quiz type [id] for [doc]: its label and its instructions from the quiz types Markdown
 * ([types]). Unknown ids fall back to the first type.
 */
export function quizType(types, id, doc) {
  const type = QUIZ_TYPES.find((t) => t.id === id) ?? QUIZ_TYPES[0];
  const sections = types.split(/^## /m).slice(1);
  const section = sections.find((text) => text.startsWith(type.heading + "\n"));
  return {
    id: type.id,
    label: type.label(doc),
    instructions: section ? section.slice(type.heading.length).trim() : "",
  };
}

/** Keeps a quiz from working through the lines in order, as chat models otherwise do. */
const SPREAD =
  "Pick items from all over these lines, in a mixed order: do not start at the " +
  "beginning, and do not take two items in a row from the same page or moment.";

/** Parses a player time in M:SS or H:MM:SS; invalid values return null. */
export function parseQuizTime(value) {
  if (!/^\d+:\d{2}(?::\d{2})?$/.test(value)) return null;
  const parts = value.split(":").map(Number);
  if (parts.at(-1) > 59 || (parts.length === 3 && parts[1] > 59)) return null;
  const seconds = parts.reduce((total, part) => total * 60 + part, 0);
  return Number.isFinite(seconds) ? seconds : null;
}

/** Includes all source lines, or lines overlapping the half-open time range [start, end). */
export function quizBrief(rules, doc, cues, range, type) {
  const watched = cues.filter(
    (cue) =>
      cue.source?.trim() &&
      (!range || (cue.start < range.end && cue.end > range.start)),
  );
  return briefMarkdown({
    rules,
    type,
    doc,
    heading: `Subtitle quiz: ${doc.title}`,
    intro:
      "Run the subtitle quiz below for a learner, using the selected subtitle lines. " +
      "Start the first exercise straight away. Instructions about the browser player do " +
      "not apply here, and the rules mention files from the library (a teaching guide, " +
      "documents) that are not included: work from the lines below.",
    section: range
      ? `Lines in time range (${clock(range.start)}–${clock(range.end)})`
      : "Whole episode",
    empty: "No subtitle lines in this selection.",
    items: watched.map((cue) => ({ label: clock(cue.start), cue })),
  });
}

/**
 * The Markdown shared by every quiz brief: heading, instructions, language roles, the chosen quiz
 * [type] (from quizType; optional), the quiz rules, then the title's lines. Each line is its source text and the app's
 * translation, the only parts a translation quiz needs: the chat model writes its own
 * explanations, so the learner notes stay out and the brief stays small. Items may carry a
 * `label` (a time) and a `group` (a page) shown as a heading when it changes, or [empty] is
 * shown when there are none. Cues are flat display cues.
 */
export function briefMarkdown({
  rules,
  type,
  doc,
  heading,
  intro,
  section,
  empty,
  items,
}) {
  const lines = [
    `# ${heading}`,
    "",
    intro,
    "",
    `Source language: \`${doc.sourceLanguage}\`; translation language: ` +
      `\`${doc.translationLanguage}\`; notes language: \`${doc.notesLanguage}\`. ` +
      "Each line below is the source text, then → the app's translation.",
    "",
    ...(type
      ? [
          `## Quiz type: ${type.label}`,
          "",
          type.instructions,
          "",
          "Whatever the type, keep the approach of the rules below: one item at a time, " +
            "wait for the attempt, diagnose, hint, retry, and reveal only when asked.",
          "",
        ]
      : []),
    "## Quiz rules",
    "",
    demote(rules),
    "",
    `## ${section}`,
    "",
    ...(items.length ? [SPREAD, ""] : []),
  ];
  if (!items.length) lines.push(empty);
  let group;
  for (const { group: next, label, cue } of items) {
    if (next && next !== group) lines.push("", `### ${next}`, "");
    group = next;
    const translation = cue.translation?.trim();
    lines.push(
      `- ${label ? `${label} ` : ""}${cue.source.trim()}` +
        (translation ? ` → ${translation}` : ""),
    );
  }
  return lines.join("\n") + "\n";
}

/** A file name for the brief, from the title and an optional [part] such as "chapter 2". */
export function quizFileName(doc, part = "") {
  const slug =
    `${doc.title} ${part}`
      .toLowerCase()
      .replace(/[^\p{L}\p{N}]+/gu, "-")
      .replace(/^-|-$/g, "") || "title";
  return `quiz-${slug}.md`;
}

/** Nests a bundled file's headings two levels below this brief's sections. */
export function demote(markdown) {
  return markdown
    .trim()
    .split("\n")
    .map((line) => (line.startsWith("#") ? "##" + line : line))
    .join("\n");
}

export function clock(seconds) {
  const total = Math.max(0, Math.floor(seconds));
  const pad = (n) => String(n).padStart(2, "0");
  return total >= 3600
    ? `${Math.floor(total / 3600)}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`
    : `${Math.floor(total / 60)}:${pad(total % 60)}`;
}
