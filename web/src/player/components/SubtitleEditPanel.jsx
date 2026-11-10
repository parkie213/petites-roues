import { useEffect, useRef, useState, useImperativeHandle } from "react";
import {
  ActionIcon,
  Alert,
  Button,
  Group,
  Stack,
  Text,
  Textarea,
} from "@mantine/core";
import { Crosshair, Play, Scissors, Trash2, X } from "lucide-react";
import { request } from "../../shared/api.ts";
import { useStudyLanguages } from "../StudyLanguages.jsx";
import {
  editClock,
  neighbourTimings,
  neighbours,
  splitDraft,
} from "../subtitle-edit.js";
import { draftLines, dragLink, dragTimes } from "../timeline.ts";
import { Timeline } from "./Timeline.tsx";
import "./SubtitleEditPanel.css";

/**
 * A nonmodal subtitle editor: playback stays usable, and mutations use the existing review API.
 * Its timeline, under the video, times the line: a boundary the line shares with a neighbour
 * moves both lines unless Alt is held, saved as one timing edit. Pressing another line on the
 * timeline saves this one first, then edits that one. {@code onPreview} is told while a preview
 * plays, so auto-pause can let it run past the line's saved end.
 */
export function SubtitleEditPanel({
  api,
  cue,
  cues,
  video,
  heldTime,
  seek,
  play,
  pause,
  onSaved,
  onClose,
  onPreview,
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
  // The line the Edit tab returns to from Add.
  const [editing, setEditing] = useState(cue);
  const [draft, setDraft] = useState(original);
  const [times, setTimes] = useState(() => ({
    start: original.start,
    end: original.end,
  }));
  const [link, setLink] = useState(() => neighbours(cues, original).touching);
  // The boundary arrow keys nudge and [ or ] set.
  const [picked, setPicked] = useState("end");
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const writing = useRef(false);
  const [error, setError] = useState("");
  const [deleting, setDeleting] = useState(false);
  const text = useRef(null);
  const cursor = useRef(0);
  const previewEnd = useRef(null);
  const valid =
    times.end > times.start &&
    times.end <= (video.current?.duration || Infinity);
  const adding = mode === "add";
  const lines = timelineLines();
  const selected = adding
    ? lines.findIndex((line) => !line.id)
    : lines.findIndex((line) => line.id === original.id);
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
    // Pausing, by the preview reaching its end or by hand, ends it.
    const stopped = () => {
      previewEnd.current = null;
      onPreview(false);
    };
    element?.addEventListener("pause", stopped);
    return () => {
      cancelAnimationFrame(frame);
      element?.removeEventListener("pause", stopped);
      if (previewEnd.current !== null) element?.pause();
      onPreview(false);
    };
  }, [video, onPreview]);
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
    const line = next === "add" ? blank() : editing;
    if (!line) return;
    load(line, cues, next);
  }
  /** The timeline's lines, with this one as drafted. */
  function timelineLines() {
    try {
      return draftLines(
        cues,
        original,
        { ...original, ...draft, ...times },
        link,
        adding,
      );
    } catch {
      // Lines saved elsewhere since moved its neighbours; show it unlinked.
      return draftLines(
        cues,
        original,
        { ...original, ...draft, ...times },
        { previous: false, next: false },
        adding,
      );
    }
  }
  /** Starts editing (or adding) [line], one of [list]. */
  function load(line, list, as = "edit") {
    setMode(as);
    setOriginal(line);
    if (as === "edit") setEditing(line);
    setDraft(line);
    setTimes({ start: line.start, end: line.end });
    setLink(neighbours(list, line).touching);
    setComment("");
    setError("");
    setDeleting(false);
  }
  /**
   * A press at [at] seconds on the timeline's [index]th line: the playhead goes there, and that
   * line is edited, after saving this one if it changed.
   */
  function switchTo(index, at) {
    const line = lines[index];
    if (busy || !line?.id) return;
    seek(at);
    if (dirty) apply("save", line.id);
    else load(line, cues);
  }
  /** Moves the line's times as a timeline drag or nudge asks, unless a neighbour would vanish. */
  function retime(next, nextLink) {
    try {
      if (!adding) neighbourTimings(cues, original, next, nextLink);
    } catch {
      return;
    }
    setTimes(next);
    setLink(nextLink);
  }
  function drag(mode, at, offset, apart) {
    if (mode !== "move") setPicked(mode);
    retime(
      dragTimes(times, mode, at, offset),
      adding ? link : dragLink(cues, original, mode, apart, link),
    );
  }
  function setBoundary(key, value) {
    setPicked(key);
    retime(
      dragTimes(times, key, value),
      adding ? link : dragLink(cues, original, key, false, link),
    );
  }
  /** Timeline and timing keys: arrows nudge the picked end (Shift: 100 ms), [ and ] set it. */
  function timingKey(event) {
    event.stopPropagation();
    const step = event.shiftKey ? 0.1 : 0.01;
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      event.preventDefault();
      setBoundary(
        picked,
        times[picked] + (event.key === "ArrowLeft" ? -step : step),
      );
    } else if (event.key === "[" || event.key === "]") {
      event.preventDefault();
      setBoundary(
        event.key === "[" ? "start" : "end",
        video.current.currentTime,
      );
    }
  }
  /** Plays from [from] to [to], then pauses there. */
  function preview(from, to) {
    seek(Math.max(0, from));
    previewEnd.current = to;
    onPreview(true);
    play();
  }
  /**
   * Saves, splits or deletes the line, then closes, or with [nextId] goes on to edit that line.
   */
  async function apply(operation, nextId) {
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
    let moved = [];
    try {
      if (operation === "save" && mode === "edit")
        moved = neighbourTimings(cues, original, times, link);
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
      // Neighbours move in one timing edit with this line, before any change to its text.
      if (moved.length)
        await request(api + "/timings", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify([{ id: original.id, ...times }, ...moved]),
        });
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
        load(updated, saved.cues);
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
      const latest = saved?.cues ?? cues;
      const next = nextId && latest.find((line) => line.id === nextId);
      if (next) load(next, latest);
      else onClose();
    } catch (failure) {
      setError(failure.message);
    } finally {
      writing.current = false;
      setBusy(false);
    }
  }
  return (
    <>
      <Timeline
        lines={lines}
        selected={selected}
        picked={picked}
        videoRef={video}
        heldTimeRef={heldTime}
        onSeek={seek}
        onSelect={switchTo}
        onDrag={drag}
        onPick={setPicked}
        onKeyDown={timingKey}
      />
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
              <div className="subtitle-edit-times" onKeyDown={timingKey}>
                {["start", "end"].map((key) => (
                  <Group key={key} gap={2} wrap="nowrap">
                    <Button
                      size="compact-sm"
                      variant="subtle"
                      aria-label={`${key} time`}
                      aria-pressed={picked === key}
                      title={`Arrow keys nudge the ${key}; ${key === "start" ? "[" : "]"} sets it to the playhead`}
                      disabled={busy}
                      onClick={() => setPicked(key)}
                    >
                      {editClock(times[key])}
                    </Button>
                    <ActionIcon
                      size="sm"
                      variant="subtle"
                      disabled={busy}
                      aria-label={`Set ${key} to playhead`}
                      title={`Set ${key} to playhead (${key === "start" ? "[" : "]"})`}
                      onClick={() =>
                        setBoundary(key, video.current.currentTime)
                      }
                    >
                      <Crosshair size={14} />
                    </ActionIcon>
                    {key === "start" && <span aria-hidden="true">→</span>}
                  </Group>
                ))}
                <Text size="xs" c="dimmed" ml="auto">
                  {(times.end - times.start).toFixed(2)} s
                </Text>
              </div>
              <Text size="xs" c="dimmed" mt={4}>
                Drag the line or its ends on the timeline; Alt-drag moves only
                this line. Arrow keys nudge the chosen end.
              </Text>
              <Group gap={4} mt="xs">
                <Button
                  size="compact-sm"
                  variant="subtle"
                  leftSection={<Play size={14} />}
                  disabled={busy || !valid}
                  onClick={() => preview(times.start, times.end)}
                >
                  Preview line
                </Button>
                <Button
                  size="compact-sm"
                  variant="subtle"
                  title="Play the line's last two seconds"
                  disabled={busy || !valid}
                  onClick={() =>
                    preview(Math.max(times.start, times.end - 2), times.end)
                  }
                >
                  Last 2 s
                </Button>
                {["start", "end"].map((key) => (
                  <Button
                    key={key}
                    size="compact-sm"
                    variant="subtle"
                    title={`Play a second either side of the ${key}`}
                    disabled={busy || !valid}
                    onClick={() => preview(times[key] - 1, times[key] + 1)}
                  >
                    Across {key}
                  </Button>
                ))}
              </Group>
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
    </>
  );
}
