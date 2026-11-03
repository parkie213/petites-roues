import { useState } from "react";
import {
  alignmentColors as colors,
  alignmentState,
  validAlignment,
} from "../player/alignment.js";
import { LearnerNote } from "../player/components/LearnerNote.jsx";
import { PhraseAlignment } from "../player/components/PhraseAlignment.jsx";
import { useStudyLanguages } from "../player/StudyLanguages.jsx";

/**
 * Lays a bubble's phrase links out as aligned rows, source beside translation, so matching
 * parts read across instead of being paired by colour between two independently wrapped
 * lines. Hovering a row previews its explanation below the rows; clicking keeps it shown.
 * The explanation stays out of the row list so rows never shift under the pointer.
 * Bubbles without a valid alignment fall back to the shared phrase view.
 */
export function ComicPhrasePairs({ cue }) {
  const { sourceLanguage, translationLanguage } = useStudyLanguages();
  const [hovered, setHovered] = useState(null);
  const [pinned, setPinned] = useState(null);
  const alignment =
    alignmentState(cue) === "ready" ? validAlignment(cue, cue.alignment) : null;
  if (!alignment) return <PhraseAlignment cue={cue} />;
  const active = hovered ?? pinned;
  const side = (link, key) =>
    link[key].map((i) => alignment[key][i]).join(" … ");
  return (
    <div className="comic-pairs">
      <div className="comic-pairs-head" aria-hidden="true">
        <span />
        <span>{sourceLanguage?.toUpperCase()}</span>
        <span>{translationLanguage?.toUpperCase()}</span>
      </div>
      {alignment.links.map((link, index) => (
        <div
          key={index}
          className={
            "comic-pair" +
            (active === index ? " is-active" : "") +
            (pinned === index ? " is-pinned" : "")
          }
          style={{ "--phrase-color": colors[index % colors.length] }}
          onMouseEnter={() => setHovered(index)}
          onMouseLeave={() => setHovered(null)}
        >
          <button
            className="comic-pair-row"
            aria-pressed={pinned === index}
            onClick={() => setPinned(pinned === index ? null : index)}
            onFocus={() => setHovered(index)}
            onBlur={() => setHovered(null)}
          >
            <span className="alignment-match" aria-hidden="true">
              {index + 1}
            </span>
            <span lang={sourceLanguage}>{side(link, "source") || "—"}</span>
            <span lang={translationLanguage}>
              {side(link, "translation") || "—"}
            </span>
          </button>
        </div>
      ))}
      <p className="comic-pair-note" aria-live="polite">
        {active !== null ? (
          alignment.links[active].note?.trim() ? (
            <LearnerNote>{alignment.links[active].note}</LearnerNote>
          ) : (
            "No explanation yet."
          )
        ) : (
          "Hover or click a row to see its explanation."
        )}
      </p>
      {cue.notes?.length > 0 && (
        <ul className="comic-pairs-notes">
          {cue.notes.map((note, i) => (
            <li key={i}>
              <LearnerNote>{note}</LearnerNote>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
