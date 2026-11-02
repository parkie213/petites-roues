import { useState } from "react";
import {
  Alert,
  Button,
  Group,
  Modal,
  Stack,
  Text,
  Textarea,
} from "@mantine/core";
import { request } from "../../shared/api.js";
import { useStudyLanguages } from "../StudyLanguages.jsx";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

/**
 * Fix one subtitle or comic bubble, or leave a comment for the reviewing agent, without leaving
 * the page. Text fixes are queued like any other edit; a comment changes nothing and is queued
 * for the agent to act on. Comics have no translation field: suggest one in the comment.
 * `portalTarget` keeps the dialog inside a fullscreen player, where the page root is hidden.
 */
export function QuickEdit({
  api,
  cue,
  comic = false,
  portalTarget,
  onSaved,
  onClose,
}) {
  const { sourceName, translationLanguage } = useStudyLanguages();
  const [source, setSource] = useState(cue.source || "");
  const [translation, setTranslation] = useState(cue.translation || "");
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sourceChanged = source.trim() !== (cue.source || "").trim();
  const translationChanged =
    !comic && translation.trim() !== (cue.translation || "").trim();
  const nothing = !sourceChanged && !translationChanged && !comment.trim();

  async function save() {
    setBusy(true);
    setError("");
    try {
      let saved = null;
      if (comic && sourceChanged)
        saved = await request(
          `${api}/comic/cues/${encodeURIComponent(cue.id)}`,
          json("PUT", { expectedSource: cue.source, source: source.trim() }),
        );
      else if (sourceChanged || translationChanged)
        saved = await request(
          `${api}/cues/${encodeURIComponent(cue.id)}`,
          json("PUT", {
            start: cue.start,
            end: cue.end,
            source: source.trim(),
            translation: translation.trim() || null,
            notes: cue.notes || [],
            continuesPrevious: Boolean(cue.continuesPrevious),
            listeningCheck: cue.listeningCheck ?? null,
            textCheck: cue.textCheck ?? null,
          }),
        );
      if (comment.trim())
        saved = await request(
          `${api}/cues/${encodeURIComponent(cue.id)}/comment`,
          json("POST", { text: comment.trim() }),
        );
      onSaved?.(saved);
      onClose();
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      opened
      onClose={() => !busy && onClose()}
      title={
        comic ? "Fix or comment on this bubble" : "Fix or comment on this line"
      }
      size="lg"
      centered
      portalProps={portalTarget ? { target: portalTarget } : undefined}
    >
      <Stack>
        <Textarea
          label={sourceName}
          value={source}
          onChange={(event) => setSource(event.target.value)}
          autosize
          minRows={1}
          disabled={busy}
        />
        {!comic && (
          <Textarea
            label="Translation"
            lang={translationLanguage}
            value={translation}
            onChange={(event) => setTranslation(event.target.value)}
            autosize
            minRows={1}
            disabled={busy}
          />
        )}
        <Textarea
          data-autofocus
          label="Comment for review"
          description="Leaves the line as it is; the reviewing agent decides what to change."
          placeholder={
            comic
              ? "A better translation, or what's unclear in the notes"
              : "e.g. 'isn't fun' → 'it's not funny'?"
          }
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          autosize
          minRows={2}
          disabled={busy}
          onKeyDown={(event) => {
            if (
              event.key === "Enter" &&
              (event.ctrlKey || event.metaKey) &&
              !nothing
            )
              save();
          }}
        />
        {(sourceChanged || translationChanged) && (
          <Text size="xs" c="dimmed">
            Changed text is queued for review, and its phrase notes are rebuilt.
          </Text>
        )}
        {error && (
          <Alert color="red" role="alert">
            {error}
          </Alert>
        )}
        <Group justify="flex-end">
          <Button variant="default" disabled={busy} onClick={onClose}>
            Cancel
          </Button>
          <Button loading={busy} disabled={nothing} onClick={save}>
            Save
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
