import { briefMarkdown, quizFileName } from "../player/quiz.js";
import { pageCues } from "./annotations.js";
import { chapterName } from "./navigation.js";

/**
 * The chapter quiz brief for a comic, matching the video "Quiz me": the quiz rules, every bubble
 * of chapter [index] in reading order with its translation.
 * The rules speak of subtitles and films, so the brief maps them onto bubbles and comics.
 * [doc] is the reader's flat comic document.
 */
export function chapterQuizBrief(rules, doc, index, type) {
  const chapter = doc.chapters[index];
  const { first, last } = chapterPages(doc, index);
  const items = doc.pages.slice(first, last + 1).flatMap((page, offset) =>
    pageCues(doc, page.id)
      .filter((cue) => cue.source?.trim())
      .map((cue) => ({
        group: `Page ${page.label || first + offset + 1}`,
        cue,
      })),
  );
  return briefMarkdown({
    rules,
    type,
    doc,
    heading: `Chapter quiz: ${doc.title}, chapter ${chapterName(chapter)}`,
    intro:
      "Run the subtitle quiz below for a learner, using the speech bubbles of the comic " +
      "chapter they are reading: in the rules, a subtitle or line means a bubble, and the " +
      "film means this comic. Bubbles are listed in reading order. Start the first exercise " +
      "straight away. Instructions about the browser player do not apply here, and the " +
      "rules mention files from the library (a teaching guide, documents) that are not " +
      "included: work from the lines below.",
    section: `Bubbles in chapter ${chapterName(chapter)} (pages ${first + 1}–${last + 1})`,
    empty: "None: this chapter's text has not been found yet.",
    items,
  });
}

/** Manifest positions of chapter [index]'s first and last pages. */
export function chapterPages(doc, index) {
  const start = (i) =>
    doc.pages.findIndex((page) => page.id === doc.chapters[i].pageId);
  const first = start(index);
  const last =
    index + 1 < doc.chapters.length
      ? start(index + 1) - 1
      : doc.pages.length - 1;
  return { first, last };
}

/** "quiz-demon-slayer-tome-1-chapter-2.md" */
export function chapterQuizFileName(doc, index) {
  const chapter = doc.chapters[index];
  return quizFileName(
    doc,
    chapter.number ? `chapter ${chapter.number}` : chapter.title,
  );
}
