import { useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { sendCommands } from "../shared/api.ts";
import type {
  ComicBoxView,
  ComicLineView,
  ComicRegion,
  ComicView,
} from "../shared/types.ts";

/** A drawn region kept until its box saves successfully or the person discards it. */
export interface DraftRegion {
  region: ComicRegion;
  pageId: string;
}

/**
 * Owns box mutations and their retry draft. Only one box creation can be in flight; its draft
 * keeps navigation locked after failure. Successful responses replace the reader's document.
 */
export function useComicAnnotations(
  api: string,
  document: ComicView | null,
  setDocument: Dispatch<SetStateAction<ComicView | null>>,
  setSelectedId: Dispatch<SetStateAction<string | null>>,
) {
  const [annotate, setAnnotate] = useState(false);
  const [draftRegion, setDraftRegion] = useState<DraftRegion | null>(null);
  const [savingBox, setSavingBox] = useState(false);
  const boxWrite = useRef(false);
  const [annotateError, setAnnotateError] = useState("");
  function annotateRequest(command: unknown) {
    return sendCommands<ComicView & { createdIds?: string[] }>(api, [command])
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
    annotateRequest({ type: "create-comic-box", create: { pageId, region } })
      .then((saved) => {
        const created = (saved.boxes || []).find((box) =>
          saved.createdIds?.includes(box.id),
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

  function savePlacement(box: ComicBoxView, regions: ComicRegion[]) {
    annotateRequest({
      type: "place-comic-box",
      boxId: box.id,
      update: { expectedRegions: box.regions, regions },
    }).catch(() => {});
  }

  function deleteSelected(
    selectedBox: ComicBoxView | undefined,
    selected: ComicLineView | undefined,
  ) {
    if (!selectedBox || !annotate) return;
    if (
      !window.confirm(
        selectedBox.lines.length > 1
          ? `Delete this text block and its ${selectedBox.lines.length} sentences?`
          : selected?.source
            ? `Delete the bubble "${selected?.source}"?`
            : "Delete this box?",
      )
    )
      return;
    annotateRequest({ type: "delete-comic-box", boxId: selectedBox.id })
      .then(() => setSelectedId(null))
      .catch(() => {});
  }

  function toggleAnnotate() {
    setAnnotate((value) => !value);
    setDraftRegion(null);
    setAnnotateError("");
  }
  return {
    annotate,
    draftRegion,
    savingBox,
    annotateError,
    setAnnotateError,
    discardDraft: () => setDraftRegion(null),
    toggleAnnotate,
    addBubble,
    savePlacement,
    deleteSelected,
  };
}
