import { useStudyLanguages } from "../StudyLanguages.jsx";
import { useEffect, useRef, useState } from "react";
import {
  alignmentColors as colors,
  alignmentState,
  validAlignment,
} from "../alignment.js";
import "./PhraseAlignment.css";
import { LearnerNote } from "./LearnerNote.jsx";

export function PhraseAlignment({ cue, plain = false, showLinkNotes = false }) {
  const { sourceLanguage, translationLanguage, sourceName, translationName } =
    useStudyLanguages();
  const state = alignmentState(cue);
  const alignment =
    state === "ready" ? validAlignment(cue, cue.alignment) : null;
  const [hovered, setHovered] = useState(null);
  const [pinned, setPinned] = useState(null);
  const root = useRef(null);
  const active = pinned ?? hovered;
  const exploring = pinned !== null || hovered !== null;
  const graph = alignment && !plain;

  const clear = () => {
    setPinned(null);
    setHovered(null);
    const focused = document.activeElement;
    if (root.current?.contains(focused) && focused.matches(".alignment-phrase"))
      focused.blur();
  };
  useEffect(() => {
    if (!graph) return;
    const outside = (event) => {
      if (
        root.current?.contains(event.target) &&
        event.target.closest?.(".alignment-phrase")
      )
        return;
      clear();
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [graph]);

  if (!cue)
    return <div className="alignment-empty">Choose a subtitle to explore.</div>;
  const phrase = (side, text, i) => {
    const links = alignment.links.flatMap((link, index) =>
      link[side].includes(i) ? [index] : [],
    );
    const index = links[0];
    const highlighted = active !== null && links.includes(active);
    return (
      <button
        key={i}
        className={`alignment-phrase${highlighted ? " is-active" : ""}${exploring && !highlighted ? " is-muted" : ""}`}
        style={{ "--phrase-color": colors[index % colors.length] }}
        onMouseEnter={() => setHovered(index)}
        onMouseLeave={() => setHovered(null)}
        onFocus={() => setHovered(index)}
        onBlur={() => setHovered(null)}
        onClick={() => {
          setPinned(pinned === index ? null : index);
          setHovered(index);
        }}
        aria-pressed={pinned !== null && links.includes(pinned)}
      >
        <span className="alignment-match" aria-hidden="true">
          {index + 1}
        </span>
        {text}
      </button>
    );
  };
  return (
    <div
      ref={root}
      className="phrase-alignment"
      onKeyDown={(event) => {
        if (event.key === "Escape") clear();
      }}
    >
      {graph ? (
        <div
          className={`alignment-study${cue.notes?.length ? " has-sentence-notes" : ""}`}
        >
          <div className="alignment-layout">
            <div
              className="alignment-canvas"
              aria-label={`Matching ${sourceName} and ${translationName} phrases`}
            >
              <div className="alignment-row">
                <span className="alignment-language">
                  {sourceLanguage?.toUpperCase()}
                </span>
                <div className="alignment-sentence" lang={sourceLanguage}>
                  {alignment.source.map((text, i) => phrase("source", text, i))}
                </div>
              </div>
              <div className="alignment-row">
                <span className="alignment-language">
                  {translationLanguage?.toUpperCase()}
                </span>
                <div
                  className="alignment-sentence alignment-translation"
                  lang={translationLanguage}
                >
                  {alignment.translation.map((text, i) =>
                    phrase("translation", text, i),
                  )}
                </div>
              </div>
            </div>
          </div>
          {showLinkNotes && (
            <section
              className="alignment-link-note"
              aria-label="Phrase explanation"
              aria-live="polite"
            >
              {active !== null ? (
                alignment.links[active].note?.trim() ? (
                  <LearnerNote>{alignment.links[active].note}</LearnerNote>
                ) : (
                  <span>No explanation yet.</span>
                )
              ) : (
                <span>Select a phrase to see its explanation.</span>
              )}
            </section>
          )}
          {cue.notes?.length > 0 && (
            <section
              className="sentence-level-notes"
              aria-label="Sentence-level notes"
            >
              <h3>Sentence-level notes</h3>
              <ul>
                {cue.notes.map((note, index) => (
                  <li key={index}>
                    <LearnerNote>{note}</LearnerNote>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      ) : (
        <>
          <div className="current-source" lang={sourceLanguage}>
            {cue.source}
          </div>
          <div className="translation" lang={translationLanguage}>
            {cue.translation}
          </div>
          {state === "stale" ? (
            <p className="alignment-warning" role="status">
              Phrase links need regenerating after this subtitle changed.
            </p>
          ) : (
            !alignment &&
            cue.translation && (
              <p className="alignment-hint">
                Phrase links haven't been added to this subtitle yet.
              </p>
            )
          )}
        </>
      )}
      {!graph && cue.notes?.length > 0 && (
        <section
          className="legacy-translation-notes"
          aria-label={
            state === "ready" ? "Sentence-level notes" : "Translation notes"
          }
        >
          {state === "ready" && <h3>Sentence-level notes</h3>}
          <ul>
            {cue.notes.map((note, index) => (
              <li key={index}>
                <LearnerNote>{note}</LearnerNote>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
