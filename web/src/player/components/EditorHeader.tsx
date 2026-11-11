import { ActionIcon, Group } from "@mantine/core";
import { ChevronLeft, ChevronRight, X } from "lucide-react";

interface Props {
  adding: boolean;
  /** Whether going to another line saves this one first. */
  dirty: boolean;
  busy: boolean;
  /** The lines before and after this one, by their start, or null where there is none. */
  previous: number | null;
  next: number | null;
  /** Goes to the line [step] away, the playhead to [start]. */
  onStep: (step: -1 | 1, start: number) => void;
  onClose: () => void;
}

/** The subtitle editor's heading: what it does, stepping between lines, and closing. */
export function EditorHeader({
  adding,
  dirty,
  busy,
  previous,
  next,
  onStep,
  onClose,
}: Props) {
  const steps = [
    ["Previous line", -1, ChevronLeft, previous],
    ["Next line", 1, ChevronRight, next],
  ] as const;
  return (
    <header>
      <h2>
        {adding ? "Add subtitle" : "Edit subtitle"} <kbd>E</kbd>
      </h2>
      <Group gap={2} ml="auto" mr="xs">
        {steps.map(([label, step, Icon, start]) => (
          <ActionIcon
            key={label}
            variant="subtle"
            aria-label={label}
            title={dirty ? `Save and go to the ${label.toLowerCase()}` : label}
            disabled={busy || start === null}
            onClick={() => start !== null && onStep(step, start)}
          >
            <Icon size={18} />
          </ActionIcon>
        ))}
      </Group>
      <ActionIcon
        variant="subtle"
        aria-label="Close subtitle editor"
        disabled={busy}
        onClick={onClose}
      >
        <X size={18} />
      </ActionIcon>
    </header>
  );
}
