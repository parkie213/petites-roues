import { Paper, Progress, Stack, Text } from "@mantine/core";
import { describeJob } from "./library-job.js";

/** A running Add or Refresh: what it is doing, how far through, and the file it is on. */
export function JobProgress({ job }) {
  const { label, detail, value } = describeJob(job);
  return (
    <Paper withBorder p="md" radius="md" role="status" aria-live="polite">
      <Stack gap={8}>
        <Text size="sm" fw={600}>
          {label}
        </Text>
        <Progress
          value={value ?? 100}
          striped={value == null}
          animated={value == null}
          aria-label={label}
        />
        {detail && (
          <Text size="xs" c="dimmed" truncate="start" title={detail}>
            {detail}
          </Text>
        )}
      </Stack>
    </Paper>
  );
}
