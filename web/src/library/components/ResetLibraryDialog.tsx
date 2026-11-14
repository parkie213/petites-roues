import { Button, Group, List, Modal, Stack, Text } from "@mantine/core";

/**
 * Asks before resetting the library, saying what is lost (positions, phone pairings and review
 * history) and what is kept (folders, files on disk and settings). Confirming starts the reset
 * job; the library page shows its progress.
 */
export function ResetLibraryDialog({
  opened,
  onCancel,
  onConfirm,
}: {
  opened: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal
      opened={opened}
      onClose={onCancel}
      title="Reset library?"
      centered
      closeButtonProps={{ "aria-label": "Close dialog" }}
    >
      <Stack>
        <Text size="sm">
          The library is deleted and rebuilt from your folders. This cannot be
          undone.
        </Text>
        <div>
          <Text size="sm" fw={600}>
            Lost
          </Text>
          <List size="sm">
            <List.Item>Reading and playback positions</List.Item>
            <List.Item>Phone pairings; phones must pair again</List.Item>
            <List.Item>Review history</List.Item>
          </List>
        </div>
        <div>
          <Text size="sm" fw={600}>
            Kept
          </Text>
          <List size="sm">
            <List.Item>The registered folders</List.Item>
            <List.Item>Documents and media on disk</List.Item>
            <List.Item>Settings</List.Item>
          </List>
        </div>
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={onCancel}>
            Cancel
          </Button>
          <Button color="red" onClick={onConfirm}>
            Reset library
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
