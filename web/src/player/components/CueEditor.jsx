import { useStudyLanguages } from "../StudyLanguages.jsx";
import { useRef, useState } from "react";
import {
  Alert,
  Button,
  Fieldset,
  Group,
  Stack,
  Text,
  Textarea,
  TextInput,
} from "@mantine/core";

/** Compact inline subtitle editor; annotations stay intact for the separate review workflow. */
export function CueEditor({ mode, cue, time, onSave, onClose }) {
  const { sourceLanguage, sourceName } = useStudyLanguages();
  const text = useRef(null);
  const [draft, setDraft] = useState(cue);
  const [at, setAt] = useState(
    time > cue.start && time < cue.end ? time : (cue.start + cue.end) / 2,
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function apply(operation, payload) {
    setBusy(true);
    setError("");
    try {
      await onSave(operation, payload);
      onClose();
    } catch (failure) {
      setError(failure.message);
      setBusy(false);
    }
  }

  function split() {
    const boundary = text.current.selectionStart;
    const first = draft.source.slice(0, boundary).trim();
    const second = draft.source.slice(boundary).trim();
    if (!first || !second) {
      setError("Place the text cursor between the two parts, then split.");
      text.current.focus();
      return;
    }
    apply("split", {
      at: Number(at),
      first,
      second,
      start: draft.start,
      end: draft.end,
    });
  }

  return (
    <form
      className="cue-editor"
      aria-label={
        mode === "split"
          ? "Split subtitle"
          : mode === "add"
            ? "Add subtitle"
            : "Edit subtitle"
      }
      onSubmit={(event) => {
        event.preventDefault();
        if (mode === "split") split();
        else apply(mode, draft);
      }}
    >
      <Fieldset disabled={busy} variant="unstyled">
        <Stack gap="xs">
          <Textarea
            ref={text}
            aria-label="Subtitle text"
            autoFocus
            required
            readOnly={mode === "split"}
            // Mantine hides read-only carets; splitting still needs a visible insertion point.
            styles={
              mode === "split" ? { input: { caretColor: "auto" } } : undefined
            }
            rows={2}
            placeholder={`Type the ${sourceName} subtitle…`}
            lang={sourceLanguage}
            value={draft.source}
            onChange={(event) =>
              setDraft({ ...draft, source: event.target.value })
            }
          />
          <Group gap="xs" align="flex-end">
            <Button size="xs" type="submit" loading={busy}>
              {mode === "split" ? "Split at cursor" : "Save"}
            </Button>
            <Button size="xs" variant="default" onClick={onClose}>
              Cancel
            </Button>
            {mode !== "split" && (
              <>
                <TextInput
                  label="Start"
                  type="number"
                  min="0"
                  step="any"
                  required
                  w={95}
                  size="xs"
                  value={draft.start}
                  onChange={(event) =>
                    setDraft({ ...draft, start: Number(event.target.value) })
                  }
                />
                <TextInput
                  label="End"
                  type="number"
                  min="0"
                  step="any"
                  required
                  w={95}
                  size="xs"
                  value={draft.end}
                  onChange={(event) =>
                    setDraft({ ...draft, end: Number(event.target.value) })
                  }
                />
              </>
            )}
            {mode === "split" && (
              <>
                <TextInput
                  label="Split time"
                  aria-label="Split time in seconds"
                  type="number"
                  min="0"
                  step="any"
                  w={100}
                  size="xs"
                  value={at}
                  onChange={(event) => setAt(event.target.value)}
                />
                <Text size="xs" c="dimmed">
                  Place the text cursor where the subtitle should split.
                </Text>
              </>
            )}
            {mode === "edit" && (
              <Button
                size="xs"
                variant="subtle"
                color="red"
                onClick={() => apply("delete")}
              >
                Delete subtitle
              </Button>
            )}
          </Group>
        </Stack>
      </Fieldset>
      {error && (
        <Alert color="red" role="alert" mt="xs" p="xs">
          {error}
        </Alert>
      )}
    </form>
  );
}
