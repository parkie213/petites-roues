import { briefMarkdown, quizFileName } from "../player/quiz.js";
import { chapterName } from "./navigation.js";

/**
 * Prepares the static demo's equivalent of the server chapter brief. Only the selected chapter's
 * source lines and translations are exported, in manifest order; learner notes stay in the app.
 * This is a read-only operation, with the caller's selected quiz type and teaching rules.
 */
export function chapterQuiz(document, { chapterPageId, rules, type }) {
  if (typeof rules !== "string" || !rules.trim())
    throw new Error("Quiz rules are required.");
  if (
    type != null &&
    (typeof type.label !== "string" || typeof type.instructions !== "string")
  )
    throw new Error("Invalid quiz type.");
  const { pages, chapters = [] } = document.comic;
  const index = chapters.findIndex(
    (chapter) => chapter.pageId === chapterPageId,
  );
  if (index < 0) throw new Error("Unknown chapter.");
  const chapter = chapters[index];
  const start = pages.findIndex((page) => page.id === chapter.pageId);
  const end =
    index + 1 < chapters.length
      ? pages.findIndex((page) => page.id === chapters[index + 1].pageId)
      : pages.length;
  if (start < 0 || end <= start) throw new Error("Invalid chapter page range.");
  const name = chapterName(chapter);
  const items = pages.slice(start, end).flatMap((page, offset) =>
    (page.boxes ?? []).flatMap((box) =>
      box.lines
        .filter((line) => line.source?.trim())
        .map((cue) => ({
          cue,
          group: `Page ${page.label || start + offset + 1}`,
        })),
    ),
  );
  return {
    text: briefMarkdown({
      rules,
      type,
      doc: document,
      heading: `Chapter quiz: ${document.title}, chapter ${name}`,
      intro:
        "Run the subtitle quiz below for a learner, using the speech bubbles of the comic chapter they are reading: in the rules, a subtitle or line means a bubble, and the film means this comic. Bubbles are listed in reading order. Start the first exercise straight away. Instructions about the browser player do not apply here, and the rules mention files from the library (a teaching guide, documents) that are not included: work from the lines below.",
      section: `Bubbles in chapter ${name} (pages ${start + 1}–${end})`,
      empty: "None: this chapter's text has not been found yet.",
      items,
    }),
    fileName: quizFileName(
      document,
      chapter.number ? `chapter ${chapter.number}` : chapter.title,
    ),
  };
}
