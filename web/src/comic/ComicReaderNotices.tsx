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
        <Alert color="blue" title="Edit bubble regions">
          <Text size="sm">
            Drag an existing region to move it. Select a bubble to show its
            corner handles, then drag a corner to resize. Drag on empty page
            space to add a bubble; text can be added in the study panel. To
            remove an accidental box, select it and choose Delete selected
            bubble below. Changes save automatically and are queued for review.
            Use the toolbar’s Edit bubble regions button to finish.
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
            <Group gap="xs" mt="xs">
              <Text size="sm">
                Selected: {selectedBox.lines[0].source || "Empty bubble"}
              </Text>
              <Button
                size="compact-sm"
                variant="light"
                disabled={savingBox}
                color="red"
                mt="xs"
                onClick={onDeleteSelected}
              >
                {selectedBox.lines.length > 1
                  ? "Delete selected text block"
                  : "Delete selected bubble"}
              </Button>
            </Group>
          )}
        </Alert>
      )}
    </>
  );
}
