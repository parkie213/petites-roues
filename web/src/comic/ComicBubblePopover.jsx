import { Popover, Text } from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { ComicBubbleCard } from "./ComicBubbleCard.jsx";
import { useStudyLanguages } from "../player/StudyLanguages.jsx";

/**
 * Anchors a bubble's study card to its page region. The reader owns whether it is open and
 * pinned; this component only reports pointer movement across the card so hover can persist
 * while the learner explores phrases inside it.
 */
export function ComicBubblePopover({
  cue,
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
        className="comic-bubble-popover comic-phrases"
        aria-label={`Bubble ${number}`}
        onPointerEnter={onPointerEnter}
        onPointerLeave={onPointerLeave}
      >
        {cue.source ? (
          <ComicBubbleCard
            key={cue.id}
            cue={cue}
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
