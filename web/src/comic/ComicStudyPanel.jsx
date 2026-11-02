import { useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import {
  ActionIcon,
  Alert,
  Button,
  Group,
  Stack,
  Text,
  Textarea,
  Title,
} from "@mantine/core";
import { request } from "../shared/api.js";
import { LearnerNote } from "../player/components/LearnerNote.jsx";
import { ComicPhrasePairs } from "./ComicPhrasePairs.jsx";
import { useStudyLanguages } from "../player/StudyLanguages.jsx";
import { ListenButton } from "./ListenButton.jsx";

/** Inspect an agent's proposed transcription and safely correct its text without changing regions. */
export function ComicStudyPanel({
  cues,
  cue,
  api,
  onSelect,
  onSaved,
  onEditing,
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(cue?.source || "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const {
    sourceLanguage,
    translationLanguage,
    notesLanguage,
    sourceName,
    translationName,
  } = useStudyLanguages();
  function edit(value) {
    setEditing(value);
    onEditing(value);
    setError("");
  }
  async function save() {
    setSaving(true);
    setError("");
    try {
      const document = await request(
        `${api}/cues/${encodeURIComponent(cue.id)}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ expectedSource: cue.source, source: draft }),
        },
      );
      onSaved(document);
      edit(false);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setSaving(false);
    }
  }
  const position = cues.findIndex((item) => item.id === cue?.id);
  return (
    <aside
      className="comic-study-panel comic-phrases"
      aria-label="Bubble study"
    >
      <Stack gap="md">
        <Group justify="space-between" wrap="nowrap">
          <div>
            <Title order={2} size="h5">
              {cue ? `Bubble ${position + 1} of ${cues.length}` : "This page"}
            </Title>
            <Text size="xs" c="dimmed">
              Hover a bubble on the page to study it here.
            </Text>
          </div>
          {cue && (
            <Group gap={4} wrap="nowrap">
              <ListenButton text={cue.source} />
              <ActionIcon
                variant="default"
                aria-label="Previous bubble"
                disabled={editing || position === 0}
                onClick={() => onSelect(cues[position - 1].id)}
              >
                <ChevronUp size={16} />
              </ActionIcon>
              <ActionIcon
                variant="default"
                aria-label="Next bubble"
                disabled={editing || position === cues.length - 1}
                onClick={() => onSelect(cues[position + 1].id)}
              >
                <ChevronDown size={16} />
              </ActionIcon>
            </Group>
          )}
        </Group>
        {!cue ? (
          <Text size="sm" c="dimmed">
            No annotations on this page yet.
          </Text>
        ) : (
          <>
            {!cue.source && (
              <Text size="xs" c="dimmed">
                Box saved · awaiting OCR or transcription
              </Text>
            )}
            {cue.textCheck && (
              <Alert color="yellow" title="Text check">
                {cue.textCheck}
              </Alert>
            )}
            {editing ? (
              <>
                <Textarea
                  label={`${sourceName} text`}
                  lang={sourceLanguage}
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  autosize
                  minRows={3}
                  disabled={saving}
                />
                <Text size="xs" c="dimmed">
                  Changing the text clears its translation, notes and phrase
                  links, and queues an annotation review.
                </Text>
                {error && (
                  <Alert color="red" title="Could not save">
                    {error}
                  </Alert>
                )}
                <Group>
                  <Button
                    size="xs"
                    loading={saving}
                    disabled={!draft.trim() || draft === cue.source}
                    onClick={save}
                  >
                    Save text
                  </Button>
                  <Button
                    size="xs"
                    variant="default"
                    disabled={saving}
                    onClick={() => edit(false)}
                  >
                    Cancel
                  </Button>
                </Group>
              </>
            ) : (
              <>
                {cue.alignment ? (
                  <ComicPhrasePairs cue={cue} />
                ) : (
                  <>
                    <div>
                      <Text size="xs" c="dimmed" mb={6}>
                        {sourceName}
                      </Text>
                      <Text lang={sourceLanguage} fw={600}>
                        {cue.source || "Text awaiting OCR or transcription."}
                      </Text>
                    </div>
                    <div>
                      <Text size="xs" c="dimmed" mb={6}>
                        {translationName}
                      </Text>
                      <Text lang={translationLanguage}>
                        {cue.translation || "Translation awaiting review."}
                      </Text>
                    </div>
                    {cue.notes?.length > 0 && (
                      <div>
                        <Title order={3} size="sm">
                          Learner notes
                        </Title>
                        <ul
                          className="comic-learner-notes"
                          lang={notesLanguage}
                        >
                          {cue.notes.map((note, i) => (
                            <li key={i}>
                              <LearnerNote>{note}</LearnerNote>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </>
                )}
                {cue.alignmentStale && (
                  <Alert color="yellow" title="Annotation review needed">
                    The text changed. Teaching material needs to be regenerated.
                  </Alert>
                )}
                <Button
                  size="xs"
                  variant="light"
                  onClick={() => {
                    setDraft(cue.source);
                    edit(true);
                  }}
                >
                  {cue.source ? "Edit text" : "Add text"}
                </Button>
              </>
            )}
          </>
        )}
      </Stack>
    </aside>
  );
}
