import { ActionIcon, Group, SegmentedControl, Text } from "@mantine/core";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { ComicBubbleCard } from "./ComicBubbleCard.jsx";
import { useStudyLanguages } from "../player/StudyLanguages.jsx";

/**
 * A printed text block split into sentences, shown in one of two ways. "step" shows one sentence's
 * card at a time with previous/next through the block. "text" lists the whole block as running
 * lines; the selected sentence opens in place as its full card and the others stay one tap away.
 * Selection is the reader's, so the study panel and read-aloud follow the sentence shown here.
 * The switch at the top changes the reader-wide mode, so the two can be compared on any block.
 * In "step" every sentence's card is laid out in one grid cell and only the current one is shown,
 * so the card keeps the size of the longest and previous/next never resize it.
 */
export function ComicBlockCard({
  lines,
  selectedId,
  onSelect,
  mode,
  onMode,
  document,
  onKeep,
}) {
  const { sourceLanguage } = useStudyLanguages();
  const found = lines.findIndex((line) => line.id === selectedId);
  const position = found < 0 ? 0 : found;
  const current = lines[position];
  const card = (sentence) => (
    <ComicBubbleCard
      key={sentence.id}
      cue={sentence}
      document={document}
      onKeep={onKeep}
    />
  );
  const toggle = (
    <SegmentedControl
      size="xs"
      fullWidth
      mb={8}
      value={mode}
      onChange={(value) => {
        onKeep?.();
        onMode(value);
      }}
      data={[
        { value: "step", label: "One sentence at a time" },
        { value: "text", label: "Whole block" },
      ]}
    />
  );
  if (mode === "text")
    return (
      <>
        {toggle}
        <ol className="comic-block-lines" lang={sourceLanguage}>
          {lines.map((sentence) =>
            sentence === current ? (
              <li key={sentence.id} className="comic-block-open">
                {card(sentence)}
              </li>
            ) : (
              <li key={sentence.id}>
                <span
                  role="button"
                  tabIndex={0}
                  className="comic-block-line"
                  onClick={() => onSelect(sentence.id)}
                  onKeyDown={(event) => {
                    if (event.key !== "Enter" && event.key !== " ") return;
                    event.preventDefault();
                    onSelect(sentence.id);
                  }}
                >
                  {sentence.source}
                </span>
              </li>
            ),
          )}
        </ol>
      </>
    );
  return (
    <div className="comic-block-step">
      {toggle}
      <Group justify="space-between" wrap="nowrap" mb={6}>
        <Text size="xs" c="dimmed">
          Sentence {position + 1} of {lines.length}
        </Text>
        <Group gap={4} wrap="nowrap">
          <ActionIcon
            variant="default"
            size="sm"
            aria-label="Previous sentence"
            disabled={position === 0}
            onClick={() => onSelect(lines[position - 1].id)}
          >
            <ChevronLeft size={14} />
          </ActionIcon>
          <ActionIcon
            variant="default"
            size="sm"
            aria-label="Next sentence"
            disabled={position === lines.length - 1}
            onClick={() => onSelect(lines[position + 1].id)}
          >
            <ChevronRight size={14} />
          </ActionIcon>
        </Group>
      </Group>
      <div className="comic-block-stack">
        {lines.map((sentence) =>
          sentence === current ? (
            // Keyed by being shown too, so the shown card mounts fresh: its phrase state resets
            // and listen-on-open reads the new sentence.
            <div key={`${sentence.id}:shown`}>{card(sentence)}</div>
          ) : (
            <div
              key={sentence.id}
              className="comic-block-ghost"
              aria-hidden
              inert
            >
              <ComicBubbleCard cue={sentence} document={document} quiet />
            </div>
          ),
        )}
      </div>
    </div>
  );
}
