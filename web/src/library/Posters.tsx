import { useState } from "react";
import { Chip, Group, Stack, Text, Title } from "@mantine/core";
import { LinkDialog } from "./LinkDialog.jsx";
import { postersHref, type Selection } from "./navigation.ts";
import type { LinkActions, LinkRequest } from "./browser.ts";
import type { LibraryData } from "./useLibraryData.ts";
import type { PosterFilters } from "./components/LibraryToolbar.tsx";
import {
  SeriesView,
  groupCard,
  type FollowPoster,
} from "./components/PosterSeries.tsx";
import { usePosterShelves } from "./usePosterShelves.ts";
import { filterShelves } from "./poster-filter.ts";
import "./Posters.css";

interface PostersProps {
  data: LibraryData;
  language: string | null;
  selection: Selection;
  go: (href: string) => void;
  onChanged: LinkActions["onChanged"];
  search: string;
  mediaFilter: PosterFilters["media"];
  attentionOnly: boolean;
}

/**
 * Plex-like browsing of what can be watched or read: titles with their media and media awaiting a
 * study document; documents whose media was not found are listed in the Documents view. The
 * server groups them into shelves and cards (GET /api/library/posters, the rules shared with the
 * Android app), narrowed to the chosen folder chip and [language]; this only renders them. They are
 * fetched again whenever [data], the loaded library, changes. [onChanged] reloads the library after
 * a card action, receiving the error when the action failed. Media without a study document can be
 * linked to one by hand from its card.
 */
export function Posters({
  data,
  language,
  selection,
  go,
  onChanged,
  search,
  mediaFilter,
  attentionOnly,
}: PostersProps) {
  const { posters, error } = usePosterShelves(
    data,
    selection.repository,
    language,
  );
  const [linking, setLinking] = useState<LinkRequest | null>(null);
  const actions = { onChanged, onLink: setLinking };
  if (!posters)
    return error ? (
      <Text c="red" role="alert">
        {error}
      </Text>
    ) : null;
  const repositoryNames = new Map(
    data.repos.map((repo) => [repo.id, repo.name]),
  );
  // Only folders that hold something to show get a chip.
  const folders = data.repos.filter((repo) =>
    posters.folders.includes(repo.id),
  );
  const filter = folders.some((repo) => repo.id === selection.repository)
    ? selection.repository
    : null;
  const result = filterShelves(posters.shelves, {
    search,
    mediaFilter,
    attentionOnly,
  });
  // Cards name their language only when the posters show more than one.
  const tagged = posters.multilingual;
  const follow: FollowPoster = (event, href) => {
    if (
      event &&
      (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey)
    )
      return;
    event?.preventDefault();
    go(href);
  };
  const open = selection.group
    ? posters.shelves
        .flatMap((shelf) => shelf.groups)
        .find((g) => g.key === selection.group)
    : null;
  const shown = { ...selection, repository: filter };

  return (
    <Stack gap="xl">
      {folders.length > 1 && (
        <Chip.Group
          value={filter ?? ""}
          onChange={(value) =>
            go(
              postersHref({
                repository: value || null,
                group: selection.group,
              }),
            )
          }
        >
          <Group gap="xs">
            <Chip value="" size="xs" variant="light">
              All folders
            </Chip>
            {folders.map((repo) => (
              <Chip key={repo.id} value={repo.id} size="xs" variant="light">
                {repo.name}
              </Chip>
            ))}
          </Group>
        </Chip.Group>
      )}
      {open ? (
        <SeriesView
          group={open}
          selection={shown}
          follow={follow}
          repositoryNames={repositoryNames}
          onChanged={onChanged}
          onLink={setLinking}
        />
      ) : (
        result
          .filter((shelf) => shelf.groups.length > 0)
          .map((shelf) => (
            <Stack key={shelf.key} gap="sm" component="section">
              <Group gap="xs" align="baseline">
                <Title order={3}>{shelf.label}</Title>
                <Text size="sm" c="dimmed">
                  {shelf.groups.length}
                </Text>
              </Group>
              <div
                className="poster-grid"
                onClick={(event) => {
                  const link = (
                    event.target as Element
                  ).closest<HTMLAnchorElement>("a[href^='/?']");
                  if (link) follow(event, link.getAttribute("href")!);
                }}
              >
                {shelf.groups.map((group) =>
                  groupCard(group, shown, repositoryNames, actions, tagged),
                )}
              </div>
            </Stack>
          ))
      )}
      {!open && result.length === 0 && (
        <Text c="dimmed">
          {filter
            ? "Nothing in this folder yet. Refresh after adding videos, comics or study documents."
            : "No titles match these filters."}
        </Text>
      )}
      <LinkDialog
        link={linking}
        repos={data.repos}
        onClose={() => setLinking(null)}
        onDone={onChanged}
      />
    </Stack>
  );
}
