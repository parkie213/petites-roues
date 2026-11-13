import { appHref } from "../../shared/app-location.js";
import {
  ActionIcon,
  Button,
  Group,
  Paper,
  Stack,
  Text,
  ThemeIcon,
} from "@mantine/core";
import { BookOpen, ChevronRight, Film, Link2, Pencil } from "lucide-react";
import type { CatalogueItem } from "../../shared/types.ts";
import type { LinkActions } from "../browser.ts";
import { HandLinked } from "./HandLinked.tsx";

/** A study document's available reading, playback, editing and manual-link actions. */
export function StudyTitle({
  entry,
  onLink,
  onChanged,
}: LinkActions & { entry: CatalogueItem }) {
  const comic = entry.type === "comic";
  return (
    <Paper withBorder radius="md" p="md" my="xs">
      <Stack gap="sm">
        <Group gap="sm" wrap="nowrap" align="flex-start">
          <ThemeIcon variant="light" color="gray" size={34}>
            {comic ? <BookOpen size={18} /> : <Film size={18} />}
          </ThemeIcon>
          <div>
            <Text fw={600} size="sm" lh={1.4}>
              {entry.title}
            </Text>
          </div>
        </Group>
        {entry.documentAvailable ? (
          <Group gap={6} wrap="nowrap">
            <Button
              component="a"
              variant="light"
              size="xs"
              fullWidth
              rightSection={<ChevronRight size={14} />}
              aria-label={`${comic ? "Read" : entry.videoAvailable ? "Play" : "Open"} ${entry.title}`}
              href={appHref(
                `/${comic ? "reader" : entry.videoAvailable ? "watch" : "player"}/${encodeURIComponent(entry.id)}/`,
              )}
            >
              {comic
                ? "Read"
                : entry.videoAvailable
                  ? "Play"
                  : "Open study notes"}
            </Button>
            {!comic && entry.videoAvailable && (
              <ActionIcon
                component="a"
                variant="default"
                size={30}
                title="Edit"
                aria-label={`Edit ${entry.title}`}
                href={appHref(`/player/${encodeURIComponent(entry.id)}/`)}
              >
                <Pencil size={14} />
              </ActionIcon>
            )}
          </Group>
        ) : (
          <Text c="dimmed" size="xs">
            Document missing — refresh repository
          </Text>
        )}
        {entry.documentAvailable && !entry.mediaAvailable && (
          <Button
            variant="default"
            size="xs"
            fz="xs"
            leftSection={<Link2 size={14} />}
            onClick={() =>
              onLink({
                title: { id: entry.id, type: entry.type, name: entry.title },
              })
            }
          >
            Locate media…
          </Button>
        )}
        {entry.mediaLinkedByHand && (
          <HandLinked
            repositoryId={entry.mediaRepositoryId}
            relativePath={entry.mediaPath}
            onChanged={onChanged}
          />
        )}
      </Stack>
    </Paper>
  );
}
