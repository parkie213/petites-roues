import { useEffect, useRef } from "react";
import {
  ActionIcon,
  Anchor,
  Badge,
  Box,
  Breadcrumbs,
  Button,
  Group,
  Menu,
  NavLink,
  Paper,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from "@mantine/core";
import {
  BookOpen,
  ChevronRight,
  Film,
  Folder,
  FolderOpen,
  Library,
  MoreHorizontal,
  Pencil,
  RefreshCw,
  Trash2,
  TriangleAlert,
} from "lucide-react";
import { repositoryColumns } from "./repository-tree.js";
import { folderHref } from "./navigation.js";

function StudyTitle({ entry }) {
  const comic = entry.type === "comic";
  const available = comic ? entry.mediaAvailable : entry.videoAvailable;
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
            {entry.edition && (
              <Text size="xs" c="dimmed" mt={4}>
                {entry.edition}
              </Text>
            )}
          </div>
        </Group>
        {!available && (
          <Group gap={5}>
            <TriangleAlert size={13} color="var(--mantine-color-yellow-5)" />
            <Text c="dimmed" size="xs">
              {comic ? "CBZ is not present" : "Video is not present"}
            </Text>
          </Group>
        )}
        {entry.documentAvailable ? (
          <Group gap={6} wrap="nowrap">
            <Button
              component="a"
              variant="light"
              size="xs"
              fullWidth
              rightSection={<ChevronRight size={14} />}
              aria-label={`${comic ? "Read" : entry.videoAvailable ? "Play" : "Open"} ${entry.title}`}
              href={`/${comic ? "reader" : entry.videoAvailable ? "watch" : "player"}/${encodeURIComponent(entry.id)}/`}
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
                href={`/player/${encodeURIComponent(entry.id)}/`}
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
      </Stack>
    </Paper>
  );
}

const titleCount = (node) =>
  node.titles.length +
  node.folders.reduce((sum, child) => sum + titleCount(child), 0);

/** Renders indexed folders; Mantine owns navigation-row and menu interactions. */
export function LibraryBrowser({
  data,
  selection,
  navigate,
  busy,
  act,
  onRemove,
}) {
  const strip = useRef(null);
  const repo = data.repos.find((item) => item.id === selection.repository);
  const columns = repo ? repositoryColumns(repo.entries, selection.parts) : [];
  useEffect(() => {
    const browser = strip.current;
    browser?.scrollTo({ left: browser.scrollWidth, behavior: "smooth" });
  }, [selection.repository, selection.parts.join("/")]);
  function follow(event, repository, parts) {
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
            {displayedParts.map((part, index) => (
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
            act={act}
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
              description={`${item.entries.length} ${item.entries.length === 1 ? "title" : "titles"}`}
              active={repo?.id === item.id}
              aria-current={repo?.id === item.id ? "location" : undefined}
              leftSection={<Library size={18} />}
              rightSection={<ChevronRight size={14} />}
              onClick={(event) => follow(event, item.id, [])}
            />
          ))}
        </Box>
        {columns.map(({ node, parts }, index) => (
          <Box
            component="nav"
            className="library-browser-column"
            key={repo.id + "/" + parts.join("/")}
            aria-label={parts.at(-1) || repo.name}
            p="sm"
          >
            <Group justify="space-between" px="sm" py="xs" mb="xs">
              <Text size="xs" fw={600} c="dimmed" tt="uppercase">
                {parts.at(-1) || "Folders"}
              </Text>
              <Badge size="xs" color="gray" variant="light">
                {node.folders.length + node.titles.length}
              </Badge>
            </Group>
            {node.folders.map((folder) => {
              const selected = columns[index + 1]?.parts.at(-1) === folder.name;
              const count = titleCount(folder);
              return (
                <NavLink
                  key={folder.name}
                  href={folderHref(repo.id, [...parts, folder.name])}
                  label={folder.name}
                  description={`${count} ${count === 1 ? "title" : "titles"}`}
                  active={selected}
                  aria-current={selected ? "location" : undefined}
                  leftSection={
                    selected ? <FolderOpen size={18} /> : <Folder size={18} />
                  }
                  rightSection={<ChevronRight size={14} />}
                  onClick={(event) =>
                    follow(event, repo.id, [...parts, folder.name])
                  }
                  onKeyDown={(event) => {
                    if (
                      event.key === "ArrowRight" ||
                      event.key === "ArrowLeft"
                    ) {
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
            {node.titles.map((entry) => (
              <StudyTitle key={entry.id} entry={entry} />
            ))}
            {!node.folders.length && !node.titles.length && (
              <Text p="md" size="sm" c="dimmed">
                No indexed titles. Add content, then refresh this repository.
              </Text>
            )}
          </Box>
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
              Choose a repository to explore your titles.
            </Text>
          </Stack>
        )}
      </div>
    </Paper>
  );
}

function RepositoryMenu({ repo, busy, act, onRemove }) {
  return (
    <Menu shadow="md" width={260} position="bottom-end">
      <Menu.Target>
        <ActionIcon
          variant="default"
          size="lg"
          aria-label={"Options for " + repo.name}
        >
          <MoreHorizontal size={19} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label>Repository</Menu.Label>
        <Text
          size="xs"
          c="dimmed"
          px="sm"
          pb="sm"
          style={{ overflowWrap: "anywhere" }}
        >
          {repo.localPath}
        </Text>
        <Menu.Item
          disabled={busy}
          leftSection={<RefreshCw size={15} />}
          onClick={() =>
            act(
              "/api/repositories/" + repo.id + "/refresh",
              "POST",
              null,
              "Repository refreshed.",
            ).catch(() => {})
          }
        >
          Refresh repository
        </Menu.Item>
        <Menu.Divider />
        <Menu.Item
          disabled={busy}
          color="red"
          leftSection={<Trash2 size={15} />}
          onClick={() => onRemove(repo.id)}
        >
          Remove from library…
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}
