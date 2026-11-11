import { Button, Group, Modal, Stack, Text } from "@mantine/core";

/**
 * Asks before removing the folder [name] from the library, saying its files stay on disk. While
 * the removal is [busy] the dialog cannot be dismissed.
 */
export function RemoveFolderDialog({
  name,
  opened,
  busy,
  onCancel,
  onConfirm,
}: {
  name: string;
  opened: boolean;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal
      opened={opened}
      onClose={onCancel}
      title="Remove folder?"
      centered
      closeOnEscape={!busy}
      closeOnClickOutside={!busy}
      closeButtonProps={{ disabled: busy, "aria-label": "Close dialog" }}
    >
      <Stack>
        <Text size="sm">
          Remove{" "}
          <Text span fw={600}>
            {name}
          </Text>{" "}
          from the library? Its files stay on disk. Local review state for its
          titles will be removed.
        </Text>
        <Group justify="flex-end" mt="md">
          <Button variant="default" disabled={busy} onClick={onCancel}>
            Cancel
          </Button>
          <Button color="red" loading={busy} onClick={onConfirm}>
            Remove from library
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
