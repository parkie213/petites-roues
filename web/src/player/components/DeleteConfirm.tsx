import { Alert, Button, Group } from "@mantine/core";

/** Asks before deleting the edited line, which queues the deletion for review. */
export function DeleteConfirm({
  busy,
  onConfirm,
  onKeep,
}: {
  busy: boolean;
  onConfirm: () => void;
  onKeep: () => void;
}) {
  return (
    <Alert color="red" title="Delete this subtitle?">
      This removes the line and queues the deletion for review.
      <Group mt="sm">
        <Button color="red" size="xs" disabled={busy} onClick={onConfirm}>
          Confirm delete
        </Button>
        <Button size="xs" variant="default" onClick={onKeep}>
          Keep subtitle
        </Button>
      </Group>
    </Alert>
  );
}
