import { useEffect, useRef, useState, useImperativeHandle } from "react";
import {
  ActionIcon,
  Alert,
  Button,
  Group,
  RangeSlider,
  Stack,
  Text,
  Textarea,
  TextInput,
} from "@mantine/core";
import { Crosshair, Play, Scissors, Trash2, X } from "lucide-react";
import { request } from "../../shared/api.js";
import { useStudyLanguages } from "../StudyLanguages.jsx";
import { editClock, editSeconds, splitDraft } from "../subtitle-edit.js";
import "./SubtitleEditPanel.css";

/** A nonmodal subtitle editor: playback stays usable, and mutations use the existing review API. */
export function SubtitleEditPanel({
  api,
  cue,
  cues,
  video,
  time,
  seek,
  play,
  pause,
  onSaved,
  onClose,
  closeRef,
}) {
  const { sourceName, sourceLanguage, translationLanguage } =
    useStudyLanguages();
  const [mode, setMode] = useState(cue ? "edit" : "add");
  const blank = () => {
    const start = video.current?.currentTime || 0;
    const next = cues.find((line) => line.start > start);
    return {
      start,
      end: Math.min(
        start + 2,
        next?.start ?? start + 2,
        video.current?.duration || Infinity,
      ),
      source: "",
      translation: "",
      notes: [],
    };
  };
  const [original, setOriginal] = useState(cue || blank);
  const [draft, setDraft] = useState(original);
  const [start, setStart] = useState(editClock(original.start));
  const [end, setEnd] = useState(editClock(original.end));
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const writing = useRef(false);
  const [error, setError] = useState("");
  const [deleting, setDeleting] = useState(false);
  const text = useRef(null);
  const cursor = useRef(0);
  const previewEnd = useRef(null);
  const bounds = useRef([Math.max(0, original.start - 2), original.end + 2]);
  const times = { start: editSeconds(start), end: editSeconds(end) };
  const valid =
    times.start !== null &&
    times.end !== null &&
    times.end > times.start &&
    times.end <= (video.current?.duration || Infinity);
  const changed =
    draft.source !== original.source ||
    (draft.translation || "") !== (original.translation || "") ||
    times.start !== original.start ||
    times.end !== original.end;
  const dirty =
    changed ||
    Boolean(comment.trim()) ||
    (mode === "add" && Boolean(draft.source.trim()));
  useEffect(() => {
    const element = video.current;
    let frame;
    function tick() {
      if (
        previewEnd.current !== null &&
        element.currentTime >= previewEnd.current
      ) {
        element.pause();
        element.currentTime = Math.max(0, previewEnd.current - 0.001);
        previewEnd.current = null;
      }
      frame = requestAnimationFrame(tick);
    }
    frame = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(frame);
      if (previewEnd.current !== null) element?.pause();
    };
  }, [video]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  function close() {
    if (busy || (dirty && !window.confirm("Discard unsaved subtitle changes?")))
      return false;
    onClose();
    return true;
  }
  useImperativeHandle(closeRef, () => close);
  function changeMode(next) {
    if (
      next === mode ||
      (dirty && !window.confirm("Discard unsaved subtitle changes?"))
    )
      return;
    const line = next === "add" ? blank() : cue;
    if (!line) return;
    setMode(next);
    setOriginal(line);
    setDraft(line);
    setStart(editClock(line.start));
    setEnd(editClock(line.end));
    setComment("");
    setError("");
    setDeleting(false);
    bounds.current = [Math.max(0, line.start - 2), line.end + 2];
  }
  function setBoundary(key, value) {
    const rounded = Math.max(0, Math.round(value * 1000) / 1000);
    (key === "start" ? setStart : setEnd)(editClock(rounded));
  }
  async function apply(operation) {
    if (writing.current) return;
    if (operation !== "delete" && (!valid || !draft.source.trim())) {
      setError(
        `Enter ${sourceName} text and valid Start / End times within the video.`,
      );
      return;
    }
    const payload = {
      ...draft,
      ...times,
      source: draft.source.trim(),
      translation: draft.translation?.trim() || null,
      notes: draft.notes || [],
      continuesPrevious: Boolean(draft.continuesPrevious),
      listeningCheck: draft.listeningCheck ?? null,
      textCheck: draft.textCheck ?? null,
    };
    let body = payload;
    try {
      if (operation === "split") {
        if (
          comment.trim() ||
          (draft.translation || "") !== (original.translation || "")
        )
          throw Error("Save your translation or comment before splitting.");
        body = splitDraft(payload, cursor.current, video.current.currentTime);
      }
    } catch (failure) {
      setError(failure.message);
      return;
    }
    writing.current = true;
    setBusy(true);
    setError("");
    pause();
    previewEnd.current = null;
    try {
      let saved;
      const target = `${api}/cues/${encodeURIComponent(original.id)}`;
      if (
        operation === "delete" ||
        operation === "split" ||
        mode === "add" ||
        changed
      ) {
        const url =
          operation === "split"
            ? target + "/split"
            : mode === "add"
              ? api + "/cues"
              : target;
        saved = await request(url, {
          method:
            operation === "delete"
              ? "DELETE"
              : operation === "split" || mode === "add"
                ? "POST"
                : "PUT",
          headers: { "Content-Type": "application/json" },
          ...(operation !== "delete" && { body: JSON.stringify(body) }),
        });
        onSaved(saved);
        if (operation === "delete" || operation === "split") {
          onClose();
          return;
        }
        const updated =
          mode === "add"
            ? saved.cues.find((line) => !cues.some((old) => old.id === line.id))
            : saved.cues.find((line) => line.id === original.id);
        if (!updated)
          throw Error(
            "Subtitle saved. Close and reopen the panel before adding a comment.",
          );
        setOriginal(updated);
        setDraft(updated);
        setStart(editClock(updated.start));
        setEnd(editClock(updated.end));
        setMode("edit");
        // Use the returned ID so a failed comment can be retried without adding a second cue.
        body = updated;
      } else body = original;
      if (comment.trim()) {
        saved = await request(
          `${api}/cues/${encodeURIComponent(body.id)}/comment`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ text: comment.trim() }),
          },
        );
        onSaved(saved);
        setComment("");
      }
      onClose();
    } catch (failure) {
      setError(failure.message);
    } finally {
      writing.current = false;
      setBusy(false);
    }
  }
  return (
    <aside
      className="subtitle-edit-panel"
      aria-label="Subtitle editor"
      onKeyDown={(event) => {
        event.stopPropagation();
        if (event.key === "Escape") {
          event.preventDefault();
          close();
        }
        if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
          event.preventDefault();
          apply("save");
        }
      }}
    >
      <header>
        <h2>
          {mode === "add" ? "Add subtitle" : "Edit subtitle"} <kbd>E</kbd>
        </h2>
        <ActionIcon
          variant="subtle"
          aria-label="Close subtitle editor"
          disabled={busy}
          onClick={close}
        >
          <X size={18} />
        </ActionIcon>
      </header>
      <div className="subtitle-edit-scroll">
        <Group className="subtitle-edit-tabs" gap="lg">
          <button
            disabled={!cue || busy}
            aria-pressed={mode === "edit"}
            onClick={() => changeMode("edit")}
          >
            Edit
          </button>
          <button
            disabled={busy}
            aria-pressed={mode === "add"}
            onClick={() => changeMode("add")}
          >
            Add at playhead
          </button>
        </Group>
        <Stack gap="md">
          <Textarea
            ref={text}
            label={sourceName}
            lang={sourceLanguage}
            value={draft.source}
            autosize
            minRows={2}
            maxRows={5}
            disabled={busy}
            onSelect={(e) => {
              cursor.current = e.currentTarget.selectionStart;
            }}
            onChange={(e) =>
              setDraft({ ...draft, source: e.currentTarget.value })
            }
          />
          <Textarea
            label="Translation"
            lang={translationLanguage}
            value={draft.translation || ""}
            autosize
            minRows={2}
            maxRows={5}
            disabled={busy}
            onChange={(e) =>
              setDraft({ ...draft, translation: e.currentTarget.value })
            }
          />
          <section className="subtitle-edit-timing">
            <h3>Timing</h3>
            <div className="subtitle-edit-boundaries">
              {["start", "end"].map((key) => (
                <div key={key}>
                  <TextInput
                    label={key === "start" ? "Start" : "End"}
                    aria-label={`${key} time`}
                    value={key === "start" ? start : end}
                    disabled={busy}
                    onChange={(e) =>
                      (key === "start" ? setStart : setEnd)(
                        e.currentTarget.value,
                      )
                    }
                    rightSection={
                      <ActionIcon
                        size="sm"
                        variant="subtle"
                        disabled={busy}
                        aria-label={`Set ${key} to playhead`}
                        onClick={() =>
                          setBoundary(key, video.current.currentTime)
                        }
                      >
                        <Crosshair size={15} />
                      </ActionIcon>
                    }
                  />
                  <Group gap={4} grow mt={5}>
                    {[-0.1, 0.1].map((delta) => (
                      <Button
                        key={delta}
                        variant="default"
                        size="compact-xs"
                        aria-label={`Nudge ${key} ${delta < 0 ? "earlier" : "later"} by 100 milliseconds`}
                        disabled={busy || times[key] === null}
                        onClick={() => setBoundary(key, times[key] + delta)}
                      >
                        {delta < 0 ? "−" : "+"} 100 ms
                      </Button>
                    ))}
                  </Group>
                </div>
              ))}
            </div>
            <div className="subtitle-edit-track">
              <RangeSlider
                aria-label="Subtitle boundaries"
                thumbFromLabel="Subtitle start"
                thumbToLabel="Subtitle end"
                color="coral"
                min={Math.min(bounds.current[0], times.start ?? 0)}
                max={Math.max(bounds.current[1], times.end ?? 0)}
                step={0.01}
                minRange={0.01}
                value={
                  valid
                    ? [times.start, times.end]
                    : [original.start, original.end]
                }
                label={editClock}
                disabled={busy}
                onChange={([a, b]) => {
                  setStart(editClock(a));
                  setEnd(editClock(b));
                }}
              />
              <Text size="xs" c="dimmed" mt="sm">
                Playhead {editClock(time)}
              </Text>
              <Button
                mt="xs"
                size="compact-sm"
                variant="subtle"
                leftSection={<Play size={14} />}
                disabled={busy || !valid}
                onClick={() => {
                  seek(times.start);
                  previewEnd.current = times.end;
                  play();
                }}
              >
                Preview line
              </Button>
              <Group gap={6} mt={5}>
                <Button
                  size="compact-xs"
                  variant="subtle"
                  onClick={() =>
                    seek(Math.max(0, (video.current?.currentTime || 0) - 0.1))
                  }
                >
                  − 100 ms
                </Button>
                <Button
                  size="compact-xs"
                  variant="subtle"
                  onClick={() => seek((video.current?.currentTime || 0) + 0.1)}
                >
                  + 100 ms
                </Button>
              </Group>
            </div>
            {mode === "edit" && (
              <>
                <Button
                  fullWidth
                  variant="default"
                  mt="sm"
                  leftSection={<Scissors size={16} />}
                  disabled={busy}
                  onClick={() => apply("split")}
                >
                  Split at cursor &amp; playhead
                </Button>
                <Text size="xs" c="dimmed" mt={5}>
                  Place the {sourceName} text cursor at the break; seek to the
                  split time.
                </Text>
              </>
            )}
          </section>
          <Textarea
            label="Comment for review"
            placeholder="What should be checked?"
            value={comment}
            minRows={2}
            autosize
            disabled={busy}
            onChange={(e) => setComment(e.currentTarget.value)}
          />
          <Text size="xs" c="dimmed">
            Changes use the existing annotation review queue.
          </Text>
          {error && (
            <Alert color="red" role="alert">
              {error}
            </Alert>
          )}
          {deleting && (
            <Alert color="red" title="Delete this subtitle?">
              This removes the line and queues the deletion for review.
              <Group mt="sm">
                <Button
                  color="red"
                  size="xs"
                  disabled={busy}
                  onClick={() => apply("delete")}
                >
                  Confirm delete
                </Button>
                <Button
                  size="xs"
                  variant="default"
                  onClick={() => setDeleting(false)}
                >
                  Keep subtitle
                </Button>
              </Group>
            </Alert>
          )}
        </Stack>
      </div>
      <footer>
        {mode === "edit" && (
          <Button
            color="red"
            variant="subtle"
            disabled={busy}
            leftSection={<Trash2 size={16} />}
            onClick={() => setDeleting(true)}
          >
            Delete
          </Button>
        )}
        <Group gap="xs" ml="auto">
          <Button variant="subtle" disabled={busy} onClick={close}>
            Cancel
          </Button>
          <Button
            color="coral"
            loading={busy}
            disabled={!dirty || !valid}
            onClick={() => apply("save")}
          >
            Save changes
          </Button>
        </Group>
      </footer>
    </aside>
  );
}
