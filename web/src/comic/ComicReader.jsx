import { useEffect, useRef, useState } from "react";
import {
  ActionIcon,
  Alert,
  Button,
  Group,
  Loader,
  Menu,
  Text,
  Tooltip,
} from "@mantine/core";
import {
  Ellipsis,
  Headphones,
  PanelRight,
  ScanText,
  SquareDashed,
} from "lucide-react";
import { request, titlePath } from "../shared/api.js";
import { ComicPageView } from "./ComicPageView.jsx";
import { ComicStudyPanel } from "./ComicStudyPanel.jsx";
import { StudyLanguagesProvider } from "../player/StudyLanguages.jsx";
import { QuickEdit } from "../player/components/QuickEdit.jsx";
import { boxLines, boxOf, pageBoxes } from "./annotations.js";
import { chapterQuizBrief, chapterQuizFileName } from "./quiz.js";
import { QuizShare } from "../player/components/QuizShare.jsx";
import { chapterAt, chapterName, resumePage } from "./navigation.js";
import { useStoredState } from "../shared/storage.js";
import { ReadAloudKey, ReadAloudProvider } from "./ReadAloud.jsx";
import {
  ComicStage,
  ComicToolbar,
  ToolbarToggle,
  ViewTools,
  useComicView,
} from "./ComicFrame.jsx";
import { libraryReturnPath } from "../shared/library-return.js";

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
  const rtl = document?.readingDirection === "rtl";
  const locked = editing || savingBox || !!draftRegion;
  // `onKey` runs at key time, after `selected` below is set for this render.
  const view = useComicView({
    count,
    index,
    onIndex: setIndex,
    rtl,
    locked,
    onKey(event) {
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
    },
  });
  const visible = view.visible.map((i) => document.pages[i]);
  const page = visible[0];
  const boxes = visible.flatMap((p) => pageBoxes(document, p.id));
  // The selection is a line (a bubble, or one sentence of a text block); its box is for annotating.
  const lines = boxLines(boxes);
  const selected = lines.find((line) => line.id === selectedId) || lines[0];
  const selectedBox = selected && boxOf(boxes, selected.id);

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
        <Button component="a" href={libraryReturnPath()} mt="md">
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
  const chapters = document.chapters ?? [];
  const chapter = chapterAt(document.pages, chapters, index);
  const chapterStart = (c) =>
    document.pages.findIndex((p) => p.id === chapters[c].pageId);
  return (
    <StudyLanguagesProvider document={document}>
      <ReadAloudProvider
        url={titlePath(id) + "/speech"}
        autoListen={autoListen}
      >
        <ReadAloudKey text={locked ? null : selected?.source} />
        <main className="comic-reader">
          <ComicToolbar
            title={document.title}
            middle={
              chapters.length > 0 && (
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
              )
            }
          >
            <ToolbarToggle
              label={autoListen ? "Stop listening on hover" : "Listen on hover"}
              pressed={autoListen}
              onClick={() => setAutoListen((value) => !value)}
              icon={<Headphones size={18} />}
            />
            <ViewTools view={view} />
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
                  leftSection={<PanelRight size={16} />}
                  rightSection={studyOpen ? "✓" : null}
                  disabled={editing}
                  onClick={() => setStudyOpen((value) => !value)}
                >
                  Study panel
                </Menu.Item>
                <Menu.Item
                  leftSection={<SquareDashed size={16} />}
                  rightSection={annotate ? "✓" : null}
                  disabled={savingBox || !!draftRegion}
                  onClick={() => {
                    setAnnotate((value) => !value);
                    setDraftRegion(null);
                    setAnnotateError("");
                  }}
                >
                  Annotate bubbles
                </Menu.Item>

                <Menu.Item
                  leftSection={<ScanText size={16} />}
                  rightSection={debug ? "✓" : null}
                  onClick={() => setDebug((value) => !value)}
                >
                  Show bubble outlines
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </ComicToolbar>
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
            <ComicStage view={view}>
              <ComicPageView
                key={visible.map((p) => p.id).join("+")}
                document={document}
                pages={visible}
                api={api}
                zoom={view.zoom}
                scale={view.scale}
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
            </ComicStage>
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
