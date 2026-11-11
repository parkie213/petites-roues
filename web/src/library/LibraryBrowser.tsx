import { useEffect, useRef, useState, type MouseEvent } from "react";
import {
  Anchor,
  Box,
  Breadcrumbs,
  Group,
  NavLink,
  Paper,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from "@mantine/core";
import { ChevronRight, FolderOpen, Library } from "lucide-react";
import {
  describeFolder,
  repositoryColumns,
  repositoryTree,
} from "./repository-tree.ts";
import { folderHref, type Selection } from "./navigation.ts";
import type { LibraryData } from "./useLibraryData.ts";
import type { LinkActions, LinkRequest, NavigateFolder } from "./browser.ts";
import { LinkDialog } from "./LinkDialog.jsx";
import { FolderColumn } from "./components/FolderColumn.tsx";
import { RepositoryMenu } from "./components/RepositoryMenu.tsx";

/**
 * Renders indexed folders; Mantine owns navigation-row and menu interactions. Media and documents
 * can be linked by hand here as in Posters and Documents; [onChanged] reloads the library after,
 * receiving the error when it failed.
 */
export function LibraryBrowser({
  data,
  selection,
  navigate,
  busy,
  refresh,
  onRemove,
  onChanged,
}: {
  data: LibraryData;
  selection: Pick<Selection, "repository" | "parts">;
  navigate: NavigateFolder;
  busy: boolean;
  refresh: (id: string) => void;
  onRemove: (id: string) => void;
  onChanged: LinkActions["onChanged"];
}) {
  const strip = useRef<HTMLDivElement>(null);
  const [linking, setLinking] = useState<LinkRequest | null>(null);
  const repo = data.repos.find((item) => item.id === selection.repository);
  const columns = repo
    ? repositoryColumns(repo.entries, selection.parts, repo.media)
    : [];
  const path = selection.parts.join("/");
  useEffect(() => {
    const browser = strip.current;
    browser?.scrollTo({ left: browser.scrollWidth, behavior: "smooth" });
  }, [selection.repository, path]);
  function follow(
    event: MouseEvent<HTMLElement>,
    repository: string,
    parts: string[],
  ) {
    if (
      event.button ||
      event.ctrlKey ||
      event.metaKey ||
      event.shiftKey ||
      event.altKey
    )
      return;
    event.preventDefault();
    navigate(repository, parts);
  }
  const displayedParts = columns.at(-1)?.parts || [];
  return (
    <Paper
      withBorder
      radius="lg"
      component="section"
      aria-label="Library browser"
      className="library-browser-shell"
    >
      <Group justify="space-between" p="lg" wrap="nowrap">
        <Stack gap={5} style={{ minWidth: 0 }}>
          <Title order={3} size="md">
            {repo?.name || "Your repositories"}
          </Title>
          <Breadcrumbs
            separator={<ChevronRight size={12} />}
            styles={{ root: { flexWrap: "wrap" } }}
          >
            <Anchor size="xs" c="dimmed" href="/">
              Library
            </Anchor>
            {repo && (
              <Anchor
                size="xs"
                href={folderHref(repo.id)}
                onClick={(event) => follow(event, repo.id, [])}
              >
                {repo.name}
              </Anchor>
            )}
            {repo &&
              displayedParts.map((part, index) => (
                <Anchor
                  key={index}
                  size="xs"
                  href={folderHref(repo.id, displayedParts.slice(0, index + 1))}
                  onClick={(event) =>
                    follow(event, repo.id, displayedParts.slice(0, index + 1))
                  }
                >
                  {part}
                </Anchor>
              ))}
          </Breadcrumbs>
        </Stack>
        {repo && (
          <RepositoryMenu
            repo={repo}
            busy={busy}
            refresh={refresh}
            onRemove={onRemove}
          />
        )}
      </Group>
      <div className="library-browser-columns" ref={strip}>
        <Box
          component="nav"
          className="library-browser-column"
          aria-label="Repositories"
          p="sm"
        >
          <Text
            size="xs"
            fw={600}
            c="dimmed"
            tt="uppercase"
            px="sm"
            py="xs"
            mb="xs"
          >
            Repositories
          </Text>
          {data.repos.map((item) => (
            <NavLink
              key={item.id}
              href={folderHref(item.id)}
              label={item.name}
              description={describeFolder(
                repositoryTree(item.entries, item.media),
              )}
              active={repo?.id === item.id}
              aria-current={repo?.id === item.id ? "location" : undefined}
              leftSection={<Library size={18} />}
              rightSection={<ChevronRight size={14} />}
              onClick={(event) => follow(event, item.id, [])}
            />
          ))}
        </Box>
        {repo &&
          columns.map(({ node, parts }, index) => (
            <FolderColumn
              key={repo.id + "/" + parts.join("/")}
              repo={repo}
              node={node}
              parts={parts}
              nextParts={columns[index + 1]?.parts}
              follow={follow}
              navigate={navigate}
              onLink={setLinking}
              onChanged={onChanged}
            />
          ))}
        {!repo && (
          <Stack
            className="library-browser-placeholder"
            align="center"
            justify="center"
            gap="md"
            p="xl"
          >
            <ThemeIcon size={56} radius="xl" variant="light" color="gray">
              <FolderOpen size={28} />
            </ThemeIcon>
            <Text c="dimmed" size="sm" ta="center" maw={260}>
              Choose a folder to explore your titles.
            </Text>
          </Stack>
        )}
      </div>
      <LinkDialog
        link={linking}
        repos={data.repos}
        onClose={() => setLinking(null)}
        onDone={onChanged}
      />
    </Paper>
  );
}
