import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Button,
  Checkbox,
  Group,
  Loader,
  Menu,
  NativeSelect,
  Paper,
  Stack,
  Splitter,
  Text,
} from "@mantine/core";
import { request, titlePath } from "../shared/api.js";
import { activeCues, currentCue, formatTime } from "./model.js";
import { useStoredState } from "../shared/storage.js";
import { useCueEditing } from "./useCueEditing.js";
import { useStudyDocument } from "./useStudyDocument.js";
import {
  usePlayerPlayback,
  playbackSpeeds as speeds,
} from "./usePlayerPlayback.js";
import { Timeline } from "./components/Timeline.jsx";
import { CueEditor } from "./components/CueEditor.jsx";
import { QuickEdit } from "./components/QuickEdit.jsx";
import { SecondSubtrack } from "./components/SecondSubtrack.jsx";
import { PlayerMenu } from "./components/PlayerMenu.jsx";
import { VideoQuizShare } from "./components/QuizShare.jsx";
import { PlayerShortcuts } from "./components/PlayerShortcuts.jsx";
import { VideoControls } from "./components/VideoControls.jsx";
import { VideoStudyHelp } from "./components/VideoStudyHelp.jsx";
import { VideoSubtitles } from "./components/VideoSubtitles.jsx";
import { PhraseAlignment } from "./components/PhraseAlignment.jsx";
import { alignmentState, pendingReviewCount } from "./alignment.js";
import { toggleFullscreen } from "./playback.js";
import { usePlayerShortcuts } from "./usePlayerShortcuts.js";

import { CorrectionSuggestion } from "./components/CorrectionSuggestion.jsx";
import { usePlayerClipboard } from "./usePlayerClipboard.js";
import { Transcript } from "./components/Transcript.jsx";
import "./Player.css";
import { languageName } from "./languages.js";
import { StudyLanguagesProvider } from "./StudyLanguages.jsx";

export function Player({ id }) {
  const api = titlePath(id),
    prefix = `sacre-bleu:${id}`;
  const [status, setStatus] = useState("");
  const [actionsExpanded, setActionsExpanded] = useStoredState(
    prefix + ":subtitleActions",
    false,
  );
  const [showReviewChecks, setShowReviewChecks] = useState(false);
  const [plainAlignment, setPlainAlignment] = useState(null);
  const [studyHeight, setStudyHeight] = useStoredState(
    prefix + ":studyHeight",
    null,
  );
  const studyDrag = useRef(null);
  const studyWorkspace = useRef(null);
  const [notesLayoutApplied, setNotesLayoutApplied] = useStoredState(
    prefix + ":notesLayout:v3",
    false,
  );

  const [secondSubtrack, setSecondSubtrack] = useState(null);
  const [mergeSelection, setMergeSelection] = useState(new Set());
  const editing = actionsExpanded;
  const [selectionMode, setSelectionMode] = useState(false);
  const selectionAnchor = useRef(null);
  const [selected, setSelected] = useState(-1);
  const [span, setSpan] = useState(12);
  const [query, setQuery] = useState(""),
    [notesOnly, setNotesOnly] = useState(false),
    [checksOnly, setChecksOnly] = useState(false);
  const { doc, cues, setCues, changes, setChanges, error, returnPath } =
    useStudyDocument({ id, api, setStatus });
  const [videoHelp, setVideoHelp] = useState(null);
  const [quickEdit, setQuickEdit] = useState(null);
  const [definition, setDefinition] = useState(null);
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
  const [overlay, setOverlay] = useStoredState(prefix + ":overlay", true);
  const [autoPause, setAutoPause] = useStoredState(prefix + ":autoPause", true);
  const [follow, setFollow] = useStoredState(prefix + ":follow", true);
  const [speed, setSpeed] = useStoredState(prefix + ":speed", 1);
  const [width, setWidth] = useStoredState(prefix + ":width", null);
  const videoContainer = useRef(null);
  const videoClickTimer = useRef(null);
  useEffect(() => () => clearTimeout(videoClickTimer.current), []);
  const rows = useRef(new Map()),
    search = useRef(null),
    manualScroll = useRef(0);
  const {
    video,
    time,
    setTime,
    playing,
    setPlaying,
    heldCue,
    heldTime,
    replayEnd,
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
    speed,
    setSelected,
    setStatus,
  });
  const active = currentCue(cues, time),
    displayIndex = Math.max(0, active),
    cue = cues[displayIndex];
  const definitionCue = activeCues(cues, time).find(
    (item) => alignmentState(item) === "ready",
  );
  useEffect(() => {
    setDefinition(null);
  }, [definitionCue?.id]);
  const {
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
  } = useCueEditing({
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
  });
  useLayoutEffect(() => {
    const workspace = studyWorkspace.current;
    if (
      notesLayoutApplied ||
      !workspace ||
      editor ||
      !video.current ||
      video.current.readyState < 1
    )
      return;
    const canvas = workspace.querySelector(".alignment-canvas");
    if (!canvas) return;
    // Reclaim the retired phrase-notes row once; subsequent divider adjustments stay saved.
    setStudyHeight(
      Math.max(160, workspace.getBoundingClientRect().height - 110),
    );
    setNotesLayoutApplied(true);
  }, [
    doc,
    cue?.id,
    time,
    editor,
    notesLayoutApplied,
    setNotesLayoutApplied,
    setStudyHeight,
  ]);
  const previousCue = cues[displayIndex - 1];
  const canTrimPrevious =
    cue &&
    previousCue &&
    previousCue.end > cue.start &&
    cue.start > previousCue.start;
  const reviewCount = useMemo(() => pendingReviewCount(changes), [changes]);

  useEffect(() => {
    if (follow && active >= 0 && performance.now() > manualScroll.current)
      rows.current.get(cues[active]?.id)?.scrollIntoView({ block: "center" });
  }, [active, follow, cues]);
  const zoom = useCallback(
    (value) => setSpan(Math.min(120, Math.max(1, value))),
    [],
  );
  const { copy, copyReviewPrompt } = usePlayerClipboard({
    api,
    id,
    doc,
    cue,
    displayIndex,
    dirty,
    saveNow,
    setStatus,
  });
  function toggleCueTiming() {
    if (suggestion) return;
    setActionsExpanded(!editing);
    if (!editing) {
      pause();
      setSelected(displayIndex);
    }
  }
  function openQuickEdit() {
    if (!cue || editor || suggestion) return;
    pause();
    setQuickEdit(cue);
  }
  usePlayerShortcuts({
    toggleCueTiming,
    openQuickEdit,
    definition,
    setDefinition,
    definitionCue,
    cues,
    editor,
    video,
    videoContainer,
    setStatus,
    play,
    pause,
    seek,
    replay,
    displayIndex,
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
  });

  if (error)
    return (
      <main className="page">
        <a href="/">← Library</a>
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
        <Text>Loading player…</Text>
      </Group>
    );
  return (
    <StudyLanguagesProvider document={doc}>
      <div className="player-shell">
        <Group
          component="nav"
          className="library-nav"
          aria-label="Player"
          gap="md"
          px="md"
          py="xs"
          wrap="nowrap"
        >
          <Button component="a" size="xs" variant="default" href={returnPath}>
            <span aria-hidden="true">←</span> Library
          </Button>
          <Button
            component="a"
            size="xs"
            variant="default"
            href={`/watch/${encodeURIComponent(id)}/`}
          >
            Play
          </Button>
          <Stack gap={2} miw={0} style={{ flex: 1 }}>
            <Text fw={600} size="sm" truncate>
              {doc.title}
            </Text>
            <Text size="xs" c="dimmed">
              {[
                `${languageName(doc.sourceLanguage)} → ${languageName(doc.translationLanguage)}`,
                doc.series,
                doc.season != null ? "Season " + doc.season : null,
                doc.episode != null ? "Episode " + doc.episode : null,
              ]
                .filter(Boolean)
                .join(" · ")}
            </Text>
          </Stack>
          <VideoQuizShare doc={doc} cues={cues} time={time} />
          <PlayerMenu label="More options">
            <Stack gap={4}>
              <strong>Title details</strong>
              <p>{doc.title}</p>
              {doc.sourceUrl && (
                <a href={doc.sourceUrl} target="_blank" rel="noreferrer">
                  View original source ↗
                </a>
              )}
            </Stack>
            <Checkbox
              label="Show review checks"
              size="xs"
              checked={showReviewChecks}
              onChange={(event) =>
                setShowReviewChecks(event.currentTarget.checked)
              }
            />
            <Stack gap="xs" className="keep-menu-open">
              <SecondSubtrack
                onChange={setSecondSubtrack}
                titleId={id}
                onError={setStatus}
              />
            </Stack>
            {reviewCount > 0 && (
              <Button size="xs" variant="default" onClick={copyReviewPrompt}>
                Copy notes review prompt ({reviewCount})
              </Button>
            )}
          </PlayerMenu>
        </Group>
        <Splitter
          className="player-layout"
          classNames={{ pane: "player-pane" }}
          attributes={{ handle: { "aria-label": "Resize player panels" } }}
          lineSize={7}
          sizes={width ? [`${width}px`, 100] : [68, 32]}
          onSizeChange={([left]) =>
            setWidth(
              typeof left === "number"
                ? (left / 100) * (innerWidth - 7)
                : parseFloat(left),
            )
          }
        >
          <Splitter.Pane defaultSize={68} min="320px">
            <section className="left">
              <div className="video-player" ref={videoContainer}>
                <div className="video-stage">
                  <video
                    ref={video}
                    onClick={(event) => {
                      // Wait briefly so a double click changes size without toggling playback.
                      if (event.detail > 1) return;
                      clearTimeout(videoClickTimer.current);
                      videoClickTimer.current = setTimeout(() => {
                        if (video.current)
                          video.current.paused ? play() : pause();
                      }, 300);
                    }}
                    onDoubleClick={() => {
                      clearTimeout(videoClickTimer.current);
                      toggleFullscreen(videoContainer.current).catch((error) =>
                        setStatus(error.message),
                      );
                    }}
                    preload="metadata"
                    src={api + "/media"}
                    onPlay={() => {
                      setDefinition(null);
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
                    onError={async () => {
                      let message =
                        "This browser cannot play the video. Place one compatible video in the title folder, then reopen this title.";
                      try {
                        const entries = await request("/api/library/titles");
                        message =
                          entries.find((entry) => entry.id === id)?.unavailable
                            ?.title || message;
                      } catch {
                        /* Keep the playback error if the catalogue is unavailable. */
                      }
                      setStatus(message);
                    }}
                  />
                  {overlay &&
                    activeCues(cues, time).map((activeCue, index) => (
                      <VideoSubtitles
                        key={activeCue.id}
                        cue={activeCue}
                        definitionIndex={
                          definition?.cueId === activeCue.id
                            ? definition.index
                            : null
                        }
                        lineOffset={index}
                        helpMode={activePhraseCues.size ? "phrase" : videoHelp}
                        onActiveChange={setPhraseHelp}
                      />
                    ))}
                  {videoHelp && activePhraseCues.size === 0 && (
                    <VideoStudyHelp
                      cues={activeCues(cues, time)}
                      notes={cue?.notes}
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
                  <Button
                    size="xs"
                    variant="default"
                    onClick={() => seek(time - 10)}
                    aria-label="Back 10 seconds"
                  >
                    −10s
                  </Button>
                  <Button
                    size="xs"
                    variant="default"
                    onClick={() => seek(time + 10)}
                    aria-label="Forward 10 seconds"
                  >
                    +10s
                  </Button>
                  <Button
                    size="xs"
                    variant="default"
                    onClick={() => replay(displayIndex)}
                    aria-label="Replay subtitle"
                    title="Replay subtitle"
                  >
                    ↻
                  </Button>
                  <VideoControls
                    videoRef={video}
                    containerRef={videoContainer}
                    time={time}
                    onSeek={seek}
                    onError={setStatus}
                  />

                  <NativeSelect
                    size="xs"
                    aria-label="Playback speed"
                    value={speed}
                    onChange={(event) => setSpeed(Number(event.target.value))}
                    data={speeds.map((rate) => ({
                      value: String(rate),
                      label: `${rate}×`,
                    }))}
                    w={82}
                  />
                  <PlayerMenu label="Options">
                    <Checkbox
                      label="Video subtitles (S)"
                      checked={overlay}
                      onChange={(event) =>
                        setOverlay(event.currentTarget.checked)
                      }
                      size="xs"
                    />
                    <Checkbox
                      label="Follow transcript"
                      checked={follow}
                      onChange={(event) =>
                        setFollow(event.currentTarget.checked)
                      }
                      size="xs"
                    />
                    <Checkbox
                      label="Pause after each subtitle (A)"
                      checked={autoPause}
                      onChange={(event) =>
                        setAutoPause(event.currentTarget.checked)
                      }
                      size="xs"
                    />
                  </PlayerMenu>
                  <PlayerShortcuts editing={editing} />
                </Group>
              </div>
              <div
                className="study-divider"
                role="separator"
                aria-label="Resize video and study area"
                aria-orientation="horizontal"
                tabIndex={0}
                onPointerDown={(e) => {
                  const height =
                    e.currentTarget.nextElementSibling.getBoundingClientRect()
                      .height;
                  const videoHeight =
                    video.current.getBoundingClientRect().height;
                  studyDrag.current = {
                    y: e.clientY,
                    height,
                    max: Math.max(160, height + videoHeight - 100),
                  };
                  e.currentTarget.setPointerCapture(e.pointerId);
                  e.preventDefault();
                }}
                onPointerMove={(e) => {
                  const drag = studyDrag.current;
                  if (drag)
                    setStudyHeight(
                      Math.min(
                        drag.max,
                        Math.max(160, drag.height + drag.y - e.clientY),
                      ),
                    );
                }}
                onPointerUp={() => {
                  studyDrag.current = null;
                }}
                onLostPointerCapture={() => {
                  studyDrag.current = null;
                }}
                onDoubleClick={() => setStudyHeight(null)}
                onKeyDown={(e) => {
                  if (e.key === "Home") {
                    e.preventDefault();
                    setStudyHeight(null);
                  }
                  if (e.key !== "ArrowUp" && e.key !== "ArrowDown") return;
                  e.preventDefault();
                  const height =
                    e.currentTarget.nextElementSibling.getBoundingClientRect()
                      .height;
                  const max = Math.max(
                    160,
                    height + video.current.getBoundingClientRect().height - 100,
                  );
                  setStudyHeight(
                    Math.min(
                      max,
                      Math.max(160, height + (e.key === "ArrowUp" ? 20 : -20)),
                    ),
                  );
                }}
              />
              <Paper
                component="section"
                radius={0}
                p="sm"
                ref={studyWorkspace}
                className={`study-workspace${suggestion ? " is-correcting" : !editor ? " compact-translation" : ""}`}
                style={
                  suggestion?.height || studyHeight
                    ? {
                        "--study-height":
                          (suggestion?.height || studyHeight) + "px",
                        boxSizing: "border-box",
                      }
                    : undefined
                }
                aria-label={editor ? "Subtitle editor" : "Translation help"}
              >
                <header className="workspace-header">
                  {editor && (
                    <h2 className="workspace-heading">
                      {editor.mode === "add"
                        ? "Add subtitle"
                        : editor.mode === "split"
                          ? "Split subtitle"
                          : "Edit subtitle"}
                    </h2>
                  )}
                  <Group className="workspace-actions" gap={4} ml="auto">
                    {!editor && alignmentState(cue) === "ready" && (
                      <Button
                        size="xs"
                        variant="default"
                        className="alignment-view-toggle"
                        onClick={() =>
                          setPlainAlignment(
                            plainAlignment === cue.id ? null : cue.id,
                          )
                        }
                      >
                        {plainAlignment === cue.id
                          ? "Show phrase links"
                          : "Plain text"}
                      </Button>
                    )}

                    {!editor &&
                      (((showReviewChecks || checksOnly) &&
                        cue?.listeningCheck) ||
                        cue?.textCheck) && (
                        <span>
                          <PlayerMenu
                            color="yellow"
                            label={
                              (showReviewChecks || checksOnly) &&
                              cue.listeningCheck
                                ? "Listening check"
                                : "Text check"
                            }
                          >
                            {(showReviewChecks || checksOnly) &&
                              cue.listeningCheck && (
                                <p>
                                  <strong>Listening check</strong>
                                  <br />
                                  {cue.listeningCheck}
                                </p>
                              )}
                            {cue.textCheck && (
                              <p>
                                <strong>Text check</strong>
                                <br />
                                {cue.textCheck}
                              </p>
                            )}
                          </PlayerMenu>
                        </span>
                      )}
                    {!editor && (
                      <>
                        <Button
                          size="xs"
                          variant="subtle"
                          onClick={() => copy()}
                        >
                          Copy line
                        </Button>
                        <Button
                          size="xs"
                          variant="subtle"
                          disabled={
                            !cue || Boolean(suggestion) || actionBusy || merging
                          }
                          onClick={openSuggestion}
                        >
                          Suggest correction
                        </Button>
                      </>
                    )}
                    <Menu width={240} position="bottom-end" shadow="md">
                      <Menu.Target>
                        <Button
                          size="xs"
                          variant={editing ? "light" : "default"}
                          disabled={Boolean(suggestion)}
                        >
                          Edit ▾
                        </Button>
                      </Menu.Target>
                      <Menu.Dropdown>
                        <Menu.Item
                          role="menuitemcheckbox"
                          aria-checked={editing}
                          rightSection={editing ? "✓" : null}
                          onClick={toggleCueTiming}
                        >
                          Adjust cue timings (C)
                        </Menu.Item>
                        <Menu.Divider />
                        {canTrimPrevious && (
                          <Menu.Item
                            disabled={Boolean(editor) || actionBusy || merging}
                            onClick={() => {
                              pause();
                              editVersion.current++;
                              setCues((current) =>
                                current.map((line) =>
                                  line.id === previousCue.id
                                    ? { ...line, end: cue.start }
                                    : line,
                                ),
                              );
                              setDirty(true);
                              setStatus(
                                "Previous subtitle trimmed to this start.",
                              );
                            }}
                          >
                            Trim previous to this start
                          </Menu.Item>
                        )}
                        <Menu.Item
                          disabled={
                            !cue || Boolean(editor) || actionBusy || merging
                          }
                          onClick={openQuickEdit}
                        >
                          Fix or comment (E)
                        </Menu.Item>
                        <Menu.Item
                          disabled={
                            !cue || Boolean(editor) || actionBusy || merging
                          }
                          onClick={() => openEditor("edit")}
                        >
                          Edit subtitle
                        </Menu.Item>
                        <Menu.Item
                          disabled={
                            !cue || Boolean(editor) || actionBusy || merging
                          }
                          onClick={() => openEditor("split")}
                        >
                          Split subtitle
                        </Menu.Item>
                        <Menu.Item
                          disabled={Boolean(editor) || actionBusy || merging}
                          onClick={() => openEditor("add")}
                        >
                          Add subtitle
                        </Menu.Item>
                        <Menu.Divider />
                        <Menu.Item
                          color="red"
                          disabled={
                            !cue || Boolean(editor) || actionBusy || merging
                          }
                          onClick={deleteCurrentCue}
                        >
                          {actionBusy ? "Deleting…" : "Delete subtitle"}
                        </Menu.Item>
                      </Menu.Dropdown>
                    </Menu>
                  </Group>
                </header>
                {suggestion ? (
                  <CorrectionSuggestion
                    suggestion={suggestion}
                    setSuggestion={setSuggestion}
                    busy={suggestionBusy}
                    onSubmit={submitSuggestion}
                  />
                ) : editor ? (
                  <CueEditor
                    {...editor}
                    onSave={saveCue}
                    onClose={() => setEditor(null)}
                  />
                ) : (
                  <PhraseAlignment
                    key={cue?.id}
                    cue={cue}
                    plain={plainAlignment === cue?.id}
                  />
                )}
              </Paper>
              <Group className="timeline-toolbar" gap="sm" px="sm" py={5}>
                <span>Timeline</span>
                <Group gap="xs" ml="auto">
                  <Button
                    size="xs"
                    variant="default"
                    aria-label="Zoom out timeline"
                    onClick={() => zoom(span * 1.4)}
                  >
                    −
                  </Button>
                  <span>±{Math.round(span)}s</span>
                  <Button
                    size="xs"
                    variant="default"
                    aria-label="Zoom in timeline"
                    onClick={() => zoom(span / 1.4)}
                  >
                    +
                  </Button>
                </Group>
                {(editing || dirty) && (
                  <>
                    {editing && (
                      <span className="timing-mode-label">Edit mode</span>
                    )}
                    <Button
                      size="xs"
                      variant="default"
                      className="save"
                      disabled={!dirty}
                      onClick={() =>
                        saveNow().catch((e) =>
                          setStatus("Save failed: " + e.message),
                        )
                      }
                    >
                      {dirty ? "Save timings" : "Saved"}
                    </Button>
                  </>
                )}
              </Group>
              <Timeline
                secondSubtrack={secondSubtrack}
                cues={cues}
                videoRef={video}
                heldTimeRef={heldTime}
                selected={selected}
                span={span}
                onZoom={zoom}
                onSeek={seek}
                onSelect={setSelected}
                onEdit={edit}
                editing={editing}
                onEditStart={() => {
                  pause();
                  clearTimeout(timingSaveTimer.current);
                  setTimingDragging(true);
                }}
                onEditEnd={() => setTimingDragging(false)}
              />
              <Group className="wave-footer" gap="sm" px="sm" py={4}>
                {editing && (
                  <>
                    <span className="start-label">start</span>
                    <span className="end-label">end</span>
                    <span>drag edges · [ ] = set to playhead</span>
                  </>
                )}
                <span className="save-status" role="status">
                  {status}
                </span>
                <time>{formatTime(time)}</time>
              </Group>
              <Text
                className="keyboard-hint"
                size="10px"
                c="dimmed"
                px="sm"
                py={4}
                m={0}
              >
                Space play · ←→ subtitles · R replay · T translation · W notes ·
                E fix/comment · F fullscreen · S subtitles · A autopause · C cue
                timings · / search
                {editing ? " · [ ] set timing" : ""}
              </Text>
            </section>
          </Splitter.Pane>
          <Splitter.Pane defaultSize={32} min="280px">
            <Transcript
              cues={cues}
              showReviewChecks={showReviewChecks || checksOnly}
              active={active}
              query={query}
              setQuery={setQuery}
              notesOnly={notesOnly}
              setNotesOnly={setNotesOnly}
              checksOnly={checksOnly}
              setChecksOnly={setChecksOnly}
              rows={rows}
              search={search}
              manualScroll={manualScroll}
              selectionMode={selectionMode}
              setSelectionMode={setSelectionMode}
              mergeSelection={mergeSelection}
              setMergeSelection={setMergeSelection}
              selectionAnchor={selectionAnchor}
              merging={merging}
              actionBusy={actionBusy}
              editor={editor}
              dirty={dirty}
              mergeSelected={mergeSelected}
              setSelected={setSelected}
              pause={pause}
              seek={seek}
            />
          </Splitter.Pane>
        </Splitter>
      </div>
      {quickEdit && (
        <QuickEdit
          api={api}
          cue={quickEdit}
          portalTarget={videoContainer.current}
          onSaved={(saved) => {
            if (saved) setCues(saved.cues);
            request(api + "/review-queue")
              .then(setChanges)
              .catch(() => {});
          }}
          onClose={() => setQuickEdit(null)}
        />
      )}
    </StudyLanguagesProvider>
  );
}
