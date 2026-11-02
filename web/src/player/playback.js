// Hold the study cursor inside the completed subtitle without seeking the video.
// Seeking backwards here briefly changes the decoded frame and can flash the next cue.
export function pauseAtCueBoundary(video, cue) {
  video.pause();
  return Math.max(cue.start, cue.end - 0.001);
}

// A short restart window lets a second Left press reach the previous subtitle
// even when playback has advanced slightly since the first seek.
export function subtitleSeekTime(cues, index, time, direction) {
  if (!cues.length) return null;
  if (index < 0) return cues[0].start;
  const current = Math.min(index, cues.length - 1);
  const target =
    direction > 0
      ? Math.min(current + 1, cues.length - 1)
      : time > cues[current].start + 0.5
        ? current
        : Math.max(0, current - 1);
  return cues[target].start;
}

// Transport and transcript buttons retain focus after clicks. Space should continue
// playback from them, rather than activating their seek/replay action again.
export function playbackSpaceTarget(target) {
  if (
    target?.isContentEditable ||
    target?.closest?.('[role="slider"]') ||
    target?.closest?.('[contenteditable="true"]')
  )
    return false;
  if (["INPUT", "TEXTAREA", "SELECT", "A"].includes(target?.tagName))
    return false;
  if (target?.closest?.("button"))
    return Boolean(
      target.closest(
        ".cue-row, .playback-toolbar button, .alignment-phrase, .video-subtitle-phrase",
      ),
    );
  return true;
}

// Fullscreen belongs to the whole player, including interactive subtitles and controls.
export async function toggleFullscreen(container) {
  if (document.fullscreenElement === container) await document.exitFullscreen();
  else await container.requestFullscreen();
}

// Single-key video help remains available after using transport and phrase buttons.
export function videoHelpShortcut(event) {
  if (
    event.defaultPrevented ||
    event.repeat ||
    event.ctrlKey ||
    event.altKey ||
    event.metaKey ||
    !playbackSpaceTarget(event.target)
  )
    return null;
  const key = event.key.toLowerCase();
  return key === "t" ? "translation" : key === "w" ? "notes" : null;
}
