import { Popover, Text } from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { ComicBubbleCard } from "./ComicBubbleCard.jsx";
import { ComicBlockCard } from "./ComicBlockCard.jsx";
import { useStudyLanguages } from "../player/StudyLanguages.jsx";

/**
 * Anchors a bubble's study card to its page region. The reader owns whether it is open and
 * pinned; this component only reports pointer movement across the card so hover can persist
 * while the learner explores phrases inside it. A text block's card opens on the reader's selected
 * sentence (`selectedId`) when it is one of the block's, else on the first, stepping or listing as
 * `blockMode` chooses.
 */
export function ComicBubblePopover({
  box,
  selectedId,
  blockMode,
  onBlockMode,
  onSelect,
  document,
  number,
  opened,
  onDismiss,
  onKeep,
  onPointerEnter,
  onPointerLeave,
  children,
}) {
  const { sourceLanguage } = useStudyLanguages();
  const block = box.lines.length > 1;
  const line = box.lines[0];
  // The card only fits beside the bubble on wide screens.
  const wide = useMediaQuery("(min-width: 900px)");
  return (
    <Popover
      opened={opened}
      onChange={(value) => !value && onDismiss()}
      position={wide ? "right-start" : "bottom"}
      shadow="lg"
      withArrow
      arrowPosition="side"
      returnFocus
      middlewares={{ flip: true, shift: { padding: 12 } }}
      transitionProps={{ duration: 80 }}
    >
      <Popover.Target>{children}</Popover.Target>
      <Popover.Dropdown
        className={
          "comic-bubble-popover comic-phrases" +
          (block && blockMode === "text" ? " is-block-text" : "")
        }
        aria-label={block ? `Text block ${number}` : `Bubble ${number}`}
        onPointerEnter={onPointerEnter}
        onPointerLeave={onPointerLeave}
      >
        {block ? (
          <ComicBlockCard
            lines={box.lines}
            selectedId={selectedId}
            onSelect={(id) => {
              onKeep();
              onSelect(id);
            }}
            mode={blockMode}
            onMode={onBlockMode}
            document={document}
            onKeep={onKeep}
          />
        ) : line.source ? (
          <ComicBubbleCard
            key={line.id}
            cue={line}
            document={document}
            onKeep={onKeep}
          />
        ) : (
          <Text size="sm" c="dimmed" lang={sourceLanguage}>
            Text awaiting OCR or transcription.
          </Text>
        )}
      </Popover.Dropdown>
    </Popover>
  );
}
