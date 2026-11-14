import { isDemo } from "../../shared/app-location.js";
import { ActionIcon, Button, Group, Menu, Text } from "@mantine/core";
import { Ellipsis, Plus, RefreshCw, Smartphone, Volume2 } from "lucide-react";
import { useState } from "react";
import { createPortal } from "react-dom";
import { ResetLibraryDialog } from "./ResetLibraryDialog.tsx";

/**
 * The library's actions, placed in the page header at [target]: choose read-aloud voices, connect a phone, refresh every
 * folder (once there is one), add a folder, and reset the library from the overflow menu. Nothing shows until the header provides a place.
 */
export function LibraryActions({
  target,
  canRefresh,
  working,
  onConnect,
  onVoices,
  onRefresh,
  onAdd,
  hasPendingEdits,
  onMenuOpen,
  onReset,
}: {
  target?: Element | null;
  canRefresh: boolean;
  /** An action is under way: Refresh shows it, and Add waits. */
  working: boolean;
  onConnect: () => void;
  onVoices: () => void;
  onRefresh: () => void;
  onAdd: () => void;
  /** Edits wait for review, so a reset would lose them; null until known. */
  hasPendingEdits: boolean | null;
  /** The overflow menu opened: the page checks for pending edits. */
  onMenuOpen: () => void;
  onReset: () => void;
}) {
  const [confirming, setConfirming] = useState(false);
  if (!target) return null;
  return createPortal(
    <Group gap="sm" className="library-actions">
      <ActionIcon
        variant="subtle"
        color="navy"
        size="lg"
        aria-label="Read-aloud voices"
        title="Read-aloud voices"
        disabled={isDemo}
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
        disabled={isDemo}
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
          disabled={isDemo}
          onClick={onRefresh}
        >
          <RefreshCw size={21} />
        </ActionIcon>
      )}
      <Button
        color="navy"
        leftSection={<Plus size={17} />}
        disabled={isDemo || working}
        onClick={onAdd}
      >
        Add folder
      </Button>
      <Menu position="bottom-end" onOpen={onMenuOpen}>
        <Menu.Target>
          <ActionIcon
            variant="subtle"
            color="navy"
            size="lg"
            aria-label="More library actions"
            title="More library actions"
            disabled={isDemo}
          >
            <Ellipsis size={21} />
          </ActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item
            color="red"
            disabled={working || hasPendingEdits === true}
            onClick={() => setConfirming(true)}
          >
            Reset library
          </Menu.Item>
          {hasPendingEdits === true && (
            <Text size="xs" c="dimmed" px="sm" pb="xs" maw={240}>
              Edits are pending: process or discard them first.
            </Text>
          )}
        </Menu.Dropdown>
      </Menu>
      <ResetLibraryDialog
        opened={confirming}
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false);
          onReset();
        }}
      />
    </Group>,
    target,
  );
}
