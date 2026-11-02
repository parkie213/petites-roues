import { noteSegments } from "../learner-note.js";
import { useStudyLanguages } from "../StudyLanguages.jsx";

/** Backticks mark source-language expressions; explanation text uses the notes language. */
export function LearnerNote({ children }) {
  const { sourceLanguage, notesLanguage } = useStudyLanguages();
  return (
    <span lang={notesLanguage}>
      {noteSegments(children).map((segment, index) =>
        segment.highlighted ? (
          <mark className="source-expression" lang={sourceLanguage} key={index}>
            {segment.text}
          </mark>
        ) : (
          segment.text
        ),
      )}
    </span>
  );
}
