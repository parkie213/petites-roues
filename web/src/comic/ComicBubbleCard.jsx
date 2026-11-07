import { useEffect, useState } from "react";
import {
  ActionIcon,
  Button,
  Group,
  Text,
  Textarea,
  Tooltip,
} from "@mantine/core";
import { MessageCircleQuestion } from "lucide-react";
import { askMessage, askTargets } from "./ask.js";
import {
  alignmentColors as colors,
  alignmentState,
  translationContext,
} from "../player/alignment.js";
import { LearnerNote } from "../player/components/LearnerNote.jsx";
import { useStudyLanguages } from "../player/StudyLanguages.jsx";
import { ListenButton } from "./ListenButton.jsx";
import { useReadAloud } from "./ReadAloud.jsx";

// Brushing across a bubble on the way to another should not start reading it.
const AUTO_LISTEN_DELAY = 250;

/**
 * A bubble's text as a subtitle-style line of phrases with its whole translation and sentence
 * notes always shown beneath. Hovering or focusing a phrase highlights its part of the
 * translation, as the video player's translation strip does, and shows its explanation below;
 * clicking pins it. English chunks explore their source phrase the same way. The ask button opens
 * a question box that sends the bubble to a chat app (see {@link AskBox}); it and the listen button
 * sit at the card's foot so the source line has the full width. Highlighting never changes the card's size, so it never moves under the
 * pointer. The bubble is read aloud with R, or on opening when listen-on-open is enabled; the
 * listen button reads the pinned phrase alone when one is pinned. A quiet card never reads on
 opening: it is a hidden copy that only reserves space (see {@link ComicBlockCard}).
 */
export function ComicBubbleCard({ cue, document, onKeep, quiet = false }) {
  const [asking, setAsking] = useState(false);
  const { sourceLanguage, translationLanguage } = useStudyLanguages();
  const { speak, autoListen } = useReadAloud();
  const [hovered, setHovered] = useState(null);
  const [pinned, setPinned] = useState(null);
  const alignment = alignmentState(cue) === "ready" ? cue.alignment : null;
  const active = alignment ? (hovered ?? pinned) : null;
  // French phrases and their English chunks explore the same phrase. They are inline spans acting
  // as buttons, not <button>s: a button is always laid out as an inline-block, so a phrase that
  // wraps would become one box with a single underline across its full width.
  const explore = (index) => ({
    role: "button",
    tabIndex: 0,
    onKeyDown: (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      setPinned(pinned === index ? null : index);
    },
    onMouseEnter: () => setHovered(index),
    onMouseLeave: () => setHovered(null),
    onFocus: () => setHovered(index),
    onBlur: () => setHovered(null),
    onClick: () => setPinned(pinned === index ? null : index),
  });
  useEffect(() => {
    if (quiet || !autoListen || !cue.source) return;
    const timer = setTimeout(() => speak(cue.source), AUTO_LISTEN_DELAY);
    return () => clearTimeout(timer);
  }, []);
  // Every phrase's explanation is laid out in one grid cell so the card keeps the height of the
  // longest.
  const phraseNotes = alignment
    ? alignment.source.map((_, index) => [
        ...new Set(
          alignment.links
            .filter((link) => link.source.includes(index))
            .map((link) => link.note)
            .filter(Boolean),
        ),
      ])
    : [];
  return (
    <div className="comic-card">
      <div className="comic-card-line" lang={sourceLanguage}>
        {alignment
          ? alignment.source.map((phrase, index) => [
              index > 0 && (cue.sourceSeparator ?? " "),
              <span
                key={index}
                className="comic-card-phrase"
                style={{
                  "--phrase-color":
                    colors[
                      alignment.links.findIndex((l) =>
                        l.source.includes(index),
                      ) % colors.length
                    ],
                }}
                data-active={active === index || undefined}
                aria-pressed={pinned === index}
                {...explore(index)}
              >
                {phrase}
              </span>,
            ])
          : cue.source}
      </div>
      <p className="comic-card-translation" lang={translationLanguage}>
        {alignment
          ? translationContext(alignment, active).map((chunk, index) => {
              // An English chunk explores the first source phrase it translates.
              const phrase = alignment.links[chunk.link]?.source[0];
              const style = {
                "--phrase-color":
                  chunk.link < 0
                    ? "transparent"
                    : colors[chunk.link % colors.length],
              };
              return (
                <span key={index}>
                  {index > 0 && (cue.translationSeparator ?? " ")}
                  {phrase === undefined ? (
                    <span className="comic-card-chunk" style={style}>
                      {chunk.text}
                    </span>
                  ) : (
                    <span
                      className="comic-card-chunk"
                      style={style}
                      data-active={chunk.active || undefined}
                      aria-pressed={pinned === phrase}
                      {...explore(phrase)}
                    >
                      {chunk.text}
                    </span>
                  )}
                </span>
              );
            })
          : cue.translation || "No translation for this bubble yet."}
      </p>
      {cue.notes?.length > 0 && (
        <ul className="comic-card-help" aria-label="Sentence notes">
          {cue.notes.map((note, i) => (
            <li key={i}>
              <LearnerNote>{note}</LearnerNote>
            </li>
          ))}
        </ul>
      )}
      {alignment && (
        <section className="comic-card-notes" aria-label="Phrase explanation">
          <p className="comic-card-hint" hidden={active !== null}>
            Hover or click a phrase for its explanation.
          </p>
          {phraseNotes.map((notes, index) => (
            <div key={index} hidden={active !== index}>
              {notes.map((note, i) => (
                <p key={i}>
                  <LearnerNote>{note}</LearnerNote>
                </p>
              ))}
            </div>
          ))}
        </section>
      )}
      <div className="comic-card-actions">
        {document && (
          <Tooltip label="Ask a chat app about this bubble" withArrow>
            <ActionIcon
              variant={asking ? "light" : "subtle"}
              aria-label="Ask about this bubble"
              aria-expanded={asking}
              onClick={() => {
                setAsking((open) => !open);
                onKeep?.();
              }}
            >
              <MessageCircleQuestion size={18} />
            </ActionIcon>
          </Tooltip>
        )}
        <ListenButton
          text={pinned !== null ? alignment.source[pinned] : cue.source}
        />
      </div>
      {asking && (
        <AskBox
          document={document}
          cue={cue}
          phrase={pinned}
          onClose={() => setAsking(false)}
        />
      )}
    </div>
  );
}

/**
 * Takes the learner's question about a bubble and sends it with the bubble's notes to a chat app.
 * Opening ChatGPT or Claude also copies the message, because their prefilled links are unofficial
 * and may open an empty chat; Copy is for pasting into an existing chat, and Share uses the
 * system share sheet where the browser has one.
 */
function AskBox({ document, cue, phrase, onClose }) {
  const [question, setQuestion] = useState("");
  const [status, setStatus] = useState("");
  const explored =
    phrase == null ? null : (cue.alignment?.source?.[phrase] ?? null);
  const message = () => askMessage(document, cue, phrase, question);
  const copy = (text, done) =>
    navigator.clipboard.writeText(text).then(
      () => setStatus(done),
      () => setStatus("Could not copy: your browser blocked the clipboard."),
    );
  return (
    <div className="comic-card-ask">
      {explored && (
        <Text size="xs" c="dimmed">
          About “{explored}”
        </Text>
      )}
      <Textarea
        aria-label="Your question"
        placeholder="What do you want to ask?"
        autosize
        minRows={2}
        maxRows={6}
        size="sm"
        data-autofocus
        autoFocus
        value={question}
        onChange={(event) => setQuestion(event.currentTarget.value)}
        onKeyDown={(event) => {
          if (event.key === "Escape") {
            event.stopPropagation();
            onClose();
          }
        }}
      />
      <Group gap={6} mt={6}>
        {askTargets.map((target) => (
          <Button
            key={target.name}
            size="compact-sm"
            variant="light"
            onClick={() => {
              const text = message();
              const copied = copy(
                text,
                `Opened ${target.name} and copied the message: paste it if the chat is empty.`,
              );
              window.open(target.url(text), "_blank", "noopener");
              return copied;
            }}
          >
            {target.name} ↗
          </Button>
        ))}
        <Button
          size="compact-sm"
          variant="default"
          onClick={() => copy(message(), "Copied: paste it into a chat.")}
        >
          Copy
        </Button>
        {typeof navigator.share === "function" && (
          <Button
            size="compact-sm"
            variant="default"
            onClick={() => navigator.share({ text: message() }).catch(() => {})}
          >
            Share…
          </Button>
        )}
      </Group>
      {status && (
        <Text size="xs" c="dimmed" mt={6} role="status">
          {status}
        </Text>
      )}
    </div>
  );
}
