import { Button, Group, Stack, Text, Textarea } from "@mantine/core";

/** Presents a correction suggestion; the editing hook owns submission and errors. */
export function CorrectionSuggestion({
  suggestion,
  setSuggestion,
  busy,
  onSubmit,
}) {
  return (
    <Stack
      component="form"
      onSubmit={onSubmit}
      className="correction-suggestion"
      gap="xs"
    >
      <Group gap="md" wrap="nowrap">
        <Text fw={600} size="sm">
          Suggest a correction
        </Text>
        <Text size="xs" c="dimmed" truncate title={suggestion.sourceText}>
          Current subtitle: {suggestion.sourceText}
        </Text>
      </Group>
      <Textarea
        autoFocus
        required
        maxLength={2000}
        rows={2}
        label="Your suggestion"
        value={suggestion.text}
        placeholder="What do you hear, or what needs checking?"
        onChange={(event) =>
          setSuggestion({ ...suggestion, text: event.target.value })
        }
      />
      <Group justify="flex-end" gap="xs">
        <Button
          size="xs"
          disabled={busy || !suggestion.text.trim()}
          loading={busy}
          type="submit"
        >
          Submit for review
        </Button>
        <Button
          size="xs"
          variant="default"
          disabled={busy}
          onClick={() => setSuggestion(null)}
        >
          Cancel
        </Button>
      </Group>
    </Stack>
  );
}
