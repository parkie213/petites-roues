import { useState } from "react";
import {
  Button,
  Code,
  Modal,
  ScrollArea,
  Stack,
  Text,
  TextInput,
  UnstyledButton,
} from "@mantine/core";
import {
  documentChoices,
  filterChoices,
  linkMedia,
  mediaChoices,
} from "./media-links.js";
import "./LinkDialog.css";

/**
 * Links a media file to a study document by hand. [link] says from which side:
 * `{ media: { repositoryId, relativePath, kind, name } }` picks a document for that file, and
 * `{ title: { id, type, name } }` picks a media file for that document. Null keeps it closed.
 * [repos] is the loaded library; [onDone] reloads it, receiving the error when linking failed.
 */
export function LinkDialog({ link, repos, onClose, onDone }) {
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const forMedia = Boolean(link?.media);
  const kind = forMedia ? link.media.kind : link?.title.type;
  const choices = !link
    ? []
    : filterChoices(
        forMedia ? documentChoices(repos, kind) : mediaChoices(repos, kind),
        text,
      );
  const what = kind === "comic" ? "CBZ" : "video";

  async function pick(choice) {
    setBusy(true);
    try {
      await linkMedia(
        forMedia
          ? { ...link.media, titleId: choice.titleId }
          : { ...choice, titleId: link.title.id },
      );
      close();
      onDone();
    } catch (error) {
      onDone(error);
    } finally {
      setBusy(false);
    }
  }
  function close() {
    setText("");
    onClose();
  }

  return (
    <Modal
      opened={Boolean(link)}
      onClose={close}
      title={forMedia ? "Link to study document" : `Locate ${what}`}
      centered
      size="lg"
      closeButtonProps={{ "aria-label": "Close dialog" }}
    >
      {link && (
        <Stack gap="sm">
          <Text size="sm">
            Choose the {forMedia ? "study document" : what} for{" "}
            <Text span fw={600}>
              {forMedia ? link.media.name : link.title.name}
            </Text>
            .
          </Text>
          <TextInput
            placeholder="Filter"
            aria-label="Filter"
            value={text}
            onChange={(event) => setText(event.currentTarget.value)}
            data-autofocus
          />
          <ScrollArea.Autosize mah={360}>
            <Stack gap={2}>
              {choices.map((choice) => (
                <UnstyledButton
                  key={choice.key}
                  className="link-choice"
                  disabled={busy}
                  onClick={() => pick(choice)}
                >
                  <Text size="sm" fw={600} style={{ overflowWrap: "anywhere" }}>
                    {choice.label}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {choice.detail}
                  </Text>
                </UnstyledButton>
              ))}
              {choices.length === 0 && (
                <Text size="sm" c="dimmed">
                  {text
                    ? "Nothing matches."
                    : forMedia
                      ? "No study documents of this kind. Add the folder that holds them."
                      : `No ${what} files. Add the folder that holds them.`}
                </Text>
              )}
            </Stack>
          </ScrollArea.Autosize>
          <Text size="xs" c="dimmed">
            The link is kept in this library only; the document is not changed.
            To share the fix, record this file as the document's reference with{" "}
            <Code>prepare/reference.py</Code>.
          </Text>
          <Button
            variant="default"
            onClick={close}
            style={{ alignSelf: "end" }}
          >
            Cancel
          </Button>
        </Stack>
      )}
    </Modal>
  );
}
