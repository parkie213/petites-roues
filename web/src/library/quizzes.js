/**
 * General quizzes: Markdown prompts in a content repository's quizzes/ folder, not tied to a title
 * (served by GET /api/quizzes). The brief hands one to a chat app with the quiz rules.
 */
import { demote, quizFileName } from "../player/quiz.js";

/** The dialog's key for [quiz], unique across repositories. */
export const quizKey = (quiz) => `${quiz.repository}/${quiz.id}`;

/**
 * The brief for a general [quiz]: what to do, the quiz file itself, then the subtitle quiz
 * [rules] for the teaching approach. The chat model writes its own sentences, since there are
 * no title lines to draw on.
 */
export function generalQuizBrief(rules, quiz) {
  return [
    `# Quiz: ${quiz.title}`,
    "",
    "Run the quiz below for a learner. It is not tied to a film or book: make up natural, " +
      "varied sentences of your own, as a native speaker would say them, and do not present " +
      "them as quotations. The quiz says which language is studied and which language to " +
      "explain in; where the rules mention the source, translation or notes language, use " +
      "those. Start the first exercise straight away.",
    "",
    "Vary the items: mix verbs, persons and situations rather than working through a list " +
      "in order, and return more often to what the learner finds hard.",
    "",
    "## The quiz",
    "",
    demote(quiz.text),
    "",
    "## Quiz rules",
    "",
    "These rules were written for quizzes on a title's subtitles. Keep their teaching " +
      "approach (one item at a time, wait for the attempt, diagnose, hint, retry, reveal only " +
      "when asked) and ignore what they say about films, documents, the browser player or " +
      "files from the library.",
    "",
    demote(rules),
    "",
  ].join("\n");
}

/** "quiz-common-irregular-verbs.md" */
export const generalQuizFileName = (quiz) =>
  quizFileName({ title: quiz.title });
