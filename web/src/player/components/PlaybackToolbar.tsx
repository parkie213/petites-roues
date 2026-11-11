import { Button, Group, Switch, Text } from "@mantine/core";
import type { RefObject } from "react";
import { Pause, Play } from "lucide-react";
import { VideoControls } from "./VideoControls.jsx";

interface Props {
  videoRef: RefObject<HTMLVideoElement | null>;
  /** The player, which fullscreen enlarges. */
  containerRef: RefObject<HTMLElement | null>;
  time: number;
  playing: boolean;
  onToggle: () => void;
  onSeek: (seconds: number) => void;
  /** The auto-pause switch, or null for a video without lines to pause after. */
  autoPause: { checked: boolean; onChange: (checked: boolean) => void } | null;
  /** The last thing worth saying about playback, or empty. */
  status: string;
  onError: (message: string) => void;
}

/** The controls under the video: play or pause, the position and volume, and auto-pause. */
export function PlaybackToolbar({
  videoRef,
  containerRef,
  time,
  playing,
  onToggle,
  onSeek,
  autoPause,
  status,
  onError,
}: Props) {
  return (
    <Group
      className="playback-toolbar"
      gap={6}
      px="sm"
      py={6}
      aria-label="Playback controls"
    >
      <Button
        size="xs"
        variant="light"
        onClick={onToggle}
        aria-label={playing ? "Pause" : "Play"}
      >
        {playing ? <Pause size={17} /> : <Play size={17} />}
      </Button>
      <VideoControls
        videoRef={videoRef}
        containerRef={containerRef}
        time={time}
        onSeek={onSeek}
        onError={onError}
      />
      {autoPause && (
        <Switch
          size="xs"
          color="coral"
          label="Auto-pause"
          title="Pause after each subtitle (A)"
          styles={{ label: { fontSize: "var(--mantine-font-size-sm)" } }}
          checked={autoPause.checked}
          onChange={(event) => autoPause.onChange(event.currentTarget.checked)}
        />
      )}
      {status && (
        <Text size="xs" c="dimmed" className="watch-status" role="status">
          {status}
        </Text>
      )}
    </Group>
  );
}
