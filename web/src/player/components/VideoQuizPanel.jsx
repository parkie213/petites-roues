import { useEffect, useRef, useState } from "react";
import {
  ActionIcon,
  Alert,
  SegmentedControl,
  Text,
  TextInput,
} from "@mantine/core";
import { Crosshair, X } from "lucide-react";
import { clock } from "../quiz.js";
import { prepareVideoQuiz } from "../video-quiz.js";
import { useStoredState } from "../../shared/storage.ts";
import { QuizActions } from "../../library/QuizActions.jsx";
import "./SubtitleEditPanel.css";
import "./VideoQuizPanel.css";

/** How long typing in Start or End pauses before the desktop is asked about it. */
const TYPING_DELAY_MS = 300;

/**
 * Builds a subtitle quiz while the video remains available for selecting its range. The desktop
 * owns everything about the quiz (default scope, parsing, counting, rules and the brief): the
 * panel sends the selection, shows the reply, and exports its `text` only once the reply for the
 * current selection has arrived, so the clipboard call stays synchronous in the click.
 */
export function VideoQuizPanel({ id, video, time, onClose }) {
  // Until the learner edits the form, no scope is sent and the desktop's default applies.
  const [form, setForm] = useState(null);
  const [typing, setTyping] = useState(false);
  const [typeId, setTypeId] = useStoredState("quiz.type", "to-source");
  const [reply, setReply] = useState(null);
  const opened = useRef(null);
  opened.current ??= {
    position: video.current?.currentTime || 0,
    duration: Number.isFinite(video.current?.duration)
      ? video.current.duration
      : undefined,
  };
  const selection = {
    ...opened.current,
    ...(form && { scope: form.kind, start: form.start, end: form.end }),
    type: typeId,
  };
  const key = JSON.stringify(selection);
  const latest = useRef(0);
  const first = useRef(true);
  useEffect(() => {
    const delay = first.current || !typing ? 0 : TYPING_DELAY_MS;
    first.current = false;
    const timer = setTimeout(() => {
      // Requests are never aborted (the journey guard fails an aborted JSON read); a reply for an
      // older selection is simply dropped.
      const sequence = ++latest.current;
      prepareVideoQuiz(id, JSON.parse(key)).then(
        (data) => sequence === latest.current && setReply({ key, data }),
        (error) => sequence === latest.current && setReply({ key, error }),
      );
    }, delay);
    return () => clearTimeout(timer);
    // `typing` only shortens the wait for the selection that `key` already names.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, key]);
  const data = reply?.data;
  const ready = reply?.key === key && data ? data : null;
  const shown = form ?? {
    kind: data?.scope.kind ?? "whole",
    start: data?.scope.start ?? "",
    end: data?.scope.end ?? "",
  };
  const edit = (changes, typed = false) => {
    setTyping(typed);
    setForm({ ...shown, ...changes });
  };
  const playhead = () => video.current?.currentTime || 0;
  const types = data?.types ?? [];
  const type = types.find((item) => item.id === typeId) ?? types[0];
  return (
    <aside
      id="video-quiz-dock"
      className="subtitle-edit-panel video-quiz-panel"
      aria-label="Video quiz"
      onKeyDown={(event) => {
        event.stopPropagation();
        if (event.key === "Escape") {
          event.preventDefault();
          onClose();
        }
      }}
    >
      <header>
        <h2>Quiz me</h2>
        <ActionIcon
          variant="subtle"
          aria-label="Close video quiz"
          onClick={onClose}
        >
          <X size={18} />
        </ActionIcon>
      </header>
      <div className="subtitle-edit-scroll">
        <Text size="sm" c="dimmed" mb="lg">
          Practise with the video’s own dialogue.
        </Text>
        <section className="video-quiz-scope">
          <h3>Quiz scope</h3>
          <SegmentedControl
            fullWidth
            color="coral"
            aria-label="Quiz scope"
            value={shown.kind}
            onChange={(kind) => edit({ kind })}
            data={[
              { value: "whole", label: "Whole title" },
              { value: "range", label: "Time range" },
            ]}
          />
          {shown.kind === "range" && (
            <>
              <div className="subtitle-edit-boundaries">
                {[
                  ["Start", "start", Math.floor],
                  ["End", "end", Math.ceil],
                ].map(([label, field, round]) => (
                  <TextInput
                    key={label}
                    label={label}
                    value={shown[field]}
                    onChange={(event) =>
                      edit({ [field]: event.currentTarget.value }, true)
                    }
                    rightSection={
                      <ActionIcon
                        size="sm"
                        variant="subtle"
                        aria-label={`Set quiz ${field} to playhead`}
                        onClick={() =>
                          edit({ [field]: clock(round(playhead())) })
                        }
                      >
                        <Crosshair size={15} />
                      </ActionIcon>
                    }
                  />
                ))}
              </div>
              <Text size="xs" c="dimmed" mt="xs">
                Type times as m:ss or h:mm:ss, or seek in the video and use the
                target buttons. Playhead {clock(time)}.
              </Text>
            </>
          )}
        </section>
        <section className="video-quiz-types">
          <h3>Quiz type</h3>
          <div
            role="group"
            aria-label="Quiz type"
            className="video-quiz-type-grid"
          >
            {types.map((item) => (
              <button
                key={item.id}
                aria-pressed={type?.id === item.id}
                onClick={() => {
                  setTyping(false);
                  setTypeId(item.id);
                }}
              >
                {item.label}
              </button>
            ))}
          </div>
        </section>
        {reply?.error ? (
          <Alert color="red" role="alert">
            Could not prepare the quiz: {reply.error.message}
          </Alert>
        ) : data && !data.valid ? (
          <Alert color="red" role="alert">
            Enter valid Start and End times as m:ss or h:mm:ss, with End later
            than Start.
          </Alert>
        ) : data && !data.lineCount ? (
          <Alert color="yellow" role="status">
            No subtitle lines in this selection.
          </Alert>
        ) : (
          data && (
            <>
              <Text className="video-quiz-summary" size="sm">
                {data.lineCount} subtitle{" "}
                {data.lineCount === 1 ? "line" : "lines"} ·{" "}
                {data.scope.kind === "whole"
                  ? "Whole title"
                  : `${data.scope.start} – ${data.scope.end}`}
              </Text>
              {data.large && (
                <Alert color="yellow" role="status">
                  More than {data.largeAbove} lines is a long brief. A shorter
                  time range works better in a chat.
                </Alert>
              )}
            </>
          )
        )}
      </div>
      <footer>
        <QuizActions
          key={key}
          color="coral"
          disabled={!ready?.valid || !ready.text}
          buildBrief={() => ready.text}
          fileName={() => ready.fileName}
        />
      </footer>
    </aside>
  );
}
