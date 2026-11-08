import { useEffect, useState } from "react";
import { Badge, Button, Group, Text } from "@mantine/core";
import { findMedia, mediaName } from "../shared/media.js";
import { libraryReturnPath } from "../shared/library-return.js";
import "./Player.css";
import "./Watch.css";

/**
 * Plays a video that has no study document yet, with the browser's own controls: no subtitles,
 * notes or editing until a document references it. Its warnings stay visible in the bar.
 */
export function BareVideo({ fingerprint }) {
  const [media, setMedia] = useState(null);
  const [error, setError] = useState("");
  useEffect(() => {
    findMedia(fingerprint).then(setMedia, (failure) =>
      setError(failure.message),
    );
  }, [fingerprint]);
  return (
    <main className="left watch">
      <Group className="watch-bar" gap="sm" px="md" py="xs" wrap="nowrap">
        <Button
          component="a"
          size="xs"
          variant="default"
          href={libraryReturnPath()}
        >
          <span aria-hidden="true">←</span> Library
        </Button>
        <Text fw={600} size="sm" truncate style={{ flex: 1 }}>
          {mediaName(media, "Video")}
        </Text>
        {(media?.warnings ?? []).map((warning) => (
          <Badge key={warning} size="sm" variant="light" color="yellow">
            {warning}
          </Badge>
        ))}
      </Group>
      {error && (
        <Text role="alert" c="red" p="md">
          {error}
        </Text>
      )}
      <div className="video-player">
        <div className="video-stage">
          <video
            controls
            autoPlay
            preload="metadata"
            src={`/api/media/${encodeURIComponent(fingerprint)}/video`}
          />
        </div>
      </div>
    </main>
  );
}
