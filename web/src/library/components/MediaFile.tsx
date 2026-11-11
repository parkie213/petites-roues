import { Button, Group, Paper, Stack, Text, ThemeIcon } from "@mantine/core";
import { BookOpen, ChevronRight, Film, Link2 } from "lucide-react";
import type { LinkActions } from "../browser.ts";
import type { RepositoryMedia } from "../repository-tree.ts";
import { HandLinked } from "./HandLinked.tsx";

/** Where a video or CBZ opens: its title, else a video plays on its own. */
function mediaHref(item: RepositoryMedia) {
  const comic = item.mediaKind === "comic";
  if (item.titleId)
    return `/${comic ? "reader" : "watch"}/${encodeURIComponent(item.titleId)}/`;
  return comic ? null : `/video/${encodeURIComponent(item.fingerprint)}/`;
}

/**
 * A video or CBZ file in the folder, named by its file. One that no document has can be linked to
 * one by hand; a hand-linked one can be unlinked.
 */
export function MediaFile({
  item,
  onLink,
  onChanged,
}: LinkActions & { item: RepositoryMedia }) {
  const comic = item.mediaKind === "comic";
  const href = mediaHref(item);
  return (
    <Paper withBorder radius="md" p="md" my="xs">
      <Stack gap="sm">
        <Group gap="sm" wrap="nowrap" align="flex-start">
          <ThemeIcon variant="light" color="gray" size={34}>
            {comic ? <BookOpen size={18} /> : <Film size={18} />}
          </ThemeIcon>
          <Text
            fw={600}
            size="sm"
            lh={1.4}
            style={{ overflowWrap: "anywhere" }}
          >
            {item.title}
          </Text>
        </Group>
        {href && (
          <Button
            component="a"
            variant="light"
            size="xs"
            fullWidth
            rightSection={<ChevronRight size={14} />}
            href={href}
          >
            {comic ? "Read" : "Play"}
          </Button>
        )}
        {!item.titleId && (
          <Button
            variant="default"
            size="xs"
            fz="xs"
            leftSection={<Link2 size={14} />}
            onClick={() =>
              onLink({
                media: {
                  repositoryId: item.repositoryId,
                  relativePath: item.relativePath,
                  kind: item.mediaKind,
                  name: item.title,
                },
              })
            }
          >
            Link to study document…
          </Button>
        )}
        {item.linkedByHand && (
          <HandLinked
            repositoryId={item.repositoryId}
            relativePath={item.relativePath}
            onChanged={onChanged}
          />
        )}
      </Stack>
    </Paper>
  );
}
