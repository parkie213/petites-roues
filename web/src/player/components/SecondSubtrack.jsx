import { useEffect, useRef, useState } from "react";
import { Button } from "@mantine/core";
import { parseSubtitles } from "../subtitles.js";

/** Loads a temporary, read-only subtitle track for comparison on the timeline. */
export function SecondSubtrack({ titleId, onError, onChange }) {
  const input = useRef(null);
  const [source, setSource] = useState(null);
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    setSource(null);
    setVisible(true);
  }, [titleId]);

  useEffect(() => {
    onChange(source && visible ? source : null);
  }, [source, visible, onChange]);

  async function load(event) {
    const file = event.target.files[0];
    event.target.value = "";
    if (!file) return;
    try {
      const cues = parseSubtitles(await file.text());
      setSource({ name: file.name, cues });
      setVisible(true);
      onError("");
    } catch (error) {
      onError(error.message);
    }
  }

  return (
    <>
      <input
        ref={input}
        type="file"
        accept=".srt"
        hidden
        aria-label="Second subtrack SRT"
        onChange={load}
      />
      <Button
        size="xs"
        variant="default"
        title={
          source
            ? `${source.name} · ${source.cues.length} cues · displayed below the current timeline cues`
            : "Choose a local SRT to display below the current timeline cues"
        }
        onClick={() => (source ? setVisible(!visible) : input.current.click())}
        aria-pressed={Boolean(source && visible)}
      >
        {source && visible ? "Hide second subtrack" : "Display second subtrack"}
      </Button>
      {source && (
        <Button
          size="xs"
          variant="default"
          onClick={() => input.current.click()}
          title={source.name}
        >
          Change SRT
        </Button>
      )}
    </>
  );
}
