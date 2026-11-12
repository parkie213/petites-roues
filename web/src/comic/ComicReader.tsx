import { useState } from "react";
import { Alert, Button, Loader } from "@mantine/core";
import { titlePath } from "../shared/api.ts";
import type {
  ComicBoxView,
  ComicLineView,
  ComicView,
} from "../shared/types.ts";
import { ComicPageView } from "./ComicPageView.tsx";
import { ComicStudyPanel } from "./ComicStudyPanel.jsx";
import { StudyLanguagesProvider } from "../player/StudyLanguages.jsx";
import { QuickEdit } from "../player/components/QuickEdit.jsx";
import { boxLines, boxOf, pageBoxes } from "./annotations.js";
import { useStoredState } from "../shared/storage.ts";
import { ReadAloudKey, ReadAloudProvider } from "./ReadAloud.jsx";
import { ComicStage, useComicView } from "./ComicFrame.jsx";
import { libraryReturnPath } from "../shared/library-return.ts";
import { useComicDocument } from "./useComicDocument.ts";
import { useComicProgress } from "./useComicProgress.ts";
import { useComicAnnotations } from "./useComicAnnotations.ts";
import { ComicReaderToolbar } from "./ComicReaderToolbar.tsx";
import { ComicReaderNotices } from "./ComicReaderNotices.tsx";

/**
 * Reads an ordered comic manifest one page or spread at a time; only successfully displayed
 * pages update resume state. Reader view preferences persist in browser storage.
 */
export function ComicReader({ id }: { id: string }) {
  const api = titlePath(id) + "/comic";
  const { document, setDocument, index, setIndex, error } =
    useComicDocument(api);
  const { save, saveError } = useComicProgress(api);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [studyOpen, setStudyOpen] = useStoredState("comic:study", false);
  const [debug, setDebug] = useStoredState("comic:debug", false);
  // How a text block split into sentences opens: one sentence at a time, or as a list.
  const [blockMode, setBlockMode] = useStoredState("comic:block-mode", "step");
  const [autoListen, setAutoListen] = useStoredState(
    "comic:auto-listen",
    false,
  );
  const [quickEdit, setQuickEdit] = useState<ComicLineView | null>(null);
  const annotations = useComicAnnotations(
    api,
    document,
    setDocument,
    setSelectedId,
  );
  const { annotate, savingBox, draftRegion, addBubble, savePlacement } =
    annotations;

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
    onKey(event: KeyboardEvent) {
      if (
        event.key === "Delete" &&
        !event.repeat &&
        annotate &&
        !locked &&
        selectedBox
      ) {
        event.preventDefault();
        annotations.deleteSelected(selectedBox, selected);
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
    },
  });
  const visible = view.visible.map((i) => document!.pages[i]);
  const page = visible[0];
  const boxes: ComicBoxView[] = visible.flatMap((p) =>
    pageBoxes(document, p.id),
  );
  // The selection is a line (a bubble, or one sentence of a text block); its box is for annotating.
  const lines: ComicLineView[] = boxLines(boxes);
  const selected = lines.find((line) => line.id === selectedId) || lines[0];
  const selectedBox = selected && boxOf(boxes, selected.id);

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
  return (
    <StudyLanguagesProvider document={document}>
      <ReadAloudProvider
        url={titlePath(id) + "/speech"}
        autoListen={autoListen}
      >
        <ReadAloudKey text={locked ? null : selected?.source} />
        <main className="comic-reader">
          <ComicReaderToolbar
            id={id}
            document={document}
            index={index}
            view={view}
            autoListen={autoListen}
            setAutoListen={setAutoListen}
            studyOpen={studyOpen}
            setStudyOpen={setStudyOpen}
            editing={editing}
            debug={debug}
            setDebug={setDebug}
            annotations={annotations}
            selectedBox={selectedBox}
            onDeleteSelected={() =>
              annotations.deleteSelected(selectedBox, selected)
            }
          />
          <ComicReaderNotices
            saveError={saveError}
            onRetryProgress={() => save(page.id)}
            annotations={annotations}
          />
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
                onLoaded={() => save(page.id)}
                debug={debug}
                study={{
                  selectedId: selected?.id,
                  onSelect: setSelectedId,
                  cards: !studyOpen,
                  blockMode,
                  onBlockMode: setBlockMode,
                }}
                annotations={{
                  enabled: annotate,
                  locked,
                  onDraw: addBubble,
                  onPlacement: savePlacement,
                }}
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
          portalTarget={undefined}
          api={titlePath(id)}
          cue={quickEdit}
          onSaved={(saved: ComicView | null) => saved && setDocument(saved)}
          onClose={() => setQuickEdit(null)}
        />
      )}
    </StudyLanguagesProvider>
  );
}
