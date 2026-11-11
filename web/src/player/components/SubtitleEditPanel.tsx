import { useImperativeHandle, useReducer, useRef, useState } from "react";
import type { KeyboardEvent, RefObject } from "react";
import { Alert, Button, Group, Stack, Textarea, Text } from "@mantine/core";
import { Trash2 } from "lucide-react";
import type { CueView } from "../../shared/types.ts";
import { useUnsavedWarning } from "../../shared/useUnsavedWarning.ts";
import { useStudyLanguages } from "../StudyLanguages.jsx";
import { changed, dirty, lineEditor, openLine } from "../lineEditor.ts";
import type { EditMode } from "../lineEditor.ts";
import {
  blankLine,
  lineAfter,
  linePayload,
  neighbourTimings,
  splitDraft,
} from "../subtitle-edit.ts";
import type { Draft, Times } from "../subtitle-edit.ts";
import {
  addLine,
  commentOn,
  deleteLine,
  saveLine,
  saveTimings,
  splitLine,
} from "../subtitleEdits.ts";
import type { Edited } from "../subtitleEdits.ts";
import { draftLines, dragLink, dragTimes } from "../timeline.ts";
import type { Boundary, Link } from "../timeline.ts";
import { useLinePreview } from "../useLinePreview.ts";
import { useTimingAutosave } from "../useTimingAutosave.ts";
import { DeleteConfirm } from "./DeleteConfirm.tsx";
import { EditorHeader } from "./EditorHeader.tsx";
import { Timeline } from "./Timeline.tsx";
import { TimingSection } from "./TimingSection.tsx";
import "./SubtitleEditPanel.css";

/** Saving, splitting or deleting the edited line. */
type Operation = "save" | "split" | "delete";

interface Props {
  /** The title's API path, where edits are queued for review. */
  api: string;
  /** The line to edit, or null to add one at the playhead. */
  cue: CueView | null;
  cues: CueView[];
  video: RefObject<HTMLVideoElement | null>;
  /** Where a line-end pause holds the playhead, for the timeline to draw it there. */
  heldTime: Readonly<RefObject<number>>;
  seek: (seconds: number) => void;
  play: () => void;
  pause: () => void;
  /** Told the title's lines after each edit. */
  onSaved: (saved: Edited) => void;
  onClose: () => void;
  /** Told whether a preview is playing, so auto-pause lets it run past the saved end. */
  onPreview: (previewing: boolean) => void;
  /** Set to this editor's close, which answers whether it closed (it asks before discarding). */
  closeRef: RefObject<(() => boolean) | null>;
}

/**
 * A nonmodal subtitle editor: playback stays usable, and mutations use the existing review API.
 * Its timeline, under the video, times the line: a boundary the line shares with a neighbour,
 * or one dragged onto a neighbour's, moves both lines unless Alt is held, saved as one timing
 * edit ([useTimingAutosave]). Pressing another line on the timeline, or the previous or next line
 * button, saves this one first, then edits that one. Saving, splitting and deleting keep the
 * editor open, on the saved line, the split's first part or the line after the deleted one.
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
}: Props) {
  const { sourceName, sourceLanguage, translationLanguage } =
    useStudyLanguages();
  const blank = () =>
    blankLine(cues, video.current?.currentTime || 0, video.current?.duration);
  const [state, dispatch] = useReducer(lineEditor, null, () =>
    openLine(cue ?? blank(), cues, cue ? "edit" : "add"),
  );
  const { mode, original, draft, times, link, picked } = state;
  const [busy, setBusy] = useState(false);
  // Set while any edit is being written, so the timing's own saving waits its turn.
  const writing = useRef(false);
  // A timeline drag in progress; its timing is saved when it ends.
  const [dragging, setDragging] = useState(false);
  // Where the text cursor is in the source text, for splitting there.
  const cursor = useRef(0);
  const previews = useLinePreview(video, onPreview);
  const adding = mode === "add";
  const valid =
    times.end > times.start &&
    times.end <= (video.current?.duration || Infinity);
  const unsaved = dirty(state);
  const lines = timelineLines();
  const selected = adding
    ? lines.findIndex((line) => !line.id)
    : lines.findIndex((line) => line.id === original.id);
  const load = (line: Draft & Times, list: readonly CueView[], as?: EditMode) =>
    dispatch({ type: "load", line, cues: list, as });
  useTimingAutosave({
    api,
    state,
    cues,
    dragging,
    busy,
    writing,
    dispatch,
    onSaved,
  });
  useUnsavedWarning(unsaved);
  function close() {
    if (
      busy ||
      (unsaved && !window.confirm("Discard unsaved subtitle changes?"))
    )
      return false;
    onClose();
    return true;
  }
  useImperativeHandle(closeRef, () => close);
  function changeMode(next: EditMode) {
    if (
      next === mode ||
      (unsaved && !window.confirm("Discard unsaved subtitle changes?"))
    )
      return;
    const line = next === "add" ? blank() : state.editing;
    if (line) load(line, cues, next);
  }
  /** The timeline's lines, with this one as drafted. */
  function timelineLines() {
    const drafted = { ...original, ...draft, ...times };
    try {
      return draftLines(cues, original, drafted, link, adding);
    } catch {
      // Lines saved elsewhere since moved its neighbours; show it unlinked.
      return draftLines(
        cues,
        original,
        drafted,
        { previous: false, next: false },
        adding,
      );
    }
  }
  /**
   * A press at [at] seconds on the timeline's [index]th line: the playhead goes there, and that
   * line is edited, after saving this one if it changed.
   */
  function switchTo(index: number, at: number) {
    const line = lines[index];
    if (busy || !line?.id) return;
    seek(at);
    if (unsaved) apply("save", line.id);
    else load(line, cues);
  }
  /** Moves the line's times as a timeline drag or nudge asks, unless a neighbour would vanish. */
  function retime(next: Times, nextLink: Link) {
    try {
      if (!adding) neighbourTimings(cues, original, next, nextLink);
    } catch {
      return;
    }
    dispatch({ type: "retime", times: next, link: nextLink });
  }
  function drag(
    boundary: Boundary | "move",
    at: number,
    offset: number,
    apart: boolean,
    snap: number,
  ) {
    setDragging(true);
    if (boundary !== "move") dispatch({ type: "pick", boundary });
    const next = dragTimes(times, boundary, at, offset);
    retime(
      next,
      adding
        ? link
        : dragLink(cues, original, boundary, apart, link, next, snap),
    );
  }
  function setBoundary(boundary: Boundary, value: number) {
    dispatch({ type: "pick", boundary });
    const next = dragTimes(times, boundary, value);
    retime(
      next,
      adding ? link : dragLink(cues, original, boundary, false, link, next),
    );
  }
  const playhead = () => video.current?.currentTime ?? 0;
  /**
   * Timeline and timing keys: arrows nudge the picked end (Shift: 100 ms), [ and ] set the start
   * or end to the playhead. Other keys go on to the player, so Space plays and pauses.
   */
  function timingKey(event: KeyboardEvent) {
    const step = event.shiftKey ? 0.1 : 0.01;
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      event.preventDefault();
      event.stopPropagation();
      setBoundary(
        picked,
        times[picked] + (event.key === "ArrowLeft" ? -step : step),
      );
    } else if (event.key === "[" || event.key === "]") {
      event.preventDefault();
      event.stopPropagation();
      setBoundary(event.key === "[" ? "start" : "end", playhead());
    }
  }
  /**
   * Saves, splits or deletes the line, then goes on editing it, or with [nextId] that line.
   */
  async function apply(operation: Operation, nextId?: string) {
    if (writing.current) return;
    if (operation !== "delete" && (!valid || !draft.source.trim())) {
      dispatch({
        type: "error",
        message: `Enter ${sourceName} text and valid Start / End times within the video.`,
      });
      return;
    }
    const payload = linePayload(draft, times);
    let split;
    let moved: ReturnType<typeof neighbourTimings> = [];
    try {
      if (operation === "save" && mode === "edit")
        moved = neighbourTimings(cues, original, times, link);
      if (operation === "split") {
        if (
          state.comment.trim() ||
          (draft.translation || "") !== (original.translation || "")
        )
          throw Error("Save your translation or comment before splitting.");
        split = splitDraft(payload, cursor.current, playhead(), sourceName);
      }
    } catch (failure) {
      dispatch({ type: "error", message: (failure as Error).message });
      return;
    }
    writing.current = true;
    setBusy(true);
    dispatch({ type: "error", message: "" });
    pause();
    previews.cancel();
    try {
      let saved: Edited | undefined;
      let commented = original;
      // Neighbours move in one timing edit with this line, before any change to its text.
      if (moved.length) await saveTimings(api, original.id!, times, moved);
      if (operation !== "save" || adding || changed(state)) {
        saved =
          operation === "delete"
            ? await deleteLine(api, original.id!)
            : split
              ? await splitLine(api, original.id!, split)
              : adding
                ? await addLine(api, payload)
                : await saveLine(api, original.id!, payload);
        onSaved(saved);
        if (operation !== "save") {
          const following = lineAfter(operation, cues, original, saved.cues);
          if (following) {
            load(following, saved.cues);
            seek(following.start);
          } else onClose();
          return;
        }
        const updated = adding
          ? saved.cues.find((line) => !cues.some((old) => old.id === line.id))
          : saved.cues.find((line) => line.id === original.id);
        if (!updated)
          throw Error(
            "Subtitle saved. Close and reopen the panel before adding a comment.",
          );
        load(updated, saved.cues);
        // The returned id, so a failed comment can be retried without adding a second line.
        commented = updated;
      }
      const comment = state.comment.trim();
      if (comment) {
        saved = await commentOn(api, commented.id!, comment);
        onSaved(saved);
        dispatch({ type: "comment", text: "" });
      }
      // The editor stays open: on the next line asked for, or on this one as saved.
      const latest = saved?.cues ?? cues;
      const next = latest.find((line) => line.id === (nextId ?? commented.id));
      if (next) load(next, latest);
      else onClose();
    } catch (failure) {
      dispatch({ type: "error", message: (failure as Error).message });
    } finally {
      writing.current = false;
      setBusy(false);
    }
  }
  const startOf = (index: number) =>
    selected >= 0 && lines[index]?.id ? lines[index].start : null;
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
        onDragEnd={() => setDragging(false)}
        onPick={(boundary) => dispatch({ type: "pick", boundary })}
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
        <EditorHeader
          adding={adding}
          dirty={unsaved}
          busy={busy}
          previous={startOf(selected - 1)}
          next={startOf(selected + 1)}
          onStep={(step, start) => switchTo(selected + step, start)}
          onClose={close}
        />
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
              aria-pressed={adding}
              onClick={() => changeMode("add")}
            >
              Add at playhead
            </button>
          </Group>
          <Stack gap="md">
            <Textarea
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
                dispatch({
                  type: "draft",
                  changes: { source: e.currentTarget.value },
                })
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
                dispatch({
                  type: "draft",
                  changes: { translation: e.currentTarget.value },
                })
              }
            />
            <TimingSection
              times={times}
              picked={picked}
              status={state.timingStatus}
              busy={busy}
              valid={valid}
              splittable={mode === "edit"}
              sourceName={sourceName}
              onPick={(boundary) => dispatch({ type: "pick", boundary })}
              onSetToPlayhead={(boundary) => setBoundary(boundary, playhead())}
              onKeyDown={timingKey}
              onPreview={(from, to) => previews.preview(from, to, seek, play)}
              onSplit={() => apply("split")}
            />
            <Textarea
              label="Comment for review"
              placeholder="What should be checked?"
              value={state.comment}
              minRows={2}
              autosize
              disabled={busy}
              onChange={(e) =>
                dispatch({ type: "comment", text: e.currentTarget.value })
              }
            />
            <Text size="xs" c="dimmed">
              Changes use the existing annotation review queue.
            </Text>
            {state.error && (
              <Alert color="red" role="alert">
                {state.error}
              </Alert>
            )}
            {state.deleting && (
              <DeleteConfirm
                busy={busy}
                onConfirm={() => apply("delete")}
                onKeep={() => dispatch({ type: "deleting", asking: false })}
              />
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
              onClick={() => dispatch({ type: "deleting", asking: true })}
            >
              Delete
            </Button>
          )}
          <Group gap="xs" ml="auto">
            <Button variant="subtle" disabled={busy} onClick={close}>
              Close
            </Button>
            <Button
              color="coral"
              loading={busy}
              disabled={!unsaved || !valid}
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
