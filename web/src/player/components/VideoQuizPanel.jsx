import { useState } from "react";
import {
  ActionIcon,
  Alert,
  SegmentedControl,
  Text,
  TextInput,
} from "@mantine/core";
import { Crosshair, X } from "lucide-react";
import rules from "../../../../agents/workflows/subtitle-quiz.md?raw";
import types from "../../../../agents/workflows/quiz-types.md?raw";
import { QUIZ_TYPES, quizBrief, quizFileName, quizType } from "../quiz.js";
import { editClock, editSeconds } from "../subtitle-edit.js";
import { useStoredState } from "../../shared/storage.js";
import { QuizActions } from "../../library/QuizActions.jsx";
import "./SubtitleEditPanel.css";
import "./VideoQuizPanel.css";

/** Builds a subtitle quiz while the video remains available for selecting its range. */
export function VideoQuizPanel({ doc, cues, video, time, onClose }) {
  const [scope, setScope] = useState("whole");
  const [from, setFrom] = useState("0:00.000");
  const [to, setTo] = useState(() =>
    editClock(video.current?.currentTime || video.current?.duration || 0),
  );
  const [typeId, setTypeId] = useStoredState("quiz.type", QUIZ_TYPES[0].id);
  const type = quizType(types, typeId, doc);
  const start = editSeconds(from),
    end = editSeconds(to);
  const duration = video.current?.duration;
  const invalid =
    scope === "range" &&
    (start === null ||
      end === null ||
      end <= start ||
      (Number.isFinite(duration) && end > duration));
  const range = scope === "range" ? { start, end } : null;
  const selected = invalid
    ? []
    : cues.filter(
        (cue) =>
          cue.source?.trim() &&
          (!range || (cue.start < end && cue.end > start)),
      );
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
            value={scope}
            onChange={setScope}
            data={[
              { value: "whole", label: "Whole video" },
              { value: "range", label: "Time range" },
            ]}
          />
          {scope === "range" && (
            <>
              <div className="subtitle-edit-boundaries">
                {[
                  ["Start", from, setFrom],
                  ["End", to, setTo],
                ].map(([label, value, setValue]) => (
                  <TextInput
                    key={label}
                    label={label}
                    value={value}
                    onChange={(event) => setValue(event.currentTarget.value)}
                    rightSection={
                      <ActionIcon
                        size="sm"
                        variant="subtle"
                        aria-label={`Set quiz ${label.toLowerCase()} to playhead`}
                        onClick={() =>
                          setValue(editClock(video.current?.currentTime || 0))
                        }
                      >
                        <Crosshair size={15} />
                      </ActionIcon>
                    }
                  />
                ))}
              </div>
              <Text size="xs" c="dimmed" mt="xs">
                Seek in the video, then use the target buttons to set each
                boundary. Playhead {editClock(time)}.
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
            {QUIZ_TYPES.map((item) => (
              <button
                key={item.id}
                aria-pressed={type.id === item.id}
                onClick={() => setTypeId(item.id)}
              >
                {item.label(doc)}
              </button>
            ))}
          </div>
        </section>
        {invalid ? (
          <Alert color="red" role="alert">
            Enter valid Start and End times within the video, with End later
            than Start. Use M:SS.mmm or H:MM:SS.mmm.
          </Alert>
        ) : !selected.length ? (
          <Alert color="yellow" role="status">
            No subtitle lines in this selection.
          </Alert>
        ) : (
          <Text className="video-quiz-summary" size="sm">
            {selected.length} subtitle{" "}
            {selected.length === 1 ? "line" : "lines"} ·{" "}
            {scope === "whole"
              ? "Whole video"
              : `${editClock(start)} – ${editClock(end)}`}
          </Text>
        )}
      </div>
      <footer>
        <QuizActions
          key={`${scope}:${from}:${to}:${type.id}`}
          color="coral"
          disabled={invalid || !selected.length}
          buildBrief={() => quizBrief(rules, doc, cues, range, type)}
          fileName={() =>
            quizFileName(
              doc,
              range ? `${editClock(start)} to ${editClock(end)}` : "",
            )
          }
        />
      </footer>
    </aside>
  );
}
