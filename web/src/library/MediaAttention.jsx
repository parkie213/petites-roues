import { Badge, Group, Paper, Stack, Text, Title } from "@mantine/core";
import { BookOpen, Film, TriangleAlert } from "lucide-react";
import { clock } from "../player/quiz.js";

/**
 * Media found in the registered folders that needs the user: no study document references it, or
 * its audio cannot be played here. Each warning clears on the next Refresh once fixed (a document
 * with the file's fingerprint added to any folder, or the audio remuxed outside the app).
 */
export function MediaAttention({ repos }) {
  const items = repos.flatMap((repo) =>
    (repo.media ?? []).map((item) => ({ ...item, folder: repo.name })),
  );
  if (items.length === 0) return null;
  return (
    <Paper
      withBorder
      radius="lg"
      p="lg"
      aria-label="Media that needs attention"
    >
      <Stack gap="sm">
        <Group gap="xs">
          <TriangleAlert size={18} />
          <Title order={3} size="h5">
            Needs attention
          </Title>
          <Badge variant="light" color="yellow">
            {items.length}
          </Badge>
        </Group>
        <Text size="xs" c="dimmed">
          A video or comic links to a study document whose reference fingerprint
          matches it, in any folder. Add the document, or fix the audio with
          your own tools, then Refresh.
        </Text>
        <Stack gap={6}>
          {items.map((item) => {
            const name = item.relativePath.split("/").pop();
            const Icon = item.kind === "comic" ? BookOpen : Film;
            return (
              <Group
                key={`${item.repositoryId}/${item.relativePath}`}
                gap="sm"
                wrap="nowrap"
                align="flex-start"
              >
                <Icon size={16} style={{ flexShrink: 0, marginTop: 3 }} />
                <div style={{ minWidth: 0 }}>
                  <Text
                    size="sm"
                    fw={500}
                    truncate="end"
                    title={item.relativePath}
                  >
                    {name}
                  </Text>
                  <Text size="xs" c="dimmed" truncate="end">
                    {[
                      item.folder,
                      item.relativePath.includes("/") &&
                        item.relativePath.slice(
                          0,
                          item.relativePath.lastIndexOf("/"),
                        ),
                      item.durationSeconds != null &&
                        clock(item.durationSeconds),
                    ]
                      .filter(Boolean)
                      .join(" · ")}
                  </Text>
                  <Group gap={6} mt={4}>
                    {item.warnings.map((warning) => (
                      <Badge
                        key={warning}
                        size="sm"
                        variant="light"
                        color="yellow"
                      >
                        {warning}
                      </Badge>
                    ))}
                  </Group>
                </div>
              </Group>
            );
          })}
        </Stack>
      </Stack>
    </Paper>
  );
}
