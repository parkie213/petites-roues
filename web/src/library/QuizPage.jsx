import { StudyPage } from "./StudyPage.jsx";

/** General quizzes are a library destination; title-specific quizzes remain dialogs. */
export function QuizPage() {
  return <StudyPage kind="quizzes" />;
}
