import { useEffect, useState } from "react";
import { QuizShare } from "../player/components/QuizShare.jsx";
import { request } from "../shared/api.ts";
import { useStoredState } from "../shared/storage.ts";
import { generalQuizBrief, generalQuizFileName, quizKey } from "./quizzes.js";

/**
 * The library's "Quiz me": general quizzes from the repositories' quizzes/ folders, listed
 * again each time the dialog opens so a newly added file appears. The last quiz chosen is
 * remembered in this browser.
 */
export function LibraryQuiz() {
  const [quizzes, setQuizzes] = useState(null);
  const [last, setLast] = useStoredState("quiz.general", "");
  const load = () =>
    request("/api/quizzes").then(setQuizzes, () => setQuizzes([]));
  useEffect(() => {
    load();
  }, []);
  const list = quizzes ?? [];
  const find = (key) => list.find((quiz) => quizKey(quiz) === key);
  const initial = find(last) ? last : list[0] && quizKey(list[0]);
  const several = new Set(list.map((quiz) => quiz.repository)).size > 1;
  return (
    <QuizShare
      subject="a general quiz"
      withTypes={false}
      disabled={!list.length}
      reason={
        quizzes
          ? "No quizzes yet: add Markdown files to a quizzes/ folder at the root of a repository."
          : ""
      }
      onOpen={load}
      scopes={{
        label: "Quiz",
        initial,
        options: list.map((quiz) => ({
          value: quizKey(quiz),
          label: several
            ? `${quiz.title} (${quiz.repositoryName})`
            : quiz.title,
        })),
        onChange: setLast,
      }}
      fileName={(key) => generalQuizFileName(find(key))}
      brief={(rules, _type, key) => generalQuizBrief(rules, find(key))}
    />
  );
}
