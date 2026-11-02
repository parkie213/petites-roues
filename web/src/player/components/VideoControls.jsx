import { useEffect, useState } from "react";
import { ActionIcon, Slider, Text } from "@mantine/core";
import { Maximize, Minimize, Volume2, VolumeX } from "lucide-react";
import { toggleFullscreen } from "../playback.js";

function clock(seconds) {
  const total = Math.max(0, Math.floor(seconds || 0));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const rest = String(total % 60).padStart(2, "0");
  return hours
    ? `${hours}:${String(minutes).padStart(2, "0")}:${rest}`
    : `${minutes}:${rest}`;
}

/** Permanent controls outside the video, so native controls never displace its captions. */
export function VideoControls({
  videoRef,
  containerRef,
  time,
  onSeek,
  onError,
}) {
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const [canFullscreen, setCanFullscreen] = useState(false);
  // The time under the mouse, so the position a click seeks to is visible before clicking.
  const [hover, setHover] = useState(null);

  function showHoverTime(event) {
    // Measure the element Mantine maps clicks from, so the label matches the seek exactly.
    const track = event.currentTarget.querySelector(
      ".mantine-Slider-trackContainer",
    );
    if (!duration || !track || event.buttons) {
      setHover(null);
      return;
    }
    const { left, width } = track.getBoundingClientRect();
    const x = Math.min(Math.max(event.clientX, left), left + width);
    setHover({
      left: x - event.currentTarget.getBoundingClientRect().left,
      time: ((x - left) / width) * duration,
    });
  }

  useEffect(() => {
    const video = videoRef.current;
    const updateDuration = () =>
      setDuration(Number.isFinite(video.duration) ? video.duration : 0);
    const updateVolume = () => {
      setVolume(video.volume);
      setMuted(video.muted);
    };
    const updateFullscreen = () =>
      setFullscreen(document.fullscreenElement === containerRef.current);
    updateDuration();
    updateVolume();
    updateFullscreen();
    setCanFullscreen(Boolean(document.fullscreenEnabled));
    video.addEventListener("loadedmetadata", updateDuration);
    video.addEventListener("durationchange", updateDuration);
    video.addEventListener("volumechange", updateVolume);
    document.addEventListener("fullscreenchange", updateFullscreen);
    return () => {
      video.removeEventListener("loadedmetadata", updateDuration);
      video.removeEventListener("durationchange", updateDuration);
      video.removeEventListener("volumechange", updateVolume);
      document.removeEventListener("fullscreenchange", updateFullscreen);
    };
  }, [videoRef, containerRef]);

  return (
    <div
      className="video-controls"
      role="group"
      aria-label="Video position and audio"
    >
      <div
        className="video-position"
        onMouseMove={showHoverTime}
        onMouseLeave={() => setHover(null)}
      >
        <Slider
          thumbLabel="Video position"
          label={(value) => clock(value)}
          showLabelOnHover={false}
          thumbProps={{
            "aria-valuetext": `${clock(time)} of ${clock(duration)}`,
          }}
          min={0}
          max={duration || 1}
          step={0.1}
          value={Math.min(time, duration)}
          disabled={!duration}
          onChange={onSeek}
          size="xs"
        />
        {hover && (
          <div
            className="video-position-hover"
            style={{ left: hover.left }}
            aria-hidden="true"
          >
            {clock(hover.time)}
          </div>
        )}
      </div>
      <Text
        size="xs"
        c="dimmed"
        style={{ fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap" }}
      >
        {clock(time)} / {clock(duration)}
      </Text>
      <ActionIcon
        size="sm"
        variant="subtle"
        aria-label={muted || volume === 0 ? "Unmute" : "Mute"}
        aria-pressed={muted || volume === 0}
        onClick={() => {
          const video = videoRef.current;
          if (video.muted || video.volume === 0) {
            if (video.volume === 0) video.volume = 1;
            video.muted = false;
          } else video.muted = true;
        }}
      >
        {muted || volume === 0 ? <VolumeX size={16} /> : <Volume2 size={16} />}
      </ActionIcon>
      <Slider
        thumbLabel="Volume"
        label={(value) => `${Math.round(value * 100)}%`}
        thumbProps={{
          "aria-valuetext": `${Math.round((muted ? 0 : volume) * 100)} percent`,
        }}
        min={0}
        max={1}
        step={0.05}
        value={muted ? 0 : volume}
        onChange={(value) => {
          videoRef.current.volume = value;
          videoRef.current.muted = false;
        }}
        size="xs"
        w={55}
      />
      {canFullscreen && (
        <ActionIcon
          size="sm"
          variant="subtle"
          aria-label={fullscreen ? "Exit fullscreen" : "Enter fullscreen"}
          title={fullscreen ? "Exit fullscreen (F)" : "Enter fullscreen (F)"}
          onClick={async () => {
            try {
              await toggleFullscreen(containerRef.current);
            } catch (error) {
              onError(error.message);
            }
          }}
        >
          {fullscreen ? <Minimize size={16} /> : <Maximize size={16} />}
        </ActionIcon>
      )}
    </div>
  );
}
