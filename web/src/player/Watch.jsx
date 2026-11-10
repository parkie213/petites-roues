import { useCallback, useEffect, useRef, useState } from "react";
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Loader,
  Menu,
  Switch,
  Text,
} from "@mantine/core";
import {
  ArrowLeft,
  Ellipsis,
  Volume2,
  Pencil,
  Play,
  Pause,
  Lightbulb,
} from "lucide-react";
import { titlePath } from "../shared/api.js";
import { findMedia, mediaName } from "../shared/media.js";
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
import { SubtitleEditPanel } from "./components/SubtitleEditPanel.jsx";
import { VideoQuizPanel } from "./components/VideoQuizPanel.jsx";
import { VideoControls } from "./components/VideoControls.jsx";
import { VideoStudyHelp } from "./components/VideoStudyHelp.jsx";
import { VideoSubtitles } from "./components/VideoSubtitles.jsx";
import { StudyLanguagesProvider } from "./StudyLanguages.jsx";
import "./Player.css";
import "./Watch.css";

/**
 * A video without a study document, as the player shows it: named from its path, with no lines.
 * Its warnings (such as no playable audio) show in the bar.
 */
function useBareVideo(fingerprint) {
  const [media, setMedia] = useState(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!fingerprint) return;
    findMedia(fingerprint).then(
      (found) => {
        setMedia({
          title: mediaName(found, "Video"),
          cues: [],
          warnings: found?.warnings ?? [],
        });
        window.document.title = `Petites Roues · ${mediaName(found, "Video")}`;
      },
      (failure) => setError(failure.message),
    );
  }, [fingerprint]);
  return { media, error };
}

/**
 * The plain viewing page: the video with interactive subtitles and whole-sentence help, and none
 * of the editing tools. Shares playback position with the editor, which opens at the same place.
 * Given a {@code fingerprint} instead of a title {@code id}, it plays a video that has no study
 * document yet with the same controls, and without what needs a document: subtitles, Quiz me,
 * line fixes, auto-pause and saved position.
 */
export function Watch({ id, fingerprint }) {
  const bare = !id;
  const api = bare ? null : titlePath(id),
    prefix = `sacre-bleu:${id ?? "video:" + fingerprint}`;
  const [status, setStatus] = useState("");
  const study = useStudyDocument({ api, setStatus });
  const bareVideo = useBareVideo(bare ? fingerprint : null);
  const { cues, setCues, returnPath } = study;
  const doc = bare ? bareVideo.media : study.doc;
  const error = bare ? bareVideo.error : study.error;
  const [overlay, setOverlay] = useStoredState(prefix + ":overlay", true);
  const [autoPause, setAutoPause] = useStoredState(
    prefix + ":watch:autoPause",
    false,
  );
  const [videoHelp, setVideoHelp] = useState(null);
  const [quickEdit, setQuickEdit] = useState(null);
  const [previewing, setPreviewing] = useState(false);
  const [quizOpen, setQuizOpen] = useState(false);
  const closeEditor = useRef(null);
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
    mutedForAutoplay,
    unmute,
  } = usePlayerPlayback({
    api,
    doc,
    cues,
    // The editor's previews play past the saved line ends they would otherwise pause at.
    autoPause: autoPause && !previewing,
    speed: 1,
    setSelected: () => {},
    setStatus,
    saveProgress: !bare,
    autoPlay: true,
  });
  const [controlsVisible, setControlsVisible] = useState(false);
  const controlsTimer = useRef(null);
  const wakeControls = useCallback(() => {
    setControlsVisible(true);
    clearTimeout(controlsTimer.current);
    controlsTimer.current = setTimeout(() => setControlsVisible(false), 2500);
  }, []);
  useEffect(() => () => clearTimeout(controlsTimer.current), []);
  function handlePlayerPointer(event) {
    const exploring = Boolean(
      event.target.closest?.(
        ".video-subtitle-line, .video-subtitle-english, .video-subtitle-notes, .video-full-translation, .video-sentence-notes",
      ),
    );
    const bounds = event.currentTarget.getBoundingClientRect();
    const edgeSize = 48;
    const atEdge =
      event.clientY <= bounds.top + edgeSize ||
      event.clientY >= bounds.bottom - edgeSize;
    // Touch has no hover; an intentional tap remains a way to reveal playback controls.
    const touchTap =
      event.pointerType === "touch" && event.type === "pointerdown";
    if (exploring || (!atEdge && !touchTap)) {
      clearTimeout(controlsTimer.current);
      setControlsVisible(false);
    } else wakeControls();
  }
  const active = currentCue(cues, time);
  const showing = activeCues(cues, time);

  function openQuickEdit() {
    if (quickEdit) return;
    setQuizOpen(false);
    const position = video.current?.currentTime ?? time;
    const line = cues.find(
      (line) => line.start <= position && position < line.end,
    );
    pause();
    setQuickEdit({ cue: line || null });
  }
  function openQuiz() {
    if (quickEdit && !closeEditor.current?.()) return;
    pause();
    setQuizOpen(true);
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
        if (!cues.length) seek(time + (event.key === "ArrowLeft" ? -5 : 5));
        else if (event.shiftKey)
          seek(time + (event.key === "ArrowLeft" ? -2 : 2));
        else {
          const target = subtitleSeekTime(
            cues,
            active,
            time,
            event.key === "ArrowLeft" ? -1 : 1,
          );
          if (target != null) seek(target);
        }
      } else if (!cues.length) {
        if (key === "f" && !event.repeat && container.current)
          toggleFullscreen(container.current).catch((e) =>
            setStatus(e.message),
          );
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
      <main className="watch-loading" role="status" aria-live="polite">
        <Loader size={30} color="coral.4" />
        <Text size="sm" c="dimmed">
          Opening video…
        </Text>
      </main>
    );
  const page = (
    <main className="left watch">
      <div
        className={`video-player${quickEdit || quizOpen ? " watch-editing" : ""}`}
        ref={container}
        data-controls-visible={controlsVisible || undefined}
        onPointerMove={handlePlayerPointer}
        onPointerDown={handlePlayerPointer}
      >
        <Group className="watch-bar" gap="sm" wrap="nowrap">
          <ActionIcon
            component="a"
            variant="subtle"
            href={returnPath}
            aria-label="Back to library"
            title="Library"
          >
            <ArrowLeft size={19} />
          </ActionIcon>
          <Text size="sm" truncate style={{ flex: 1 }}>
            {doc.title}
          </Text>
          {(doc.warnings ?? []).map((warning) => (
            <Badge key={warning} size="sm" variant="light" color="yellow">
              {warning}
            </Badge>
          ))}
          {!bare && (
            <Button
              size="xs"
              variant="default"
              leftSection={<Lightbulb size={14} />}
              aria-expanded={quizOpen}
              aria-controls="video-quiz-dock"
              onClick={openQuiz}
            >
              Quiz me
            </Button>
          )}
          {!bare && (
            <Menu position="bottom-end" withinPortal={false}>
              <Menu.Target>
                <ActionIcon
                  variant="subtle"
                  aria-label="More options"
                  title="More options"
                >
                  <Ellipsis size={19} />
                </ActionIcon>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Item
                  leftSection={<Pencil size={14} />}
                  onClick={openQuickEdit}
                >
                  Fix or comment on this line
                </Menu.Item>
                <Menu.Divider />
                <PlayerShortcuts viewing />
              </Menu.Dropdown>
            </Menu>
          )}
        </Group>
        <div className="video-stage">
          <video
            ref={video}
            preload="metadata"
            src={
              bare
                ? `/api/media/${encodeURIComponent(fingerprint)}/video`
                : api + "/media"
            }
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
          {mutedForAutoplay && (
            <Button
              className="watch-unmute"
              size="md"
              color="coral"
              leftSection={<Volume2 size={18} />}
              onClick={unmute}
            >
              Click for sound
            </Button>
          )}
          {(overlay || videoHelp === "translation") &&
            showing.map((cue, index) => (
              <VideoSubtitles
                key={cue.id}
                cue={cue}
                lineOffset={index}
                helpMode={activePhraseCues.size ? "phrase" : videoHelp}
                showTranslation={videoHelp === "translation"}
                hideSource={!overlay}
                onActiveChange={setPhraseHelp}
              />
            ))}
          {videoHelp === "notes" && activePhraseCues.size === 0 && (
            <VideoStudyHelp
              cues={showing}
              notes={cues[Math.max(0, active)]?.notes}
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
            {playing ? <Pause size={17} /> : <Play size={17} />}
          </Button>
          <VideoControls
            videoRef={video}
            containerRef={container}
            time={time}
            onSeek={seek}
            onError={setStatus}
          />
          {!bare && (
            <Switch
              size="xs"
              color="coral"
              label="Auto-pause"
              title="Pause after each subtitle (A)"
              styles={{ label: { fontSize: "var(--mantine-font-size-sm)" } }}
              checked={autoPause}
              onChange={(event) => setAutoPause(event.currentTarget.checked)}
            />
          )}
          {status && (
            <Text size="xs" c="dimmed" className="watch-status" role="status">
              {status}
            </Text>
          )}
        </Group>
        {quickEdit && (
          <SubtitleEditPanel
            closeRef={closeEditor}
            api={api}
            cue={quickEdit.cue}
            cues={cues}
            video={video}
            time={time}
            seek={seek}
            play={play}
            pause={pause}
            onSaved={(saved) => {
              setCues(saved.cues);
              heldCue.current = -1;
              heldTime.current = -1;
            }}
            onClose={() => {
              pause();
              setQuickEdit(null);
            }}
            onPreview={setPreviewing}
          />
        )}
        {quizOpen && (
          <VideoQuizPanel
            doc={doc}
            cues={cues}
            video={video}
            time={time}
            onClose={() => setQuizOpen(false)}
          />
        )}
      </div>
    </main>
  );
  return bare ? (
    page
  ) : (
    <StudyLanguagesProvider document={doc}>{page}</StudyLanguagesProvider>
  );
}
