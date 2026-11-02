import { useStudyLanguages } from "../StudyLanguages.jsx";
import { useLayoutEffect, useRef, useState } from "react";
import { LearnerNote } from "./LearnerNote.jsx";

// Keep whole-sentence help inside fullscreen and immediately above the source subtitles.
export function VideoStudyHelp({ cues, notes, showTranslation, showNotes }) {
  const { translationLanguage } = useStudyLanguages();
  const root = useRef(null);
  const [position, setPosition] = useState(null);
  const translation = showTranslation
    ? cues
        .map((cue) => cue.translation)
        .filter(Boolean)
        .join("\n")
    : "";
  useLayoutEffect(() => {
    const element = root.current;
    const stage = element.parentElement;
    const lines = [...stage.querySelectorAll(".video-subtitle-line")];
    const measure = () => {
      const bounds = stage.getBoundingClientRect();
      const top = lines.length
        ? Math.min(...lines.map((line) => line.getBoundingClientRect().top)) -
          bounds.top
        : bounds.height - 12;
      setPosition({
        bottom: bounds.height - top + 8,
        maxHeight: Math.max(0, top - 16),
      });
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(stage);
    lines.forEach((line) => observer.observe(line));
    return () => observer.disconnect();
  }, [translation, showNotes, cues.map((cue) => cue.id).join(",")]);
  return (
    <div
      ref={root}
      className="video-study-help"
      style={position || { visibility: "hidden" }}
    >
      {showNotes && (
        <section
          className="video-sentence-notes"
          aria-label="Sentence notes"
          onClick={(event) => event.stopPropagation()}
        >
          <h3>
            Sentence notes <kbd>W</kbd>
          </h3>
          {notes?.length ? (
            <ul>
              {notes.map((note, index) => (
                <li key={index}>
                  <LearnerNote>{note}</LearnerNote>
                </li>
              ))}
            </ul>
          ) : (
            <p>No sentence notes for this subtitle.</p>
          )}
        </section>
      )}
      {translation && (
        <div className="video-full-translation" lang={translationLanguage}>
          {translation}
        </div>
      )}
    </div>
  );
}
