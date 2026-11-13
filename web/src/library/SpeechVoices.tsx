import { useEffect, useState } from "react";
import { Loader, Modal, NativeSelect, Stack, Text } from "@mantine/core";
import { request } from "../shared/api.ts";

interface VoiceLanguage {
  language: string;
  selectedVoice: string | null;
  voices: string[];
}
const names = new Intl.DisplayNames([navigator.language], { type: "language" });

/** Lists installed voices and saves source-language choices through Java into SQLite. */
export function SpeechVoices({
  opened,
  onClose,
}: {
  opened: boolean;
  onClose: () => void;
}) {
  const [languages, setLanguages] = useState<VoiceLanguage[] | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (!opened) return;
    let live = true;
    setLanguages(null);
    setError("");
    request<VoiceLanguage[]>("/api/speech/voices").then(
      (next) => {
        if (live) setLanguages(next);
      },
      (failure: Error) => {
        if (live) setError(failure.message);
      },
    );
    return () => {
      live = false;
    };
  }, [opened]);

  async function select(language: string, voice: string) {
    setSaving(true);
    setError("");
    try {
      setLanguages(
        await request<VoiceLanguage[]>(`/api/speech/voices/${language}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ voice: voice || null }),
        }),
      );
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      opened={opened}
      onClose={() => {
        if (!saving) onClose();
      }}
      title="Read-aloud voices"
    >
      <Stack>
        <Text size="sm" c="dimmed">
          Choose a voice for each source language. Choices are saved on this
          desktop.
        </Text>
        {error && (
          <Text role="alert" c="red">
            {error}
          </Text>
        )}
        {!languages && !error && (
          <Loader size="sm" aria-label="Loading voices" />
        )}
        {languages?.length === 0 && (
          <Text>
            No voices installed. Install a Piper model and its configuration in
            the configured voices folder, then reopen this dialog.
          </Text>
        )}
        {languages?.map(({ language, selectedVoice, voices }) => (
          <NativeSelect
            key={language}
            label={names.of(language) || language}
            value={selectedVoice || ""}
            disabled={saving}
            error={
              selectedVoice && !voices.includes(selectedVoice)
                ? "The selected voice is no longer installed."
                : undefined
            }
            data={[
              { value: "", label: `Automatic (${voices[0]})` },
              ...(selectedVoice && !voices.includes(selectedVoice)
                ? [
                    {
                      value: selectedVoice,
                      label: `${selectedVoice} (unavailable)`,
                    },
                  ]
                : []),
              ...voices.map((voice) => ({ value: voice, label: voice })),
            ]}
            onChange={(event) => {
              void select(language, event.currentTarget.value);
            }}
          />
        ))}
      </Stack>
    </Modal>
  );
}
