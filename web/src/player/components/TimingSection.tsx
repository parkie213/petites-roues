import { ActionIcon, Button, Group, Text } from "@mantine/core";
import type { KeyboardEvent } from "react";
import { Crosshair, Play, Scissors } from "lucide-react";
import type { Boundary } from "../timeline.ts";
import { editClock } from "../subtitle-edit.ts";
import type { Times } from "../subtitle-edit.ts";

interface Props {
  times: Times;
  picked: Boundary;
  /** What timing's own saving last said. */
  status: string;
  busy: boolean;
  /** Whether the times are usable, so previews can play them. */
  valid: boolean;
  /** Whether the line is saved, so it can be split. */
  splittable: boolean;
  sourceName: string;
  onPick: (boundary: Boundary) => void;
  onSetToPlayhead: (boundary: Boundary) => void;
  /** The timing keys (see `timingKey`), for when the clocks have focus. */
  onKeyDown: (event: KeyboardEvent) => void;
  onPreview: (from: number, to: number) => void;
  onSplit: () => void;
}

const BOUNDARIES: Boundary[] = ["start", "end"];
const key = (boundary: Boundary) => (boundary === "start" ? "[" : "]");

/**
 * The line's timing in the editor: its start and end to the millisecond (the picked one is what
 * arrow keys nudge), setting either to the playhead, previews, and splitting the line.
 */
export function TimingSection({
  times,
  picked,
  status,
  busy,
  valid,
  splittable,
  sourceName,
  onPick,
  onSetToPlayhead,
  onKeyDown,
  onPreview,
  onSplit,
}: Props) {
  return (
    <section className="subtitle-edit-timing">
      <h3>Timing</h3>
      <div className="subtitle-edit-times" onKeyDown={onKeyDown}>
        {BOUNDARIES.map((boundary) => (
          <Group key={boundary} gap={2} wrap="nowrap">
            <Button
              size="compact-sm"
              variant="subtle"
              aria-label={`${boundary} time`}
              aria-pressed={picked === boundary}
              title={`Arrow keys nudge the ${boundary}; ${key(boundary)} sets it to the playhead`}
              disabled={busy}
              onClick={() => onPick(boundary)}
            >
              {editClock(times[boundary])}
            </Button>
            <ActionIcon
              size="sm"
              variant="subtle"
              disabled={busy}
              aria-label={`Set ${boundary} to playhead`}
              title={`Set ${boundary} to playhead (${key(boundary)})`}
              onClick={() => onSetToPlayhead(boundary)}
            >
              <Crosshair size={14} />
            </ActionIcon>
            {boundary === "start" && <span aria-hidden="true">→</span>}
          </Group>
        ))}
        <Text size="xs" c="dimmed" ml="auto">
          {(times.end - times.start).toFixed(2)} s
        </Text>
      </div>
      <Text size="xs" c="dimmed" mt={4}>
        Drag the line or its ends on the timeline. Drag an end onto a
        neighbour's to join them; Alt-drag separates them. Arrow keys nudge the
        chosen end. Timing saves itself.
      </Text>
      {status && (
        <Text size="xs" c="dimmed" role="status">
          {status}
        </Text>
      )}
      <Group gap={4} mt="xs">
        <Button
          size="compact-sm"
          variant="subtle"
          leftSection={<Play size={14} />}
          disabled={busy || !valid}
          onClick={() => onPreview(times.start, times.end)}
        >
          Preview line
        </Button>
        <Button
          size="compact-sm"
          variant="subtle"
          title="Play the line's last two seconds"
          disabled={busy || !valid}
          onClick={() =>
            onPreview(Math.max(times.start, times.end - 2), times.end)
          }
        >
          Last 2 s
        </Button>
        {BOUNDARIES.map((boundary) => (
          <Button
            key={boundary}
            size="compact-sm"
            variant="subtle"
            title={`Play a second either side of the ${boundary}`}
            disabled={busy || !valid}
            onClick={() => onPreview(times[boundary] - 1, times[boundary] + 1)}
          >
            Across {boundary}
          </Button>
        ))}
      </Group>
      {splittable && (
        <>
          <Button
            fullWidth
            variant="default"
            mt="sm"
            leftSection={<Scissors size={16} />}
            disabled={busy}
            onClick={onSplit}
          >
            Split at cursor &amp; playhead
          </Button>
          <Text size="xs" c="dimmed" mt={5}>
            Place the {sourceName} text cursor at the break; seek to the split
            time.
          </Text>
        </>
      )}
    </section>
  );
}
