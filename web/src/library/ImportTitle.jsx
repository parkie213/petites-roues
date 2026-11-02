import { useEffect, useRef, useState } from "react";
import {
  Alert,
  Button,
  Group,
  Modal,
  SegmentedControl,
  Select,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import { TriangleAlert } from "lucide-react";
import { chooseOnComputer, request } from "../shared/api.js";

// ffprobe reports ISO 639-2 tags; documents use BCP 47.
const LANGUAGE_TAGS = {
  fr: ["fr", "fre", "fra"],
  en: ["en", "eng"],
  de: ["de", "ger", "deu"],
  es: ["es", "spa"],
  it: ["it", "ita"],
  ja: ["ja", "jpn"],
  ko: ["ko", "kor"],
};

function preferredTrack(tracks, language) {
  const tags = LANGUAGE_TAGS[language] || [language];
  const matching = tracks.filter((track) =>
    tags.includes((track.language || "").toLowerCase()),
  );
  return (
    matching.find((track) => track.defaultTrack) ||
    matching[0] ||
    tracks.find((track) => track.defaultTrack) ||
    tracks[0]
  );
}

function trackLabel(track) {
  return [
    `Track ${track.index}`,
    track.language,
    track.title,
    track.playable ? track.codec : `${track.codec} → converted to AAC`,
  ]
    .filter(Boolean)
    .join(" · ");
}

// The server copies the media in the background, because browsers drop a request that stays
// silent for long (Firefox does after any network change). Polls until the job ends, riding out
// a few dropped polls.
async function importFinished(job) {
  for (let dropped = 0; ;) {
    await new Promise((resolve) => setTimeout(resolve, 1500));
    let progress;
    try {
      progress = await request(`/api/import/${encodeURIComponent(job)}`);
      dropped = 0;
    } catch (failure) {
      if (failure.status || ++dropped > 5) throw failure;
      continue;
    }
    if (progress.status === "DONE") return progress.created;
    if (progress.status === "FAILED") throw Error(progress.error);
  }
}

/**
 * Creates a title from a video or CBZ on this computer: pick the file, then the repository folder,
 * languages and audio track. The server copies the media and writes an empty document.
 */
export function ImportTitle({ repos, selection, onCreated, onClose }) {
  const [file, setFile] = useState(null);
  const [form, setForm] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (field) => (value) =>
    setForm((current) => ({ ...current, [field]: value }));

  // Opens the system file dialog; cancelling the first choice closes the import.
  async function pick() {
    setError("");
    try {
      const path = await chooseOnComputer("media");
      if (path) await inspect(path);
      else if (!file) onClose();
    } catch (failure) {
      setError(failure.message);
    }
  }
  const started = useRef(false);
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    pick();
  }, []);

  async function inspect(path) {
    const inspection = await request("/api/import/inspect", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path }),
    });
    const repository =
      repos.find((repo) => repo.id === selection.repository) || repos[0];
    const base = repository.id === selection.repository ? selection.parts : [];
    setFile({ path, ...inspection });
    setForm({
      repositoryId: repository.id,
      folder: [...base, inspection.name].join("/"),
      title: inspection.name,
      sourceLanguage: "fr",
      translationLanguage: "en",
      notesLanguage: "en",
      audioTrack: String(
        preferredTrack(inspection.audioTracks, "fr")?.index ?? "",
      ),
      rightToLeft: true,
    });
  }

  async function create() {
    setBusy(true);
    setError("");
    try {
      const { job } = await request("/api/import", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...form,
          path: file.path,
          audioTrack: file.kind === "video" ? Number(form.audioTrack) : null,
          rightToLeft: file.kind === "comic" ? form.rightToLeft : null,
        }),
      });
      const created = await importFinished(job);
      await onCreated(created, file.kind);
      onClose();
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  if (!file)
    return error ? (
      <Modal opened onClose={onClose} title="Add title" centered>
        <Stack>
          <Alert color="red" icon={<TriangleAlert size={16} />} role="alert">
            {error}
          </Alert>
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={pick}>Choose another file</Button>
          </Group>
        </Stack>
      </Modal>
    ) : null;
  return (
    <Modal
      opened
      onClose={() => !busy && onClose()}
      title="Add title"
      size="lg"
      centered
      closeOnEscape={!busy}
      closeOnClickOutside={!busy}
    >
      <Stack>
        <Group justify="space-between" wrap="nowrap">
          <Text size="sm" truncate>
            {file.path}
          </Text>
          <Button variant="subtle" size="xs" disabled={busy} onClick={pick}>
            Change
          </Button>
        </Group>
        <Select
          label="Repository"
          data={repos.map((repo) => ({ value: repo.id, label: repo.name }))}
          value={form.repositoryId}
          onChange={set("repositoryId")}
          allowDeselect={false}
          disabled={busy}
        />
        <TextInput
          label="Folder in the repository"
          description="Created if needed. Its last part becomes the document ID."
          value={form.folder}
          onChange={(event) => set("folder")(event.target.value)}
          disabled={busy}
        />
        <TextInput
          label="Title"
          value={form.title}
          onChange={(event) => set("title")(event.target.value)}
          disabled={busy}
        />
        <Group grow>
          <TextInput
            label="Source language"
            value={form.sourceLanguage}
            onChange={(event) => {
              const language = event.target.value;
              setForm((current) => ({
                ...current,
                sourceLanguage: language,
                audioTrack:
                  file.kind === "video"
                    ? String(
                        preferredTrack(file.audioTracks, language)?.index ?? "",
                      )
                    : current.audioTrack,
              }));
            }}
            disabled={busy}
          />
          <TextInput
            label="Translation"
            value={form.translationLanguage}
            onChange={(event) => set("translationLanguage")(event.target.value)}
            disabled={busy}
          />
          <TextInput
            label="Notes"
            value={form.notesLanguage}
            onChange={(event) => set("notesLanguage")(event.target.value)}
            disabled={busy}
          />
        </Group>
        {file.kind === "video" ? (
          <Select
            label="Audio track"
            description="The player keeps only this track."
            data={file.audioTracks.map((track) => ({
              value: String(track.index),
              label: trackLabel(track),
            }))}
            value={form.audioTrack}
            onChange={set("audioTrack")}
            allowDeselect={false}
            disabled={busy}
          />
        ) : (
          <div>
            <Text size="sm" fw={500} mb={4}>
              Reading direction · {file.pages} pages
            </Text>
            <SegmentedControl
              data={[
                { label: "Right to left (manga)", value: "rtl" },
                { label: "Left to right", value: "ltr" },
              ]}
              value={form.rightToLeft ? "rtl" : "ltr"}
              onChange={(value) => set("rightToLeft")(value === "rtl")}
              disabled={busy}
            />
          </div>
        )}
        {error && (
          <Alert color="red" icon={<TriangleAlert size={16} />} role="alert">
            {error}
          </Alert>
        )}
        <Group justify="space-between">
          <Text size="xs" c="dimmed">
            {busy && file.kind === "video"
              ? "Copying the video. A film can take several minutes."
              : ""}
          </Text>
          <Group gap="sm">
            <Button variant="default" disabled={busy} onClick={onClose}>
              Cancel
            </Button>
            <Button loading={busy} onClick={create}>
              Create title
            </Button>
          </Group>
        </Group>
      </Stack>
    </Modal>
  );
}
