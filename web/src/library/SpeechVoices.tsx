import { useEffect, useRef, useState } from "react";
import {
  Anchor,
  Badge,
  Button,
  Group,
  Loader,
  Modal,
  NativeSelect,
  Stack,
  Text,
  TextInput,
  Tabs,
} from "@mantine/core";
import { request } from "../shared/api.ts";

interface VoiceLanguage {
  language: string;
  selectedVoice: string | null;
  voices: string[];
}
interface CatalogueVoice {
  id: string;
  name: string;
  language: string;
  region: string;
  quality: string;
  speakers: number;
  sizeBytes: number;
  modelCard: string;
  installed: boolean;
}
interface VoiceCatalogue {
  voices: CatalogueVoice[];
  fetchedAt: string;
  stale: boolean;
  warning: string | null;
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
  const [catalogue, setCatalogue] = useState<VoiceCatalogue | null>(null);
  const [tab, setTab] = useState<string | null>("installed");
  const [catalogueLoading, setCatalogueLoading] = useState(false);
  const [language, setLanguage] = useState("");
  const [search, setSearch] = useState("");
  const [installing, setInstalling] = useState<string | null>(null);
  const [previewText, setPreviewText] = useState("");
  const [audio, setAudio] = useState<string | null>(null);
  const player = useRef<HTMLAudioElement>(null);
  useEffect(() => {
    const element = player.current;
    return () => {
      element?.pause();
    };
  }, [opened]);
  const busy = saving || installing !== null;

  useEffect(() => {
    if (!opened) return;
    let live = true;
    setLanguages(null);
    setError("");
    request<VoiceLanguage[]>("/api/speech/voices").then(
      (next) => {
        if (live) {
          setLanguages(next);
          setLanguage(next[0]?.language || "");
        }
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

  async function browse(refresh = false) {
    setCatalogueLoading(true);
    setError("");
    try {
      setCatalogue(
        await request<VoiceCatalogue>(
          `/api/speech/voices/catalogue${refresh ? "?refresh=true" : ""}`,
        ),
      );
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setCatalogueLoading(false);
    }
  }

  async function install(id: string) {
    setInstalling(id);
    setError("");
    try {
      setLanguages(
        await request<VoiceLanguage[]>(
          `/api/speech/voices/catalogue/${id}/install`,
          { method: "POST" },
        ),
      );
      setCatalogue((current) =>
        current
          ? {
              ...current,
              voices: current.voices.map((voice) =>
                voice.id === id ? { ...voice, installed: true } : voice,
              ),
            }
          : null,
      );
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setInstalling(null);
    }
  }

  function play(url: string, label: string) {
    setError("");
    setAudio(label);
    const element = player.current;
    if (!element) return;
    element.pause();
    element.src = url;
    // Call play directly in the click handler to preserve the browser's user gesture.
    void element.play().catch((failure: Error) => {
      if (failure.name !== "AbortError" && element.getAttribute("src") === url)
        setError(failure.message);
    });
  }

  const catalogueLanguages = [
    ...new Set(catalogue?.voices.map((voice) => voice.language)),
  ].sort();
  const visible = catalogue?.voices.filter(
    (voice) =>
      (!language || voice.language === language) &&
      `${voice.name} ${voice.region} ${voice.quality}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );

  return (
    <Modal
      opened={opened}
      onClose={() => {
        if (!busy) {
          player.current?.pause();
          setAudio(null);
          setTab("installed");
          onClose();
        }
      }}
      title="Read-aloud voices"
      size="lg"
      closeButtonProps={{ disabled: busy }}
      closeOnEscape={!busy}
      closeOnClickOutside={!busy}
    >
      <Stack>
        <Text size="sm" c="dimmed">
          Choose an installed voice, or find a new voice and listen before
          downloading.
        </Text>
        {error && (
          <Text role="alert" c="red">
            {error}
          </Text>
        )}
        {!languages && !error && (
          <Loader size="sm" aria-label="Loading voices" />
        )}
        {audio && <Text size="sm">{audio}</Text>}
        <audio
          ref={player}
          aria-label="Voice comparison"
          controls
          hidden={!audio}
          onError={() =>
            setError(
              "Could not play this voice sample. Try again or choose another voice.",
            )
          }
        />
        <Tabs
          value={tab}
          color="navy"
          keepMounted={false}
          onChange={(value) => {
            setTab(value);
            if (value === "find" && !catalogueLoading) void browse();
          }}
        >
          <Tabs.List mb="md">
            <Tabs.Tab value="installed" disabled={busy}>
              Installed
            </Tabs.Tab>
            <Tabs.Tab value="find" disabled={busy}>
              Find voices
            </Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="installed">
            <Stack>
              {languages?.length === 0 && (
                <Text>
                  No voices installed. Open Find voices to hear samples and
                  install one.
                </Text>
              )}
              {!!languages?.length && (
                <TextInput
                  label="Text for installed voice previews"
                  placeholder="Enter a sentence in the voice’s language"
                  value={previewText}
                  onChange={(event) =>
                    setPreviewText(event.currentTarget.value)
                  }
                  maxLength={1000}
                />
              )}
              {languages?.map(({ language, selectedVoice, voices }) => (
                <Group key={language} align="end" grow>
                  <NativeSelect
                    key={language}
                    label={names.of(language) || language}
                    value={selectedVoice || ""}
                    disabled={busy}
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
                      ...voices.map((voice) => ({
                        value: voice,
                        label: voice,
                      })),
                    ]}
                    onChange={(event) => {
                      void select(language, event.currentTarget.value);
                    }}
                  />
                  <Button
                    color="navy"
                    variant="light"
                    disabled={
                      busy ||
                      !previewText.trim() ||
                      !!(selectedVoice && !voices.includes(selectedVoice))
                    }
                    onClick={() =>
                      play(
                        `/api/speech/voices/${language}/preview?text=${encodeURIComponent(previewText)}${selectedVoice ? `&voice=${encodeURIComponent(selectedVoice)}` : ""}`,
                        `Preview: ${selectedVoice || voices[0]}`,
                      )
                    }
                  >
                    Preview {names.of(language) || language}
                  </Button>
                </Group>
              ))}
            </Stack>
          </Tabs.Panel>
          <Tabs.Panel value="find">
            <Stack>
              <Text size="sm" c="dimmed">
                Listen to samples, then install a voice to use it on this
                desktop.
              </Text>
              {catalogueLoading && (
                <Loader size="sm" aria-label="Loading catalogue" />
              )}
              <Group justify="space-between">
                <Text size="xs" c="dimmed">
                  {catalogue
                    ? `${visible?.length || 0} ${visible?.length === 1 ? "voice" : "voices"}`
                    : "Piper voice catalogue"}
                </Text>
                <Button
                  variant="subtle"
                  color="gray"
                  size="compact-xs"
                  onClick={() => {
                    void browse(true);
                  }}
                  loading={catalogueLoading}
                  disabled={busy}
                >
                  Refresh
                </Button>
              </Group>
              {catalogue && (
                <Stack>
                  <Text size="sm" c="dimmed">
                    Samples use the first speaker. Each model card describes the
                    voice and its license.
                  </Text>
                  <Text size="xs" c="dimmed" title={catalogue.fetchedAt}>
                    Catalogue fetched{" "}
                    {new Date(catalogue.fetchedAt).toLocaleString(undefined, {
                      dateStyle: "medium",
                      timeStyle: "short",
                    })}
                    {catalogue.stale ? " · Over an hour old" : ""}
                  </Text>
                  {catalogue.warning && (
                    <Text role="status" size="sm" c="orange.8">
                      {catalogue.warning}
                    </Text>
                  )}
                  <NativeSelect
                    label="Catalogue language"
                    value={language}
                    onChange={(event) => setLanguage(event.currentTarget.value)}
                    data={[
                      { value: "", label: "All languages" },
                      ...catalogueLanguages.map((tag) => ({
                        value: tag,
                        label: names.of(tag) || tag,
                      })),
                    ]}
                  />
                  <TextInput
                    label="Search voices"
                    placeholder="Name, region or quality"
                    value={search}
                    onChange={(event) => setSearch(event.currentTarget.value)}
                  />
                  {catalogue.voices.some((voice) => voice.installed) && (
                    <TextInput
                      label="Text for installed voice previews"
                      placeholder="Enter a sentence in the voice’s language"
                      value={previewText}
                      onChange={(event) =>
                        setPreviewText(event.currentTarget.value)
                      }
                      maxLength={1000}
                    />
                  )}
                  {visible?.length === 0 && <Text>No matching voices.</Text>}
                  {visible?.map((voice) => (
                    <Stack
                      component="section"
                      aria-label={`${voice.name} ${voice.region} ${voice.quality}`}
                      key={voice.id}
                      gap="xs"
                      p="sm"
                      style={{
                        borderBottom: "1px solid var(--mantine-color-gray-3)",
                      }}
                    >
                      <Group justify="space-between">
                        <Text fw={600}>
                          {voice.name} · {voice.region} · {voice.quality}
                        </Text>
                        {voice.installed && (
                          <Badge color="navy">Installed</Badge>
                        )}
                      </Group>
                      <Text size="sm" c="dimmed">
                        {(voice.sizeBytes / 1024 / 1024).toFixed(1)} MB ·{" "}
                        {voice.speakers}{" "}
                        {voice.speakers === 1 ? "speaker" : "speakers"} ·{" "}
                        <Anchor
                          color="navy"
                          href={voice.modelCard}
                          target="_blank"
                          rel="noreferrer"
                        >
                          Model card & license
                        </Anchor>
                      </Text>
                      <Group>
                        <Button
                          color="navy"
                          size="xs"
                          variant="light"
                          onClick={() =>
                            play(
                              `/api/speech/voices/catalogue/${voice.id}/sample`,
                              `Sample: ${voice.name} · ${voice.region} · ${voice.quality}`,
                            )
                          }
                        >
                          Listen to {voice.name}
                        </Button>
                        {voice.installed && (
                          <Button
                            color="navy"
                            size="xs"
                            variant="light"
                            disabled={busy || !previewText.trim()}
                            onClick={() =>
                              play(
                                `/api/speech/voices/${voice.language}/preview?text=${encodeURIComponent(previewText)}&voice=${encodeURIComponent(voice.id + ".onnx")}`,
                                `Preview: ${voice.name} · ${voice.region} · ${voice.quality}`,
                              )
                            }
                          >
                            Preview {voice.name}
                          </Button>
                        )}
                        {!voice.installed && (
                          <Button
                            color="navy"
                            size="xs"
                            loading={installing === voice.id}
                            disabled={busy}
                            onClick={() => {
                              void install(voice.id);
                            }}
                          >
                            Install {voice.name}
                          </Button>
                        )}
                      </Group>
                    </Stack>
                  ))}
                  {installing && (
                    <Text role="status">
                      Downloading and checking the voice… Keep this dialog open
                      until it finishes.
                    </Text>
                  )}
                </Stack>
              )}
            </Stack>
          </Tabs.Panel>
        </Tabs>
      </Stack>
    </Modal>
  );
}
