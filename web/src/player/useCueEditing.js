import { useEffect, useRef, useState } from "react";
import { request } from "../shared/api.js";
import { moveTiming } from "./model.js";

/** Coordinates cue mutations and timing autosaves through a shared write queue. */
export function useCueEditing({
  api,
  cues,
  setCues,
  setChanges,
  selected,
  setSelected,
  cue,
  displayIndex,
  editing,
  video,
  pause,
  seek,
  heldCue,
  heldTime,
  replayEnd,
  studyWorkspace,
  mergeSelection,
  setMergeSelection,
  selectionAnchor,
  setStatus,
}) {
  const [editor, setEditor] = useState(null);
  const [suggestion, setSuggestion] = useState(null);
  const [suggestionBusy, setSuggestionBusy] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [merging, setMerging] = useState(false);
  const [timingDragging, setTimingDragging] = useState(false);
  const timingSaveTimer = useRef(null);
  const editVersion = useRef(0);
  const saveQueue = useRef(Promise.resolve());
  const state = useRef({});
  state.current = { cues, selected };
  const [dirty, setDirty] = useState(false);
  useEffect(() => {
    if (!dirty) return;
    const warning = (event) => {
      event.preventDefault();
      event.returnValue = "";
    };
    addEventListener("beforeunload", warning);
    return () => removeEventListener("beforeunload", warning);
  }, [dirty]);

  function edit(index, mode, value, offset = 0) {
    if (!editing || editor || suggestion) return;
    setCues((old) => moveTiming(old, index, mode, value, offset));
    editVersion.current++;
    setDirty(true);
    setStatus("Unsaved timing changes");
  }
  function saveNow() {
    clearTimeout(timingSaveTimer.current);
    timingSaveTimer.current = null;
    const version = editVersion.current;
    const payload = state.current.cues.map((c) => ({
      id: c.id,
      start: c.start,
      end: c.end,
    }));
    const task = saveQueue.current
      .catch(() => {})
      .then(async () => {
        setStatus("Saving study document…");
        const result = await request(api + "/timings", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        if (editVersion.current === version) {
          const selectedId = state.current.cues[state.current.selected]?.id;
          const saved = await request(api);
          if (editVersion.current !== version) return result;
          setCues(saved.cues);
          setSelected(saved.cues.findIndex((cue) => cue.id === selectedId));
          setDirty(false);
          setStatus(`Saved ${result.lines} cues`);
        }
        return result;
      });
    saveQueue.current = task;
    return task;
  }
  async function openEditor(mode, line = cue) {
    if (editor) return;
    pause();
    try {
      if (dirty) await saveNow();
      await saveQueue.current.catch(() => {});
      const position = video.current?.currentTime || 0;
      const next = cues.find((c) => c.start > position);
      setEditor({
        mode,
        time: position,
        cue:
          mode === "add"
            ? {
                start: position,
                end: Math.min(position + 2, next?.start ?? position + 2),
                source: "",
                translation: "",
                notes: [],
                continuesPrevious: false,
              }
            : { ...line },
      });
    } catch (failure) {
      setStatus("Could not open editor: " + failure.message);
    }
  }

  async function submitSuggestion(event) {
    event.preventDefault();
    if (suggestionBusy) return;
    setSuggestionBusy(true);
    try {
      const updated = await request(
        api + "/cues/" + encodeURIComponent(suggestion.cueId) + "/suggestion",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            suggestion: suggestion.text,
          }),
        },
      );
      setCues(updated.cues);
      setChanges(await request(api + "/review-queue"));
      setSuggestion(null);
      setStatus(
        "Suggestion queued for listening review; subtitle text unchanged.",
      );
    } catch (failure) {
      setStatus("Could not submit suggestion: " + failure.message);
    } finally {
      setSuggestionBusy(false);
    }
  }

  async function openSuggestion() {
    pause();
    try {
      const target = cue;
      if (dirty) await saveNow();
      setSuggestion({
        cueId: target.id,
        sourceText: target.source,
        text: "",
        height: studyWorkspace.current.getBoundingClientRect().height,
      });
    } catch (failure) {
      setStatus("Could not open suggestion: " + failure.message);
    }
  }

  async function saveCue(mode, payload, target = editor?.cue) {
    const cueId = target?.id;
    const endpoint =
      mode === "add"
        ? "/cues"
        : "/cues/" +
          encodeURIComponent(cueId) +
          (mode === "split" ? "/split" : "");
    const task = saveQueue.current
      .catch(() => {})
      .then(() =>
        request(api + endpoint, {
          method:
            mode === "delete" ? "DELETE" : mode === "edit" ? "PUT" : "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        }),
      );
    saveQueue.current = task;
    const updated = await task;
    setCues(updated.cues);
    const history = await request(api + "/review-queue").catch(() => null);
    if (history) {
      setChanges(history);
    }
    // Metadata stays stable, so the native subtitle track needn't be recreated.
    setSelected(-1);
    heldCue.current = -1;
    heldTime.current = -1;
    replayEnd.current = null;
    setDirty(false);
    setStatus(
      !history
        ? "Subtitle saved; refresh to reload the review queue."
        : mode === "split"
          ? "Subtitle split. Review translations and notes for both parts."
          : mode === "delete"
            ? "Subtitle deleted."
            : "Subtitle saved",
    );
  }

  async function deleteCurrentCue() {
    if (!cue || editor || actionBusy || merging) return;
    const target = { ...cue };
    const next = cues[displayIndex + 1] || cues[displayIndex - 1];
    pause();
    setActionBusy(true);
    try {
      if (dirty) await saveNow();
      await saveCue("delete", undefined, target);
      setMergeSelection(new Set());
      selectionAnchor.current = null;
      if (next) seek(next.start);
    } catch (failure) {
      setStatus("Could not delete subtitle: " + failure.message);
    } finally {
      setActionBusy(false);
    }
  }

  async function mergeSelected() {
    if (merging) return;
    pause();
    setMerging(true);
    try {
      if (dirty) await saveNow();
      const task = saveQueue.current
        .catch(() => {})
        .then(() =>
          request(api + "/cues/merge", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ids: [...mergeSelection] }),
          }),
        );
      saveQueue.current = task;
      const updated = await task;
      setCues(updated.cues);
      setMergeSelection(new Set());
      setSelected(-1);
      heldCue.current = -1;
      replayEnd.current = null;
      setChanges(await request(api + "/review-queue"));
      setStatus(
        "Subtitles merged. Translation and notes are queued for review.",
      );
    } catch (failure) {
      setStatus("Could not merge subtitles: " + failure.message);
    } finally {
      setMerging(false);
    }
  }

  useEffect(() => {
    if (!dirty || timingDragging) return;
    timingSaveTimer.current = setTimeout(() => {
      timingSaveTimer.current = null;
      saveNow().catch((e) => setStatus("Save failed: " + e.message));
    }, 700);
    return () => clearTimeout(timingSaveTimer.current);
  }, [cues, dirty, timingDragging]);

  return {
    editor,
    setEditor,
    suggestion,
    setSuggestion,
    suggestionBusy,
    actionBusy,
    merging,
    dirty,
    setDirty,
    editVersion,
    timingSaveTimer,
    setTimingDragging,
    edit,
    saveNow,
    openEditor,
    submitSuggestion,
    openSuggestion,
    saveCue,
    deleteCurrentCue,
    mergeSelected,
  };
}
