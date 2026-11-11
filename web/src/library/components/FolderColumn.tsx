import { Badge, Box, Group, NavLink, Text } from "@mantine/core";
import { ChevronRight, Folder, FolderOpen } from "lucide-react";
import type { MouseEvent } from "react";
import type { LinkActions, NavigateFolder } from "../browser.ts";
import { folderHref } from "../navigation.ts";
import {
  describeFolder,
  isMedia,
  type RepositoryColumn,
} from "../repository-tree.ts";
import { MediaFile } from "./MediaFile.tsx";
import { StudyTitle } from "./StudyTitle.tsx";

/** One indexed folder, its child navigation and cards; the browser owns the selected path. */
export function FolderColumn({
  node,
  parts,
  repo,
  nextParts,
  follow,
  navigate,
  onLink,
  onChanged,
}: RepositoryColumn &
  LinkActions & {
    repo: { id: string; name: string };
    nextParts?: string[];
    follow: (
      event: MouseEvent<HTMLElement>,
      repository: string,
      parts: string[],
    ) => void;
    navigate: NavigateFolder;
  }) {
  return (
    <Box
      component="nav"
      className="library-browser-column"
      aria-label={parts.at(-1) || repo.name}
      p="sm"
    >
      <Group justify="space-between" px="sm" py="xs" mb="xs">
        <Text size="xs" fw={600} c="dimmed" tt="uppercase">
          {parts.at(-1) || repo.name}
        </Text>
        <Badge size="xs" color="gray" variant="light">
          {node.folders.length + node.titles.length}
        </Badge>
      </Group>
      {node.folders.map((folder) => {
        const selected = nextParts?.at(-1) === folder.name;
        const count = describeFolder(folder);
        return (
          <NavLink
            key={folder.name}
            href={folderHref(repo.id, [...parts, folder.name])}
            label={folder.name}
            description={count}
            active={selected}
            aria-current={selected ? "location" : undefined}
            leftSection={
              selected ? <FolderOpen size={18} /> : <Folder size={18} />
            }
            rightSection={<ChevronRight size={14} />}
            onClick={(event) => follow(event, repo.id, [...parts, folder.name])}
            onKeyDown={(event) => {
              if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
                event.preventDefault();
                navigate(
                  repo.id,
                  event.key === "ArrowRight"
                    ? [...parts, folder.name]
                    : parts.slice(0, -1),
                );
              }
            }}
          />
        );
      })}
      {node.titles.map((entry) =>
        isMedia(entry) ? (
          <MediaFile
            key={entry.id}
            item={entry}
            onLink={onLink}
            onChanged={onChanged}
          />
        ) : (
          <StudyTitle
            key={entry.id}
            entry={entry}
            onLink={onLink}
            onChanged={onChanged}
          />
        ),
      )}
      {!node.folders.length && !node.titles.length && (
        <Text p="md" size="sm" c="dimmed">
          Nothing found. Add videos, comics or study documents, then Refresh
          library.
        </Text>
      )}
    </Box>
  );
}
