import { ActionIcon, Badge, Button, Group, Menu, Text } from "@mantine/core";
import { ArrowLeft, Ellipsis, Lightbulb, Pencil } from "lucide-react";
import { PlayerShortcuts } from "./PlayerShortcuts.jsx";

interface Props {
  title: string;
  /** Problems with the media, such as no playable audio, shown as badges. */
  warnings: readonly string[];
  /** The library page to go back to. */
  returnPath: string;
  /**
   * What needs a study document, or null for a video without one: Quiz me and the menu with
   * line fixes and the shortcuts.
   */
  study: {
    quizOpen: boolean;
    onQuiz: () => void;
    onEditLine: () => void;
  } | null;
}

/** The viewing page's top bar: back to the library, the title and its warnings, and study tools. */
export function WatchBar({ title, warnings, returnPath, study }: Props) {
  return (
    <Group className="watch-bar" gap="sm" wrap="nowrap">
      <ActionIcon
        component="a"
        variant="subtle"
        href={returnPath}
        aria-label="Back to library"
        title="Library"
      >
        <ArrowLeft size={19} />
      </ActionIcon>
      <Text size="sm" truncate style={{ flex: 1 }}>
        {title}
      </Text>
      {warnings.map((warning) => (
        <Badge key={warning} size="sm" variant="light" color="yellow">
          {warning}
        </Badge>
      ))}
      {study && (
        <Button
          size="xs"
          variant="default"
          leftSection={<Lightbulb size={14} />}
          aria-expanded={study.quizOpen}
          aria-controls="video-quiz-dock"
          onClick={study.onQuiz}
        >
          Quiz me
        </Button>
      )}
      {study && (
        <Menu position="bottom-end" withinPortal={false}>
          <Menu.Target>
            <ActionIcon
              variant="subtle"
              aria-label="More options"
              title="More options"
            >
              <Ellipsis size={19} />
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown>
            <Menu.Item
              leftSection={<Pencil size={14} />}
              onClick={study.onEditLine}
            >
              Fix or comment on this line
            </Menu.Item>
            <Menu.Divider />
            <PlayerShortcuts editing={false} viewing />
          </Menu.Dropdown>
        </Menu>
      )}
    </Group>
  );
}
