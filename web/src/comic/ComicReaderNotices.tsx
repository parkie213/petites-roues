import { Alert, Button, Group, Text } from "@mantine/core";
import type { ComicBoxView } from "../shared/types.ts";
import type { useComicAnnotations } from "./useComicAnnotations.ts";

/** Save failures and draft recovery remain beside the page they concern. */
export function ComicReaderNotices({
  saveError,
  onRetryProgress,
  annotations,
  selectedBox,
  onDeleteSelected,
}: {
  saveError: string;
  onRetryProgress: () => void;
  annotations: ReturnType<typeof useComicAnnotations>;
  selectedBox: ComicBoxView | undefined;
  onDeleteSelected: () => void;
}) {
  const {
    annotate,
    annotateError,
    setAnnotateError,
    savingBox,
    draftRegion,
    addBubble,
    discardDraft,
  } = annotations;
  return (
    <>
      {saveError && (
        <Alert color="yellow" title="Resume position">
          {saveError}{" "}
          <Button size="compact-xs" variant="subtle" onClick={onRetryProgress}>
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
            optional and can be added later. Drag existing boxes to move them;
            use the corners to resize.
          </Text>
          {savingBox && <Text size="sm">Saving box…</Text>}
          {!savingBox && draftRegion && (
            <Group gap="xs" mt="xs">
              <Button size="xs" onClick={() => addBubble()}>
                Retry saving box
              </Button>
              <Button size="xs" variant="subtle" onClick={discardDraft}>
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
              onClick={onDeleteSelected}
            >
              {selectedBox.lines.length > 1
                ? "Delete selected text block"
                : "Delete selected bubble"}
            </Button>
          )}
        </Alert>
      )}
    </>
  );
}
