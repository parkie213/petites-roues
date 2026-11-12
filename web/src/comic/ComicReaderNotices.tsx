import { Alert, Button, Group, Text } from "@mantine/core";
import type { useComicAnnotations } from "./useComicAnnotations.ts";

/** Save failures and draft recovery remain beside the page they concern. */
export function ComicReaderNotices({
  saveError,
  onRetryProgress,
  annotations,
}: {
  saveError: string;
  onRetryProgress: () => void;
  annotations: ReturnType<typeof useComicAnnotations>;
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
      {annotate && (savingBox || draftRegion) && (
        <Group gap="xs" px="sm" py="xs">
          {savingBox ? (
            <Text size="sm">Saving box…</Text>
          ) : (
            <>
              <Button size="compact-xs" onClick={() => addBubble()}>
                Retry saving box
              </Button>
              <Button size="compact-xs" variant="subtle" onClick={discardDraft}>
                Discard unsaved box
              </Button>
            </>
          )}
        </Group>
      )}
    </>
  );
}
