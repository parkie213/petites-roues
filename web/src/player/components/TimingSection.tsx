import { ActionIcon, Button, Checkbox, Group, Text } from "@mantine/core";
import type { KeyboardEvent } from "react";
import { Crosshair, Play, Scissors } from "lucide-react";
import type { Boundary } from "../timeline.ts";
import { boundaryRanges, editClock } from "../subtitle-edit.ts";
import type { Range, Times } from "../subtitle-edit.ts";

/** A phrase beside the boundary: its text and where it plays. */
export type Phrase = Times & { source: string };

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
  /** The phrases meeting at the picked end: A comes first, B follows; null where there is none. */
  a: Phrase | null;
  b: Phrase | null;
  /** Whether stepping to another line plays it. */
  playOnStep: boolean;
  onPlayOnStep: (on: boolean) => void;
  onPick: (boundary: Boundary) => void;
  onSetToPlayhead: (boundary: Boundary) => void;
  /** The timing keys (see `timingKey`), for when the clocks have focus. */
  onKeyDown: (event: KeyboardEvent) => void;
  onPreview: (from: number, to: number) => void;
  onSplit: () => void;
}

const BOUNDARIES: Boundary[] = ["start", "end"];
const SIDES = ["A", "B"] as const;

/**
 * The line's timing in the editor: its start and end to the millisecond (the picked one is what
 * arrow keys nudge), setting either to the playhead, hearing the two phrases that meet at the
 * picked end, and splitting the line.
 */
export function TimingSection({
  times,
  picked,
  status,
  busy,
  valid,
  splittable,
  sourceName,
  a,
  b,
  playOnStep,
  onPlayOnStep,
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
              title={`Arrow keys nudge the ${boundary}`}
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
              title={`Set ${boundary} to playhead`}
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
        chosen end. [ and ] step to the previous and next line. Timing saves
        itself.
      </Text>
      {status && (
        <Text size="xs" c="dimmed" role="status">
          {status}
        </Text>
      )}
      <div
        className="subtitle-edit-boundary"
        role="group"
        aria-label="Boundary"
      >
        <Text size="xs" c="dimmed">
          The {picked} meets the {picked === "end" ? "next" : "previous"} line:
          play both sides to hear where it belongs.
        </Text>
        <div className="subtitle-edit-sides">
          {SIDES.map((side) => {
            const phrase = side === "A" ? a : b;
            const own = (picked === "end") === (side === "A");
            const reason = `No ${picked === "end" ? "next" : "previous"} line`;
            const ranges = boundaryRanges(a, b);
            const plays: [string, Range | null][] =
              side === "A"
                ? [
                    ["All of A", ranges.allA],
                    ["Last 1 s of A", ranges.lastA],
                  ]
                : [
                    ["First 1 s of B", ranges.firstB],
                    ["All of B", ranges.allB],
                  ];
            return (
              <div key={side} className="subtitle-edit-side">
                <Text size="xs" fw={600}>
                  {side} ·{" "}
                  {own
                    ? "this line"
                    : picked === "end"
                      ? "next line"
                      : "previous line"}
                </Text>
                <Text size="xs" lineClamp={2} c={phrase ? undefined : "dimmed"}>
                  {phrase ? phrase.source : reason}
                </Text>
                <Text size="xs" c="dimmed" className="subtitle-edit-clocks">
                  {phrase
                    ? `${editClock(phrase.start)} → ${editClock(phrase.end)}`
                    : " "}
                </Text>
                <Group gap={4} mt={4}>
                  {plays.map(([label, range]) => (
                    <Button
                      key={label}
                      size="compact-xs"
                      variant="light"
                      leftSection={<Play size={12} />}
                      title={phrase ? undefined : reason}
                      disabled={busy || !valid || !range}
                      onClick={() => range && onPreview(range.from, range.to)}
                    >
                      {label}
                    </Button>
                  ))}
                </Group>
              </div>
            );
          })}
        </div>
        <Checkbox
          size="xs"
          mt={6}
          label="Play on step ([ previous, ] next)"
          checked={playOnStep}
          onChange={(event) => onPlayOnStep(event.currentTarget.checked)}
        />
      </div>
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
