import { Button, Group, Text } from "@mantine/core";
import type { LinkActions } from "../browser.ts";
import { unlinkMedia } from "../media-links.js";

/** "Linked by hand" with its Unlink action, for media linked to a document by hand. */
export function HandLinked({
  repositoryId,
  relativePath,
  onChanged,
}: {
  repositoryId: string | null | undefined;
  relativePath: string | null | undefined;
  onChanged: LinkActions["onChanged"];
}) {
  return (
    <Group gap={6} wrap="nowrap">
      <Text size="xs" c="dimmed">
        Linked by hand
      </Text>
      <Button
        variant="subtle"
        size="compact-xs"
        fz="xs"
        onClick={() =>
          unlinkMedia({ repositoryId, relativePath }).then(
            () => onChanged(),
            (error) => onChanged(error),
          )
        }
      >
        Unlink
      </Button>
    </Group>
  );
}
