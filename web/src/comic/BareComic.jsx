import { useEffect, useState } from "react";
import { Badge, Button, Group, SegmentedControl, Text } from "@mantine/core";
import { responseError } from "../shared/api.js";
import { findMedia, mediaName } from "../shared/media.js";
import { libraryReturnPath } from "../shared/library-return.js";
import { useStoredState } from "../shared/storage.js";
import { pageStep } from "./navigation.js";
import "../player/Watch.css";
import "./BareComic.css";

const pageUrl = (fingerprint, index) =>
  `/api/media/${encodeURIComponent(fingerprint)}/pages/${index}`;

/**
 * Reads a CBZ that has no study document yet: its pages one at a time, in filename order, with no
 * text, notes or editing until a document references it. Without a document there is no reading
 * direction, so the learner picks it (left to right by default); it and the page are remembered
 * per comic in this browser.
 */
export function BareComic({ fingerprint }) {
  const [media, setMedia] = useState(null);
  const [pages, setPages] = useState(null);
  const [error, setError] = useState("");
  const [place, setPlace] = useStoredState(`bare-comic:${fingerprint}`, {
    page: 0,
    direction: "ltr",
  });
  const count = pages?.length ?? 0;
  const page = Math.min(place.page, Math.max(0, count - 1));
  const turn = (step) =>
    setPlace((current) => ({
      ...current,
      page: Math.max(0, Math.min(count - 1, page + step)),
    }));

  useEffect(() => {
    findMedia(fingerprint).then(setMedia, (failure) =>
      setError(failure.message),
    );
    fetch(`/api/media/${encodeURIComponent(fingerprint)}/pages`)
      .then(async (response) => {
        if (!response.ok) throw await responseError(response);
        setPages(await response.json());
      })
      .catch((failure) => setError(failure.message));
  }, [fingerprint]);

  useEffect(() => {
    const onKey = (event) => {
      const step = pageStep(event.key, place.direction);
      if (!step) return;
      event.preventDefault();
      turn(step);
    };
    addEventListener("keydown", onKey);
    return () => removeEventListener("keydown", onKey);
  });

  // Fetch the next page ahead, so turning forward shows it at once.
  useEffect(() => {
    if (page + 1 < count) new Image().src = pageUrl(fingerprint, page + 1);
  }, [fingerprint, page, count]);

  const clickSide = (event) => {
    const box = event.currentTarget.getBoundingClientRect();
    const left = event.clientX - box.left < box.width / 2;
    turn(pageStep(left ? "ArrowLeft" : "ArrowRight", place.direction));
  };

  return (
    <main className="left watch bare-comic">
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
          {mediaName(media, "Comic")}
        </Text>
        {(media?.warnings ?? []).map((warning) => (
          <Badge key={warning} size="sm" variant="light" color="yellow">
            {warning}
          </Badge>
        ))}
        <SegmentedControl
          size="xs"
          aria-label="Reading direction"
          value={place.direction}
          onChange={(direction) =>
            setPlace((current) => ({ ...current, direction }))
          }
          data={[
            { value: "ltr", label: "Left to right" },
            { value: "rtl", label: "Right to left" },
          ]}
        />
        {count > 0 && (
          <Text size="sm" c="dimmed" style={{ whiteSpace: "nowrap" }}>
            {page + 1} / {count}
          </Text>
        )}
      </Group>
      {error && (
        <Text role="alert" c="red" p="md">
          {error}
        </Text>
      )}
      {pages && count === 0 && (
        <Text p="md" c="dimmed">
          This CBZ has no page images.
        </Text>
      )}
      {count > 0 && (
        <div className="bare-comic-stage" onClick={clickSide}>
          <img
            src={pageUrl(fingerprint, page)}
            alt={`Page ${page + 1} of ${count}`}
          />
        </div>
      )}
    </main>
  );
}
