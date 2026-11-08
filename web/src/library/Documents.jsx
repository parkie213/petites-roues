import { useState } from "react";
import { Anchor, Button, Group, Stack, Table, Text } from "@mantine/core";
import { TriangleAlert } from "lucide-react";
import { openHref } from "./cards.js";
import { LinkDialog } from "./LinkDialog.jsx";
import { unlinkMedia } from "./media-links.js";

const collator = new Intl.Collator(undefined, { numeric: true });

function where(entry) {
  return [
    entry.series,
    entry.season != null && `S${entry.season}`,
    entry.episode != null && `E${entry.episode}`,
    entry.volume != null && `Tome ${entry.volume}`,
  ]
    .filter(Boolean)
    .join(" · ");
}

/** A comic needs its CBZ to open; a video document opens as study notes without its video. */
const href = (entry) =>
  entry.type === "comic" && !entry.mediaAvailable ? null : openHref(entry);

/**
 * Every study document in the library and what it is linked to. Posters show only media, so a
 * document whose video or CBZ was not found appears here, first, with the reason, and can be
 * linked to one by hand. [onChanged] reloads the library after linking or unlinking, receiving the
 * error when it failed.
 */
export function Documents({ data, onChanged }) {
  const [linking, setLinking] = useState(null);
  const names = new Map(data.repos.map((repo) => [repo.id, repo.name]));
  const entries = data.repos
    .flatMap((repo) => repo.entries)
    .sort(
      (a, b) =>
        Number(a.mediaAvailable) - Number(b.mediaAvailable) ||
        collator.compare(a.series ?? a.title, b.series ?? b.title) ||
        (a.season ?? 0) - (b.season ?? 0) ||
        (a.episode ?? a.volume ?? 0) - (b.episode ?? b.volume ?? 0) ||
        collator.compare(a.title, b.title),
    );
  if (entries.length === 0)
    return (
      <Text c="dimmed">
        No study documents yet. Add a folder that holds them, then Refresh.
      </Text>
    );
  const what = (entry) => (entry.type === "comic" ? "CBZ" : "video");
  const unlink = (entry) =>
    unlinkMedia({
      repositoryId: entry.mediaRepositoryId,
      relativePath: entry.mediaPath,
    }).then(
      () => onChanged(),
      (error) => onChanged(error),
    );
  return (
    <Table.ScrollContainer minWidth={640}>
      <Table verticalSpacing="sm" highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Title</Table.Th>
            <Table.Th>Document</Table.Th>
            <Table.Th>Media</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {entries.map((entry) => {
            const link = href(entry);
            const problems = Object.values(entry.unavailable ?? {});
            return (
              <Table.Tr key={entry.id}>
                <Table.Td>
                  <Stack gap={0}>
                    {link ? (
                      <Anchor href={link} size="sm" fw={600}>
                        {entry.title}
                      </Anchor>
                    ) : (
                      <Text size="sm" fw={600}>
                        {entry.title}
                      </Text>
                    )}
                    <Text size="xs" c="dimmed">
                      {where(entry)}
                    </Text>
                  </Stack>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{entry.repositoryName}</Text>
                  <Text
                    size="xs"
                    c="dimmed"
                    style={{ overflowWrap: "anywhere" }}
                  >
                    {entry.relativePath}
                  </Text>
                </Table.Td>
                <Table.Td>
                  {entry.mediaAvailable && (
                    <>
                      <Text size="sm">
                        {names.get(entry.mediaRepositoryId)}
                      </Text>
                      <Text
                        size="xs"
                        c="dimmed"
                        style={{ overflowWrap: "anywhere" }}
                      >
                        {entry.mediaPath}
                      </Text>
                    </>
                  )}
                  {problems.map((problem) => (
                    <Group
                      key={problem}
                      gap={5}
                      wrap="nowrap"
                      align="flex-start"
                    >
                      <TriangleAlert
                        size={13}
                        color="var(--mantine-color-yellow-5)"
                        style={{ flexShrink: 0, marginTop: 3 }}
                      />
                      <Text size="xs" c="yellow.5">
                        {problem}
                      </Text>
                    </Group>
                  ))}
                  {entry.mediaLinkedByHand && (
                    <Group gap={6} wrap="nowrap">
                      <Text size="xs" c="dimmed">
                        Linked by hand
                      </Text>
                      <Button
                        variant="subtle"
                        size="compact-xs"
                        onClick={() => unlink(entry)}
                      >
                        Unlink
                      </Button>
                    </Group>
                  )}
                  {!entry.mediaAvailable &&
                    entry.documentAvailable &&
                    entry.repositoryId && (
                      <Button
                        variant="light"
                        size="compact-xs"
                        mt={4}
                        onClick={() =>
                          setLinking({
                            title: {
                              id: entry.id,
                              type: entry.type,
                              name: entry.title,
                            },
                          })
                        }
                      >
                        Locate {what(entry)}…
                      </Button>
                    )}
                </Table.Td>
              </Table.Tr>
            );
          })}
        </Table.Tbody>
      </Table>
      <LinkDialog
        link={linking}
        repos={data.repos}
        onClose={() => setLinking(null)}
        onDone={onChanged}
      />
    </Table.ScrollContainer>
  );
}
