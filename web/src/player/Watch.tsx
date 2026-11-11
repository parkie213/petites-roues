import { useEffect, useRef, useState } from "react";
import { Button, Loader, Text } from "@mantine/core";
import { Volume2 } from "lucide-react";
import { titlePath } from "../shared/api.ts";
import { useStoredState } from "../shared/storage.ts";
import type { CueView, VideoView } from "../shared/types.ts";
import { activeCues, currentCue } from "./model.ts";
import { subtitleSeekTime, toggleFullscreen } from "./playback.js";
import { useAutoHideControls } from "./useAutoHideControls.ts";
import { useBareVideo } from "./useBareVideo.ts";
import type { BareVideo } from "./useBareVideo.ts";
import { usePhraseHelp } from "./usePhraseHelp.ts";
import { usePlayerPlayback } from "./usePlayerPlayback.ts";
import { useStudyDocument } from "./useStudyDocument.ts";
import { useWatchShortcuts } from "./watchKeys.ts";
import type { VideoHelp, WatchAction } from "./watchKeys.ts";
import { PlaybackToolbar } from "./components/PlaybackToolbar.tsx";
import { SubtitleEditPanel } from "./components/SubtitleEditPanel.tsx";
import { VideoQuizPanel } from "./components/VideoQuizPanel.jsx";
import { VideoStudyHelp } from "./components/VideoStudyHelp.jsx";
import { VideoSubtitles } from "./components/VideoSubtitles.jsx";
import { WatchBar } from "./components/WatchBar.tsx";
import { StudyLanguagesProvider } from "./StudyLanguages.jsx";
import "./Player.css";
import "./Watch.css";

/**
 * The plain viewing page: the video with interactive subtitles and whole-sentence help, and none
 * of the editing tools. Shares playback position with the editor, which opens at the same place.
 * Given a `fingerprint` instead of a title `id`, it plays a video that has no study document yet
 * with the same controls, and without what needs a document: subtitles, Quiz me, line fixes,
 * auto-pause and saved position.
 */
export function Watch({
  id,
  fingerprint,
}: {
  id?: string;
  fingerprint?: string;
}) {
  const bare = !id;
  const api = bare ? null : titlePath(id),
    prefix = `sacre-bleu:${id ?? "video:" + fingerprint}`;
  const [status, setStatus] = useState("");
  const study = useStudyDocument({ api, setStatus });
  const bareVideo = useBareVideo(bare ? (fingerprint ?? null) : null);
  const cues = study.cues;
  const doc: VideoView | BareVideo | null = bare ? bareVideo.media : study.doc;
  // Only media without a document carries warnings, such as no playable audio.
  const warnings = (bare && bareVideo.media?.warnings) || [];
  const error = bare ? bareVideo.error : study.error;
  const [overlay, setOverlay] = useStoredState(prefix + ":overlay", true);
  const [autoPause, setAutoPause] = useStoredState(
    prefix + ":watch:autoPause",
    false,
  );
  const [videoHelp, setVideoHelp] = useState<VideoHelp | null>(null);
  const [quickEdit, setQuickEdit] = useState<{ cue: CueView | null } | null>(
    null,
  );
  const [previewing, setPreviewing] = useState(false);
  const [quizOpen, setQuizOpen] = useState(false);
  const closeEditor = useRef<(() => boolean) | null>(null);
  const { phraseHelpOpen, setPhraseHelp } = usePhraseHelp();
  const container = useRef<HTMLDivElement>(null);
  const clickTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(clickTimer.current), []);
  const playback = usePlayerPlayback({
    api,
    doc,
    cues,
    // The editor's previews play past the saved line ends they would otherwise pause at.
    autoPause: autoPause && !previewing,
    setStatus,
    saveProgress: !bare,
    autoPlay: true,
  });
  const { video, time, seek, play, pause, toggle } = playback;
  const controls = useAutoHideControls();
  const active = currentCue(cues, time);
  const showing = activeCues(cues, time);

  function fullscreen() {
    if (container.current)
      toggleFullscreen(container.current).catch((e: Error) =>
        setStatus(e.message),
      );
  }
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
  useWatchShortcuts(cues.length > 0, (action: WatchAction) => {
    switch (action.type) {
      case "help":
        setVideoHelp((current) =>
          current === action.help ? null : action.help,
        );
        pause();
        break;
      case "toggle-play":
        toggle();
        break;
      case "seek-by":
        seek(time + action.seconds);
        break;
      case "seek-line": {
        const target = subtitleSeekTime(cues, active, time, action.direction);
        if (target != null) seek(target);
        break;
      }
      case "replay":
        playback.replay(Math.max(0, active));
        break;
      case "edit":
        openQuickEdit();
        break;
      case "toggle-subtitles":
        setOverlay((value) => !value);
        break;
      case "toggle-auto-pause":
        setAutoPause((value) => !value);
        break;
      case "fullscreen":
        fullscreen();
        break;
    }
  });

  if (error)
    return (
      <main className="page">
        <a href={study.returnPath}>← Library</a>
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
        className={`video-player${quickEdit || quizOpen ? " watch-editing" : ""}${quickEdit ? " watch-timeline" : ""}`}
        ref={container}
        data-controls-visible={controls.visible || undefined}
        onPointerMove={controls.onPointer}
        onPointerDown={controls.onPointer}
      >
        <WatchBar
          title={doc.title ?? ""}
          warnings={warnings}
          returnPath={study.returnPath}
          study={
            bare
              ? null
              : { quizOpen, onQuiz: openQuiz, onEditLine: openQuickEdit }
          }
        />
        <div className="video-stage">
          <video
            ref={video}
            preload="metadata"
            src={
              bare
                ? `/api/media/${encodeURIComponent(fingerprint ?? "")}/video`
                : api + "/media"
            }
            onClick={(event) => {
              // Wait briefly so a double click goes fullscreen without toggling playback.
              if (event.detail > 1) return;
              clearTimeout(clickTimer.current);
              clickTimer.current = setTimeout(toggle, 300);
            }}
            onDoubleClick={() => {
              clearTimeout(clickTimer.current);
              fullscreen();
            }}
            onPlay={() => {
              setVideoHelp(null);
              playback.videoEvents.onPlay();
            }}
            onPause={playback.videoEvents.onPause}
            onSeeking={playback.videoEvents.onSeeking}
            onError={() =>
              setStatus(
                "This browser cannot play the video. Re-add the title to convert it.",
              )
            }
          />
          {playback.mutedForAutoplay && (
            <Button
              className="watch-unmute"
              size="md"
              color="coral"
              leftSection={<Volume2 size={18} />}
              onClick={playback.unmute}
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
                helpMode={phraseHelpOpen ? "phrase" : (videoHelp ?? "")}
                showTranslation={videoHelp === "translation"}
                hideSource={!overlay}
                onActiveChange={setPhraseHelp}
              />
            ))}
          {videoHelp === "notes" && !phraseHelpOpen && (
            <VideoStudyHelp
              cues={showing}
              notes={cues[Math.max(0, active)]?.notes}
              showNotes
            />
          )}
        </div>
        <PlaybackToolbar
          videoRef={video}
          containerRef={container}
          time={time}
          playing={playback.playing}
          onToggle={toggle}
          onSeek={seek}
          autoPause={
            bare ? null : { checked: autoPause, onChange: setAutoPause }
          }
          status={status}
          onError={setStatus}
        />
        {quickEdit && api && (
          <SubtitleEditPanel
            closeRef={closeEditor}
            api={api}
            cue={quickEdit.cue}
            cues={cues}
            video={video}
            heldTime={playback.heldTime}
            seek={seek}
            play={play}
            pause={pause}
            onSaved={(saved: { cues: CueView[] }) => {
              study.setCues(saved.cues);
              playback.release();
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
