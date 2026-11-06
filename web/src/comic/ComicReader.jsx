import { useEffect, useRef, useState } from "react";
import {
  ActionIcon,
  Alert,
  Button,
  Group,
  Loader,
  Menu,
  Select,
  Slider,
  Text,
  Title,
  Tooltip,
} from "@mantine/core";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Columns2,
  Ellipsis,
  Headphones,
  PanelRight,
  ScanText,
  SquareDashed,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { request, titlePath } from "../shared/api.js";
import { ComicPageView } from "./ComicPageView.jsx";
import { ComicStudyPanel } from "./ComicStudyPanel.jsx";
import { StudyLanguagesProvider } from "../player/StudyLanguages.jsx";
import { QuickEdit } from "../player/components/QuickEdit.jsx";
import { boxLines, boxOf, pageBoxes } from "./annotations.js";
import { chapterQuizBrief, chapterQuizFileName } from "./quiz.js";
import { QuizShare } from "../player/components/QuizShare.jsx";
import {
  adjacentView,
  chapterAt,
  chapterName,
  pageStep,
  resumePage,
  spreadPages,
} from "./navigation.js";
import { useStoredState } from "../shared/storage.js";
import { ReadAloudKey, ReadAloudProvider } from "./ReadAloud.jsx";
import "./ComicReader.css";

/**
 * Reads an ordered comic manifest one page or spread at a time; only successfully displayed
 * pages update resume state. Reader view preferences persist in browser storage.
 */
export function ComicReader({ id }) {
  const api = titlePath(id) + "/comic";
  const [document, setDocument] = useState(null);
  const [index, setIndex] = useState(0);
  const [error, setError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [selectedId, setSelectedId] = useState(null);
  const [editing, setEditing] = useState(false);
  const [studyOpen, setStudyOpen] = useStoredState("comic:study", false);
  const [debug, setDebug] = useStoredState("comic:debug", false);
  const [spread, setSpread] = useStoredState("comic:spread", false);
  // How a text block split into sentences opens: one sentence at a time, or as a list.
  const [blockMode, setBlockMode] = useStoredState("comic:block-mode", "step");
  const [autoListen, setAutoListen] = useStoredState(
    "comic:auto-listen",
    false,
  );
  const [annotate, setAnnotate] = useState(false);
  const [quickEdit, setQuickEdit] = useState(null);
  const [draftRegion, setDraftRegion] = useState(null);
  const [savingBox, setSavingBox] = useState(false);
  const boxWrite = useRef(false);
  const [annotateError, setAnnotateError] = useState("");
  const [zoom, setZoom] = useState("page");
  const [scrub, setScrub] = useState(null);
  const [scale, setScale] = useState(100);
  const writes = useRef(Promise.resolve());
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    const abort = new AbortController();
    Promise.all([
      request(api, { signal: abort.signal }),
      request(api + "/progress", { signal: abort.signal }),
    ])
      .then(([comic, progress]) => {
        setIndex(resumePage(comic.pages, progress.pageId));
        setDocument(comic);
      })
      .catch((failure) => {
        if (failure.name !== "AbortError") setError(failure.message);
      });
    return () => {
      active.current = false;
      abort.abort();
    };
  }, [api]);

  const count = document?.pages.length || 0;
  const visible = (document ? spreadPages(index, count, spread) : []).map(
    (i) => document.pages[i],
  );
  const page = visible[0];
  const boxes = visible.flatMap((p) => pageBoxes(document, p.id));
  // The selection is a line (a bubble, or one sentence of a text block); its box is for annotating.
  const lines = boxLines(boxes);
  const selected = lines.find((line) => line.id === selectedId) || lines[0];
  const selectedBox = selected && boxOf(boxes, selected.id);
  const rtl = document?.readingDirection === "rtl";
  const locked = editing || savingBox || !!draftRegion;
  const previous = document ? adjacentView(index, count, spread, -1) : null;
  const next = document ? adjacentView(index, count, spread, 1) : null;
  function go(target) {
    if (!document || locked || target === null) return;
    const clamped = Math.max(0, Math.min(count - 1, target));
    if (clamped === index) return;
    setIndex(clamped);
  }
  useEffect(() => {
    function keydown(event) {
      if (
        event.defaultPrevented ||
        event.altKey ||
        event.ctrlKey ||
        event.metaKey ||
        /INPUT|SELECT|TEXTAREA/.test(event.target.tagName) ||
        event.target.isContentEditable
      )
        return;
      const step = pageStep(event.key, document?.readingDirection);
      if (step) {
        event.preventDefault();
        go(step > 0 ? next : previous);
      }
      if (
        event.key.toLowerCase() === "e" &&
        !event.repeat &&
        !locked &&
        !annotate &&
        selected
      ) {
        event.preventDefault();
        setQuickEdit(selected);
      }
      if (event.key === "Home") {
        event.preventDefault();
        go(0);
      }
      if (event.key === "End") {
        event.preventDefault();
        go(document.pages.length - 1);
      }
    }
    if (!document) return;
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [document, index, spread, locked, annotate, selected]);

  function save(pageId) {
    writes.current = writes.current
      .catch(() => {})
      .then(() =>
        request(api + "/progress", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ pageId }),
          keepalive: true,
        }),
      )
      .then(() => {
        if (active.current) setSaveError("");
      })
      .catch(() => {
        if (active.current)
          setSaveError("Your reading position could not be saved.");
      });
  }
  function resize(delta) {
    setScale((value) =>
      Math.max(50, Math.min(300, (zoom === "custom" ? value : 100) + delta)),
    );
    setZoom("custom");
  }

  function annotateRequest(path, options) {
    return request(api + path, {
      headers: { "Content-Type": "application/json" },
      ...options,
    })
      .then((saved) => {
        setDocument(saved);
        setAnnotateError("");
        return saved;
      })
      .catch((failure) => {
        setAnnotateError(failure.message);
        throw failure;
      });
  }

  function addBubble(
    region = draftRegion?.region,
    pageId = draftRegion?.pageId,
  ) {
    if (!region || !pageId || boxWrite.current) return;
    boxWrite.current = true;
    setSavingBox(true);
    setDraftRegion({ region, pageId });
    const previousIds = new Set((document.boxes || []).map((box) => box.id));
    annotateRequest("/boxes", {
      method: "POST",
      body: JSON.stringify({
        pageId,
        region,
      }),
    })
      .then((saved) => {
        const created = (saved.boxes || []).find(
          (box) => !previousIds.has(box.id),
        );
        if (created) setSelectedId(created.lines[0].id);
        setDraftRegion(null);
      })
      .catch(() => {})
      .finally(() => {
        boxWrite.current = false;
        setSavingBox(false);
      });
  }

  function savePlacement(box, regions) {
    annotateRequest(`/boxes/${encodeURIComponent(box.id)}/placement`, {
      method: "PUT",
      body: JSON.stringify({ expectedRegions: box.regions, regions }),
    }).catch(() => {});
  }

  function deleteSelected() {
    if (!selectedBox || !annotate) return;
    if (
      !window.confirm(
        selectedBox.lines.length > 1
          ? `Delete this text block and its ${selectedBox.lines.length} sentences?`
          : selected.source
            ? `Delete the bubble "${selected.source}"?`
            : "Delete this box?",
      )
    )
      return;
    annotateRequest(`/boxes/${encodeURIComponent(selectedBox.id)}`, {
      method: "DELETE",
    })
      .then(() => setSelectedId(null))
      .catch(() => {});
  }

  if (error)
    return (
      <main className="comic-message">
        <Alert color="red" title="Could not open comic">
          {error}
        </Alert>
        <Button component="a" href="/" mt="md">
          Return to library
        </Button>
      </main>
    );
  if (!document)
    return (
      <main className="comic-message">
        <Loader aria-label="Loading comic" />
      </main>
    );
  const keys = rtl ? ["←", "→"] : ["→", "←"];
  const forward = { target: next, label: `Next page (${keys[0]})` };
  const back = { target: previous, label: `Previous page (${keys[1]})` };
  const turns = rtl ? [forward, back] : [back, forward];
  const turnIcon = (side, size) =>
    side === 0 ? <ChevronLeft size={size} /> : <ChevronRight size={size} />;
  const edge = (side) => (
    <button
      className="comic-turn"
      tabIndex={-1}
      aria-label={turns[side].label}
      title={turns[side].label}
      disabled={locked || turns[side].target === null}
      onClick={() => go(turns[side].target)}
    >
      {turnIcon(side, 28)}
    </button>
  );
  const pageName = (i) =>
    `${i + 1}${document.pages[i]?.label ? ` · ${document.pages[i].label}` : ""} / ${count}`;
  const chapters = document.chapters ?? [];
  const chapter = chapterAt(document.pages, chapters, index);
  const chapterStart = (c) =>
    document.pages.findIndex((p) => p.id === chapters[c].pageId);
  // The slider's bubble names the chapter too, so scrubbing shows where a chapter starts.
  const scrubName = (i) => {
    const c = chapterAt(document.pages, chapters, i);
    const number = chapters[c]?.number;
    return c < 0
      ? pageName(i)
      : `${number ? `Ch. ${number}` : chapters[c].title} · ${pageName(i)}`;
  };
  // Right-to-left comics fill the slider from the right; mirroring the value is its own inverse.
  const toSlider = (i) => (rtl ? count - 1 - i : i);
  const toggle = (label, pressed, onClick, icon, disabled) => (
    <Tooltip label={label} withArrow>
      <ActionIcon
        variant={pressed ? "light" : "subtle"}
        size="lg"
        aria-label={label}
        aria-pressed={pressed}
        disabled={disabled}
        onClick={onClick}
      >
        {icon}
      </ActionIcon>
    </Tooltip>
  );
  return (
    <StudyLanguagesProvider document={document}>
      <ReadAloudProvider
        url={titlePath(id) + "/speech"}
        autoListen={autoListen}
      >
        <ReadAloudKey text={locked ? null : selected?.source} />
        <main className="comic-reader">
          <header className="comic-toolbar">
            <div className="comic-toolbar-title">
              <Tooltip label="Library" withArrow>
                <ActionIcon
                  component="a"
                  href="/"
                  variant="subtle"
                  size="lg"
                  aria-label="Back to library"
                >
                  <ArrowLeft size={18} />
                </ActionIcon>
              </Tooltip>
              <div className="comic-title">
                <Title order={1} size="h6" lineClamp={1}>
                  {document.title}
                </Title>
                <Text size="xs" c="dimmed" truncate>
                  {document.edition || document.series}
                </Text>
              </div>
            </div>
            <div className="comic-toolbar-tools">
              {toggle(
                "Two-page spread",
                spread,
                () => setSpread((value) => !value),
                <Columns2 size={18} />,
                locked,
              )}
              {toggle(
                autoListen ? "Stop listening on hover" : "Listen on hover",
                autoListen,
                () => setAutoListen((value) => !value),
                <Headphones size={18} />,
              )}
              {toggle(
                annotate ? "Stop annotating" : "Annotate bubbles",
                annotate,
                () => {
                  setAnnotate((value) => !value);
                  setDraftRegion(null);
                  setAnnotateError("");
                },
                <SquareDashed size={18} />,
                savingBox || !!draftRegion,
              )}
              {toggle(
                studyOpen ? "Hide study panel" : "Show study panel",
                studyOpen,
                () => setStudyOpen((value) => !value),
                <PanelRight size={18} />,
                editing,
              )}
              <span className="comic-toolbar-divider" />
              <Select
                aria-label="Page size"
                size="xs"
                w={118}
                allowDeselect={false}
                value={zoom}
                onChange={setZoom}
                data={[
                  { value: "page", label: "Fit page" },
                  { value: "width", label: "Fit width" },
                  { value: "custom", label: `Zoom ${scale}%` },
                ]}
              />
              {toggle(
                "Zoom out",
                false,
                () => resize(-25),
                <ZoomOut size={18} />,
                zoom === "custom" && scale <= 50,
              )}
              {toggle(
                "Zoom in",
                false,
                () => resize(25),
                <ZoomIn size={18} />,
                zoom === "custom" && scale >= 300,
              )}
              <QuizShare
                doc={document}
                disabled={chapters.length === 0}
                reason="This volume has no chapters listed yet"
                scopes={{
                  label: "Chapter",
                  initial: String(Math.max(chapter, 0)),
                  options: chapters.map((c, i) => ({
                    value: String(i),
                    label: chapterName(c),
                  })),
                }}
                fileName={(i) => chapterQuizFileName(document, Number(i))}
                brief={(rules, type, i) =>
                  chapterQuizBrief(rules, document, Number(i), type)
                }
              />
              <Menu width={230} position="bottom-end" shadow="md">
                <Menu.Target>
                  <Tooltip label="More options" withArrow>
                    <ActionIcon
                      variant="subtle"
                      size="lg"
                      aria-label="More options"
                    >
                      <Ellipsis size={18} />
                    </ActionIcon>
                  </Tooltip>
                </Menu.Target>
                <Menu.Dropdown>
                  <Menu.Item
                    leftSection={<ScanText size={16} />}
                    rightSection={debug ? "✓" : null}
                    onClick={() => setDebug((value) => !value)}
                  >
                    Show bubble outlines
                  </Menu.Item>
                </Menu.Dropdown>
              </Menu>
            </div>
          </header>
          {saveError && (
            <Alert color="yellow" title="Resume position">
              {saveError}{" "}
              <Button
                size="compact-xs"
                variant="subtle"
                onClick={() => save(page.id)}
              >
                Retry saving
              </Button>
            </Alert>
          )}
          {annotateError && (
            <Alert
              color="red"
              title="Annotation"
              onClose={() => setAnnotateError("")}
              withCloseButton
            >
              {annotateError}
            </Alert>
          )}
          {annotate && (
            <Alert color="blue" title="Draw boxes for review">
              <Text size="sm">
                Drag on the page to save a box and queue it for review. Text is
                optional and can be added later. Drag existing boxes to move
                them; use the corners to resize.
              </Text>
              {savingBox && <Text size="sm">Saving box…</Text>}
              {!savingBox && draftRegion && (
                <Group gap="xs" mt="xs">
                  <Button size="xs" onClick={() => addBubble()}>
                    Retry saving box
                  </Button>
                  <Button
                    size="xs"
                    variant="subtle"
                    onClick={() => setDraftRegion(null)}
                  >
                    Discard unsaved box
                  </Button>
                </Group>
              )}
              {selectedBox && (
                <Button
                  size="compact-xs"
                  variant="subtle"
                  color="red"
                  mt="xs"
                  onClick={deleteSelected}
                >
                  {selectedBox.lines.length > 1
                    ? "Delete selected text block"
                    : "Delete selected bubble"}
                </Button>
              )}
            </Alert>
          )}
          <div className="comic-reading-area">
            <div className="comic-view-frame">
              {edge(0)}
              <ComicPageView
                key={visible.map((p) => p.id).join("+")}
                document={document}
                pages={visible}
                api={api}
                zoom={zoom}
                scale={scale}
                boxes={boxes}
                rtl={rtl}
                selectedId={selected?.id}
                onSelect={setSelectedId}
                onLoaded={() => save(page.id)}
                debug={debug}
                cards={!studyOpen}
                blockMode={blockMode}
                onBlockMode={setBlockMode}
                editing={locked}
                annotate={annotate}
                onDraw={addBubble}
                onPlacement={savePlacement}
              />
              {edge(1)}
            </div>
            {studyOpen && (
              <ComicStudyPanel
                key={selected?.id || page.id}
                boxes={boxes}
                lines={lines}
                cue={selected}
                api={api}
                onSelect={setSelectedId}
                onSaved={setDocument}
                onEditing={setEditing}
              />
            )}
          </div>
          <nav className="comic-pagebar" aria-label="Pages">
            <Tooltip label={turns[0].label} withArrow>
              <ActionIcon
                variant="subtle"
                size="lg"
                aria-label={turns[0].label}
                disabled={locked || turns[0].target === null}
                onClick={() => go(turns[0].target)}
              >
                {turnIcon(0, 18)}
              </ActionIcon>
            </Tooltip>
            <Slider
              className="comic-page-slider"
              aria-label="Page position"
              min={0}
              max={Math.max(count - 1, 1)}
              inverted={rtl}
              disabled={locked || count < 2}
              value={toSlider(scrub ?? index)}
              label={(value) => scrubName(toSlider(value))}
              onChange={(value) => setScrub(toSlider(value))}
              onChangeEnd={(value) => {
                setScrub(null);
                go(toSlider(value));
              }}
            />
            <Tooltip label={turns[1].label} withArrow>
              <ActionIcon
                variant="subtle"
                size="lg"
                aria-label={turns[1].label}
                disabled={locked || turns[1].target === null}
                onClick={() => go(turns[1].target)}
              >
                {turnIcon(1, 18)}
              </ActionIcon>
            </Tooltip>
            {chapters.length > 0 && (
              <select
                className="comic-page-select comic-chapter-select"
                aria-label="Chapter"
                title="Go to a chapter"
                disabled={locked}
                value={chapter}
                onChange={(event) =>
                  go(chapterStart(Number(event.target.value)))
                }
              >
                {chapter < 0 && (
                  <option value={-1} disabled>
                    Before the first chapter
                  </option>
                )}
                {chapters.map((c, i) => (
                  <option key={c.pageId} value={i}>
                    {chapterName(c)}
                  </option>
                ))}
              </select>
            )}
            <select
              className="comic-page-select"
              aria-label="Page"
              title={rtl ? "Read right to left" : "Read left to right"}
              disabled={locked}
              value={document.pages.indexOf(page)}
              onChange={(event) => go(Number(event.target.value))}
            >
              {document.pages.map((p, i) => (
                <option key={p.id} value={i}>
                  {pageName(i)}
                </option>
              ))}
            </select>
          </nav>
        </main>
      </ReadAloudProvider>
      {quickEdit && (
        <QuickEdit
          comic
          api={titlePath(id)}
          cue={quickEdit}
          onSaved={(saved) => saved && setDocument(saved)}
          onClose={() => setQuickEdit(null)}
        />
      )}
    </StudyLanguagesProvider>
  );
}
