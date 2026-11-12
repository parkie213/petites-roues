import { useStudyLanguages } from "../StudyLanguages.jsx";
import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import {
  alignmentColors,
  alignmentState,
  translationContext,
} from "../alignment.js";
import "./VideoSubtitles.css";
import { LearnerNote } from "./LearnerNote.jsx";

// This overlay lives inside the fullscreen player. Its popover never changes layout.
export function VideoSubtitles({
  cue,
  lineOffset = 0,
  helpMode = "",
  onActiveChange,
  showTranslation = false,
  hideSource = false,
  definitionIndex = null,
}) {
  const { sourceLanguage, translationLanguage, sourceName } =
    useStudyLanguages();
  const [hovered, setHovered] = useState(null);
  // Crossing the gap between two phrases briefly hovers neither; wait before clearing so the
  // translation strip and explanation stay put instead of flickering.
  const leaveTimer = useRef(null);
  const keepHelp = () => clearTimeout(leaveTimer.current);
  const hover = (index) => {
    keepHelp();
    setHovered(index);
  };
  const leave = () => {
    clearTimeout(leaveTimer.current);
    leaveTimer.current = setTimeout(() => setHovered(null), 350);
  };
  useEffect(() => () => clearTimeout(leaveTimer.current), []);
  const [focused, setFocused] = useState(null);
  const [pinned, setPinned] = useState(null);
  const description = useId();
  const root = useRef(null);
  const notesBox = useRef(null);
  const [notesPosition, setNotesPosition] = useState(null);
  const line = useRef(null);
  const translationBox = useRef(null);
  const [translationBottom, setTranslationBottom] = useState(null);
  const alignment = alignmentState(cue) === "ready" ? cue.alignment : null;
  const active = definitionIndex ?? pinned ?? focused ?? hovered;
  const clearSelection = useCallback(() => {
    clearTimeout(leaveTimer.current);
    setPinned(null);
    setFocused(null);
    setHovered(null);
  }, []);
  useEffect(clearSelection, [definitionIndex, clearSelection]);
  const exploring = active !== null;
  useLayoutEffect(() => {
    onActiveChange?.(cue.id, exploring);
    return () => onActiveChange?.(cue.id, false);
  }, [cue.id, exploring, onActiveChange]);
  useEffect(() => {
    if (pinned === null) return;
    const clearOutside = (event) => {
      if (
        root.current?.contains(event.target) &&
        event.target.closest?.(
          ".video-subtitle-phrase, .video-subtitle-context-chunk, .video-subtitle-notes",
        )
      )
        return;
      clearSelection();
    };
    document.addEventListener("pointerdown", clearOutside);
    return () => document.removeEventListener("pointerdown", clearOutside);
  }, [pinned, clearSelection]);
  // Source buttons and translated chunks use the same selection rules. An unmatched
  // translation chunk has no source index, so it cannot hover or pin a phrase.
  function pin(event, index) {
    event.stopPropagation();
    if (index != null)
      setPinned((current) => (current === index ? null : index));
  }
  function phraseEvents(index) {
    return {
      onMouseEnter: () => index != null && hover(index),
      onMouseLeave: leave,
      onFocus: () => setFocused(index ?? null),
      onBlur: () => setFocused(null),
      onClick: (event) => pin(event, index),
    };
  }
  const notes =
    alignment && active !== null
      ? [
          ...new Set(
            alignment.links
              .filter((link) => link.source.includes(active))
              .map((link) => link.note)
              .filter(Boolean),
          ),
        ]
      : [];
  useLayoutEffect(() => {
    if (active === null && !showTranslation) return;
    const subtitleLayers = () =>
      root.current.parentElement.querySelectorAll(
        ".video-subtitle-line, .video-full-translation, .video-sentence-notes",
      );
    const update = () => {
      const bounds = root.current.getBoundingClientRect();
      // The whole translation sits directly above this line and never moves between phrases.
      const lineTop = line.current.getBoundingClientRect().top - bounds.top;
      setTranslationBottom(bounds.height - lineTop + 6);
      const note = notesBox.current?.getBoundingClientRect();
      if (!note) return;
      // The explanation sits above every subtitle line, the translation and sentence help.
      const tops = Array.from(
        subtitleLayers(),
        (element) => element.getBoundingClientRect().top - bounds.top,
      );
      const translationHeight =
        translationBox.current?.getBoundingClientRect().height ?? 0;
      const ceiling = Math.min(...tops) - translationHeight - 18;
      const maxHeight = Math.max(0, ceiling - 8);
      setNotesPosition(
        maxHeight >= 40
          ? {
              left: Math.max(8, (bounds.width - note.width) / 2),
              top: Math.max(8, ceiling - Math.min(note.height, maxHeight)),
              maxHeight,
            }
          : null,
      );
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(root.current);
    if (notesBox.current) observer.observe(notesBox.current);
    if (translationBox.current) observer.observe(translationBox.current);
    subtitleLayers().forEach((line) => observer.observe(line));
    return () => observer.disconnect();
  }, [active, alignment, lineOffset, helpMode, showTranslation]);
  return (
    <div
      ref={root}
      className="video-subtitles"
      aria-label={`${sourceName} subtitles`}
      onMouseLeave={leave}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          clearSelection();
          event.target.blur();
          event.stopPropagation();
        }
      }}
    >
      {((active !== null && alignment) ||
        (showTranslation && cue.translation)) && (
        <div
          ref={translationBox}
          id={description}
          role="group"
          aria-label="Translation"
          className="video-subtitle-english"
          lang={translationLanguage}
          style={
            translationBottom == null
              ? { visibility: "hidden" }
              : { bottom: translationBottom }
          }
        >
          <span
            className="video-subtitle-english-line"
            onMouseEnter={keepHelp}
            onMouseLeave={leave}
          >
            {alignment
              ? translationContext(alignment, active).map((chunk, index) => {
                  const link = alignment.links[chunk.link];
                  const source = link?.source[0];
                  return (
                    <span key={index}>
                      {index > 0 && (cue.translationSeparator ?? " ")}
                      <span
                        className="video-subtitle-context-chunk"
                        data-active={chunk.active || undefined}
                        role={link?.source.length ? "button" : undefined}
                        tabIndex={link?.source.length ? 0 : undefined}
                        aria-pressed={link?.source.includes(pinned)}
                        {...phraseEvents(source)}
                        onKeyDown={(event) => {
                          if (event.key !== "Enter" && event.key !== " ")
                            return;
                          event.preventDefault();
                          pin(event, source);
                        }}
                        style={{
                          "--phrase-color":
                            chunk.link < 0
                              ? "transparent"
                              : alignmentColors[
                                  chunk.link % alignmentColors.length
                                ],
                        }}
                      >
                        {chunk.text}
                      </span>
                    </span>
                  );
                })
              : cue.translation}
          </span>
        </div>
      )}
      {notes.length > 0 && (
        <aside
          ref={notesBox}
          style={notesPosition || { visibility: "hidden" }}
          className="video-subtitle-notes"
          onMouseEnter={keepHelp}
          onMouseLeave={leave}
          aria-label="Phrase explanation"
          onClick={(event) => event.stopPropagation()}
        >
          {notes.map((note, index) => (
            <p key={index}>
              <LearnerNote>{note}</LearnerNote>
            </p>
          ))}
        </aside>
      )}
      <div
        ref={line}
        className="video-subtitle-line"
        onMouseEnter={keepHelp}
        onMouseLeave={leave}
        lang={sourceLanguage}
        style={{
          bottom: `calc(var(--subtitle-bottom, 4%) + ${lineOffset * 3}em)`,
          visibility: hideSource ? "hidden" : undefined,
        }}
      >
        {alignment ? (
          alignment.source.map((phrase, index) => {
            const linkIndex = alignment.links.findIndex((link) =>
              link.source.includes(index),
            );
            return (
              <button
                key={index}
                type="button"
                className="video-subtitle-phrase"
                style={{
                  "--phrase-color":
                    alignmentColors[linkIndex % alignmentColors.length],
                }}
                aria-pressed={pinned === index}
                aria-describedby={active === index ? description : undefined}
                data-active={active === index || undefined}
                {...phraseEvents(index)}
              >
                {phrase}
              </button>
            );
          })
        ) : (
          <span className="video-subtitle-plain">{cue.source}</span>
        )}
      </div>
    </div>
  );
}
