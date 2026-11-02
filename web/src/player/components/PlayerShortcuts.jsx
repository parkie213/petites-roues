import { Group, Kbd, Text } from "@mantine/core";
import { PlayerMenu } from "./PlayerMenu.jsx";

/**
 * Shows the player's keyboard controls beside playback, including in fullscreen. The viewing
 * page (`viewing`) has no definitions, timing or transcript, so it lists only its own keys.
 */
export function PlayerShortcuts({ editing, viewing = false }) {
  const shortcuts = [
    ["Space", "Play / pause"],
    ["←", "Restart / previous subtitle"],
    ["→", "Next subtitle"],
    ["Shift + ← / →", "Seek 2 seconds"],
    ["R", "Replay subtitle"],
    ["T", "Toggle translation"],
    ["E", "Fix or comment on this line"],
    ["W", "Toggle sentence notes"],
    ["F", "Enter / exit fullscreen"],
    ["S", "Toggle video subtitles"],
    ["A", "Toggle autopause"],
    ...(viewing
      ? []
      : [
          ["D", "Toggle phrase definitions"],
          ["C", "Toggle cue timing adjustment"],
          ["/", "Search transcript"],
        ]),
    ...(editing ? [["[ / ]", "Set subtitle start / end"]] : []),
  ];
  return (
    <PlayerMenu label="Shortcuts">
      {shortcuts.map(([key, label]) => (
        <Group key={key} justify="space-between" wrap="nowrap" gap="sm">
          <Text size="xs">{label}</Text>
          <Kbd size="xs" style={{ whiteSpace: "nowrap" }}>
            {key}
          </Kbd>
        </Group>
      ))}
      <Text size="xs" c="dimmed">
        Shortcuts are inactive while entering text. Resuming playback hides
        translations and sentence notes. In define mode, arrows move between
        phrases. D, S, Space, W, T, E or R dismisses it.
      </Text>
    </PlayerMenu>
  );
}
