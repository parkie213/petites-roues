import { useCallback, useEffect, useRef, useState } from "react";
import { ActionIcon, Button, Group, Loader, Switch, Text } from "@mantine/core";
import { Pencil } from "lucide-react";
import { titlePath } from "../shared/api.js";
import { useStoredState } from "../shared/storage.js";
import { activeCues, currentCue } from "./model.js";
import { useStudyDocument } from "./useStudyDocument.js";
import { usePlayerPlayback } from "./usePlayerPlayback.js";
import {
  playbackSpaceTarget,
  subtitleSeekTime,
  toggleFullscreen,
  videoHelpShortcut,
} from "./playback.js";
import { isComposing } from "./languages.js";
import { PlayerShortcuts } from "./components/PlayerShortcuts.jsx";
import { QuickEdit } from "./components/QuickEdit.jsx";
import { VideoQuizShare } from "./components/QuizShare.jsx";
import { VideoControls } from "./components/VideoControls.jsx";
import { VideoStudyHelp } from "./components/VideoStudyHelp.jsx";
import { VideoSubtitles } from "./components/VideoSubtitles.jsx";
import { StudyLanguagesProvider } from "./StudyLanguages.jsx";
import "./Player.css";
import "./Watch.css";

/**
 * The plain viewing page: the video with interactive subtitles and whole-sentence help, and none
 * of the editing tools. Shares playback position with the editor, which opens at the same place.
 */
export function Watch({ id }) {
  const api = titlePath(id),
    prefix = `sacre-bleu:${id}`;
  const [status, setStatus] = useState("");
  const { doc, cues, setCues, error, returnPath } = useStudyDocument({
    id,
    api,
    setStatus,
  });
  const [overlay, setOverlay] = useStoredState(prefix + ":overlay", true);
  const [autoPause, setAutoPause] = useStoredState(
    prefix + ":watch:autoPause",
    false,
  );
  const [videoHelp, setVideoHelp] = useState(null);
  const [quickEdit, setQuickEdit] = useState(null);
  const [activePhraseCues, setActivePhraseCues] = useState(() => new Set());
  const setPhraseHelp = useCallback((cueId, visible) => {
    setActivePhraseCues((current) => {
      if (current.has(cueId) === visible) return current;
      const next = new Set(current);
      if (visible) next.add(cueId);
      else next.delete(cueId);
      return next;
    });
  }, []);
  const container = useRef(null);
  const clickTimer = useRef(null);
  useEffect(() => () => clearTimeout(clickTimer.current), []);
  const {
    video,
    time,
    setTime,
    playing,
    setPlaying,
    heldCue,
    heldTime,
    lastTime,
    seek,
    play,
    pause,
    replay,
  } = usePlayerPlayback({
    api,
    doc,
    cues,
    autoPause,
    speed: 1,
    setSelected: () => {},
    setStatus,
  });
  const active = currentCue(cues, time);
  const showing = activeCues(cues, time);

  function openQuickEdit() {
    const line = cues[Math.max(0, active)];
    if (!line) return;
    pause();
    setQuickEdit(line);
  }
  const keys = useRef({});
  keys.current = {
    cues,
    active,
    time,
    play,
    pause,
    replay,
    seek,
    openQuickEdit,
  };
  useEffect(() => {
    function keydown(event) {
      if (event.defaultPrevented || isComposing(event)) return;
      const { cues, active, time, play, pause, replay, seek, openQuickEdit } =
        keys.current;
      const help = videoHelpShortcut(event);
      if (help) {
        event.preventDefault();
        setVideoHelp((current) => (current === help ? null : help));
        pause();
        return;
      }
      if (
        event.ctrlKey ||
        event.altKey ||
        event.metaKey ||
        !playbackSpaceTarget(event.target)
      )
        return;
      const key = event.key.toLowerCase();
      if (event.code === "Space") {
        event.preventDefault();
        video.current?.paused ? play() : pause();
      } else if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
        event.preventDefault();
        if (event.shiftKey) seek(time + (event.key === "ArrowLeft" ? -2 : 2));
        else {
          const target = subtitleSeekTime(
            cues,
            active,
            time,
            event.key === "ArrowLeft" ? -1 : 1,
          );
          if (target != null) seek(target);
        }
      } else if (key === "r" && !event.repeat) replay(Math.max(0, active));
      else if (key === "e" && !event.repeat) {
        event.preventDefault();
        openQuickEdit();
      } else if (key === "s" && !event.repeat) setOverlay((value) => !value);
      else if (key === "a" && !event.repeat) setAutoPause((value) => !value);
      else if (key === "f" && !event.repeat && container.current)
        toggleFullscreen(container.current).catch((e) => setStatus(e.message));
    }
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [video, setOverlay, setAutoPause]);

  if (error)
    return (
      <main className="page">
        <a href={returnPath}>← Library</a>
        <h1>Could not open title</h1>
        <p role="alert" className="error">
          {error}
        </p>
      </main>
    );
  if (!doc)
    return (
      <Group justify="center" py="xl" role="status">
        <Loader size="sm" />
        <Text>Loading…</Text>
      </Group>
    );
  return (
    <StudyLanguagesProvider document={doc}>
      <main className="left watch">
        <Group className="watch-bar" gap="sm" px="md" py="xs" wrap="nowrap">
          <Button component="a" size="xs" variant="default" href={returnPath}>
            <span aria-hidden="true">←</span> Library
          </Button>
          <Text fw={600} size="sm" truncate style={{ flex: 1 }}>
            {doc.title}
          </Text>
          <Button
            component="a"
            size="xs"
            variant="default"
            href={`/player/${encodeURIComponent(id)}/`}
          >
            Edit
          </Button>
        </Group>
        <div className="video-player" ref={container}>
          <div className="video-stage">
            <video
              ref={video}
              preload="metadata"
              src={api + "/media"}
              onClick={(event) => {
                // Wait briefly so a double click goes fullscreen without toggling playback.
                if (event.detail > 1) return;
                clearTimeout(clickTimer.current);
                clickTimer.current = setTimeout(() => {
                  if (video.current) video.current.paused ? play() : pause();
                }, 300);
              }}
              onDoubleClick={() => {
                clearTimeout(clickTimer.current);
                toggleFullscreen(container.current).catch((e) =>
                  setStatus(e.message),
                );
              }}
              onPlay={() => {
                setVideoHelp(null);
                heldTime.current = -1;
                setPlaying(true);
              }}
              onPause={() => setPlaying(false)}
              onSeeking={() => {
                const value = video.current.currentTime;
                if (Math.abs(value - heldTime.current) > 0.0001) {
                  heldCue.current = -1;
                  heldTime.current = -1;
                }
                lastTime.current = value;
                setTime(value);
              }}
              onError={() =>
                setStatus(
                  "This browser cannot play the video. Re-add the title to convert it.",
                )
              }
            />
            {overlay &&
              showing.map((cue, index) => (
                <VideoSubtitles
                  key={cue.id}
                  cue={cue}
                  lineOffset={index}
                  helpMode={activePhraseCues.size ? "phrase" : videoHelp}
                  onActiveChange={setPhraseHelp}
                />
              ))}
            {videoHelp && activePhraseCues.size === 0 && (
              <VideoStudyHelp
                cues={showing}
                notes={cues[Math.max(0, active)]?.notes}
                showTranslation={videoHelp === "translation"}
                showNotes={videoHelp === "notes"}
              />
            )}
          </div>
          <Group
            className="playback-toolbar"
            gap={6}
            px="sm"
            py={6}
            aria-label="Playback controls"
          >
            <Button
              size="xs"
              variant="light"
              onClick={() => (video.current.paused ? play() : pause())}
              aria-label={playing ? "Pause" : "Play"}
            >
              {playing ? "Ⅱ" : "▶"}
            </Button>
            <VideoControls
              videoRef={video}
              containerRef={container}
              time={time}
              onSeek={seek}
              onError={setStatus}
            />
            <Switch
              size="xs"
              label="Auto Pause"
              title="Pause after each subtitle (A)"
              styles={{ label: { fontSize: "var(--mantine-font-size-sm)" } }}
              checked={autoPause}
              onChange={(event) => setAutoPause(event.currentTarget.checked)}
            />
            <ActionIcon
              variant="default"
              size={26}
              title="Fix or comment on this line (E)"
              aria-label="Fix or comment on this line"
              onClick={openQuickEdit}
            >
              <Pencil size={14} />
            </ActionIcon>
            <VideoQuizShare doc={doc} cues={cues} time={time} />
            <PlayerShortcuts viewing />
            {status && (
              <Text size="xs" c="dimmed" className="watch-status" role="status">
                {status}
              </Text>
            )}
          </Group>
        </div>
        {quickEdit && (
          <QuickEdit
            api={api}
            cue={quickEdit}
            portalTarget={container.current}
            onSaved={(saved) => saved && setCues(saved.cues)}
            onClose={() => setQuickEdit(null)}
          />
        )}
      </main>
    </StudyLanguagesProvider>
  );
}
