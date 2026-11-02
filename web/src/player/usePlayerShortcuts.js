import { isComposing } from "./languages.js";
import { useEffect } from "react";
import {
  playbackSpaceTarget,
  toggleFullscreen,
  subtitleSeekTime,
  videoHelpShortcut,
} from "./playback.js";

/** Registers player shortcuts while respecting editable fields and handled events. */
export function usePlayerShortcuts({
  toggleCueTiming,
  openQuickEdit,
  definition,
  setDefinition,
  definitionCue,
  editor,
  video,
  videoContainer,
  setStatus,
  play,
  pause,
  seek,
  replay,
  displayIndex,
  cues,
  editing,
  selected,
  edit,
  setAutoPause,
  setVideoHelp,
  setOverlay,
  setMergeSelection,
  setSelectionMode,
  selectionAnchor,
  search,
}) {
  useEffect(() => {
    function key(event) {
      if (editor || event.defaultPrevented || isComposing(event)) return;
      const shortcutKey = event.key.toLowerCase();
      const defineTarget =
        !event.ctrlKey &&
        !event.altKey &&
        !event.metaKey &&
        playbackSpaceTarget(event.target);
      if (defineTarget && shortcutKey === "c") {
        event.preventDefault();
        if (!event.repeat) toggleCueTiming();
        return;
      }
      if (defineTarget && shortcutKey === "e") {
        event.preventDefault();
        if (!event.repeat) openQuickEdit();
        return;
      }
      if (defineTarget && shortcutKey === "d") {
        event.preventDefault();
        if (event.repeat) return;
        if (definition) setDefinition(null);
        else if (definitionCue?.alignment.source.length) {
          setOverlay(true);
          setDefinition({ cueId: definitionCue.id, index: 0 });
        }
        return;
      }
      if (definition && defineTarget) {
        if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
          event.preventDefault();
          setDefinition((current) =>
            current && definitionCue?.id === current.cueId
              ? {
                  ...current,
                  index: Math.max(
                    0,
                    Math.min(
                      definitionCue.alignment.source.length - 1,
                      current.index + (event.key === "ArrowLeft" ? -1 : 1),
                    ),
                  ),
                }
              : null,
          );
          return;
        }
        if (
          ["s", "w", "t", "e", "r", "escape"].includes(shortcutKey) ||
          event.code === "Space"
        )
          setDefinition(null);
      }
      if (event.key === "Escape") {
        setMergeSelection(new Set());
        setSelectionMode(false);
        selectionAnchor.current = null;
        return;
      }
      if (
        event.code === "Space" &&
        !event.ctrlKey &&
        !event.altKey &&
        !event.metaKey &&
        playbackSpaceTarget(event.target)
      ) {
        event.preventDefault();
        if (!event.repeat) {
          if (video.current?.paused) {
            play();
          } else pause();
        }
        return;
      }
      if (
        event.key.toLowerCase() === "f" &&
        !event.repeat &&
        !event.ctrlKey &&
        !event.altKey &&
        !event.metaKey &&
        playbackSpaceTarget(event.target) &&
        document.fullscreenEnabled &&
        videoContainer.current
      ) {
        event.preventDefault();
        toggleFullscreen(videoContainer.current).catch((error) =>
          setStatus(error.message),
        );
        return;
      }
      if (
        event.key.toLowerCase() === "s" &&
        !event.repeat &&
        !event.ctrlKey &&
        !event.altKey &&
        !event.metaKey &&
        playbackSpaceTarget(event.target)
      ) {
        event.preventDefault();
        setOverlay((current) => !current);
        return;
      }
      if (
        event.key.toLowerCase() === "a" &&
        !event.repeat &&
        !event.ctrlKey &&
        !event.altKey &&
        !event.metaKey &&
        playbackSpaceTarget(event.target)
      ) {
        event.preventDefault();
        setAutoPause((current) => !current);
        return;
      }
      const help = videoHelpShortcut(event);
      if (help) {
        event.preventDefault();
        setVideoHelp((current) => (current === help ? null : help));
        return;
      }
      if (
        (event.key === "ArrowLeft" || event.key === "ArrowRight") &&
        !event.ctrlKey &&
        !event.altKey &&
        !event.metaKey &&
        playbackSpaceTarget(event.target)
      ) {
        event.preventDefault();
        const time = video.current?.currentTime || 0;
        const direction = event.key === "ArrowLeft" ? -1 : 1;
        const target = event.shiftKey
          ? time + direction * 2
          : subtitleSeekTime(
              cues,
              time < cues[0]?.start ? -1 : displayIndex,
              time,
              direction,
            );
        if (target !== null) seek(target);
        return;
      }
      if (
        ["INPUT", "TEXTAREA", "SELECT", "BUTTON", "A"].includes(
          event.target.tagName,
        ) ||
        event.target.closest?.('[role="slider"]') ||
        event.ctrlKey ||
        event.altKey ||
        event.metaKey
      )
        return;
      if (event.key === "[" && editing && selected >= 0)
        edit(selected, "start", video.current.currentTime);
      else if (event.key === "]" && editing && selected >= 0)
        edit(selected, "end", video.current.currentTime);
      else if (event.key === "r") replay(displayIndex);
      else if (event.key === "/") {
        event.preventDefault();
        search.current?.focus();
      }
    }
    addEventListener("keydown", key);
    return () => removeEventListener("keydown", key);
  });
}
