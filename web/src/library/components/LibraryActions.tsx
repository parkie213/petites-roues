import { ActionIcon, Button, Group } from "@mantine/core";
import { Plus, RefreshCw, Smartphone, Volume2 } from "lucide-react";
import { createPortal } from "react-dom";

/**
 * The library's actions, placed in the page header at [target]: choose read-aloud voices, connect a phone, refresh every
 * folder (once there is one), and add a folder. Nothing shows until the header provides a place.
 */
export function LibraryActions({
  target,
  canRefresh,
  working,
  onConnect,
  onVoices,
  onRefresh,
  onAdd,
}: {
  target?: Element | null;
  canRefresh: boolean;
  /** An action is under way: Refresh shows it, and Add waits. */
  working: boolean;
  onConnect: () => void;
  onVoices: () => void;
  onRefresh: () => void;
  onAdd: () => void;
}) {
  if (!target) return null;
  return createPortal(
    <Group gap="sm" className="library-actions">
      <ActionIcon
        variant="subtle"
        color="navy"
        size="lg"
        aria-label="Read-aloud voices"
        title="Read-aloud voices"
        onClick={onVoices}
      >
        <Volume2 size={21} />
      </ActionIcon>
      <ActionIcon
        variant="subtle"
        color="navy"
        size="lg"
        aria-label="Connect a phone"
        title="Connect a phone"
        onClick={onConnect}
      >
        <Smartphone size={21} />
      </ActionIcon>
      {canRefresh && (
        <ActionIcon
          variant="subtle"
          color="navy"
          size="lg"
          aria-label="Refresh library"
          title="Refresh library"
          loading={working}
          onClick={onRefresh}
        >
          <RefreshCw size={21} />
        </ActionIcon>
      )}
      <Button
        color="navy"
        leftSection={<Plus size={17} />}
        disabled={working}
        onClick={onAdd}
      >
        Add folder
      </Button>
    </Group>,
    target,
  );
}
