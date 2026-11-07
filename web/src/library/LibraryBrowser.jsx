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
} from "lucide-react";
import { repositoryColumns, repositoryTree } from "./repository-tree.js";
import { folderHref } from "./navigation.js";

function StudyTitle({ entry }) {
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

/** Where a video or CBZ opens: its title, else a video plays on its own. */
function mediaHref(item) {
  const comic = item.mediaKind === "comic";
  if (item.titleId)
    return `/${comic ? "reader" : "watch"}/${encodeURIComponent(item.titleId)}/`;
  return comic ? null : `/video/${encodeURIComponent(item.fingerprint)}/`;
}

/** A video or CBZ file in the folder, named by its file. */
function MediaFile({ item }) {
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
      </Stack>
    </Paper>
  );
}

const isMedia = (item) => item.kind === "media";

/** Study documents, videos and comics below a folder, for its row description. */
function counts(node) {
  const total = { documents: 0, videos: 0, comics: 0 };
  for (const item of node.titles) {
    if (!isMedia(item)) total.documents++;
    else if (item.mediaKind === "comic") total.comics++;
    else total.videos++;
  }
  for (const child of node.folders.map(counts))
    for (const key of Object.keys(total)) total[key] += child[key];
  return total;
}

const plural = (n, word) => `${n} ${word}${n === 1 ? "" : "s"}`;

function describe({ documents, videos, comics }) {
  return (
    [
      videos > 0 && plural(videos, "video"),
      comics > 0 && plural(comics, "comic"),
      documents > 0 && plural(documents, "study document"),
    ]
      .filter(Boolean)
      .join(" · ") || "Empty"
  );
}

/** Renders indexed folders; Mantine owns navigation-row and menu interactions. */
export function LibraryBrowser({
  data,
  selection,
  navigate,
  busy,
  refresh,
  onRemove,
}) {
  const strip = useRef(null);
  const repo = data.repos.find((item) => item.id === selection.repository);
  const columns = repo
    ? repositoryColumns(repo.entries, selection.parts, repo.media)
    : [];
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
              description={describe(
                counts(repositoryTree(item.entries, item.media)),
              )}
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
                {parts.at(-1) || repo.name}
              </Text>
              <Badge size="xs" color="gray" variant="light">
                {node.folders.length + node.titles.length}
              </Badge>
            </Group>
            {node.folders.map((folder) => {
              const selected = columns[index + 1]?.parts.at(-1) === folder.name;
              const count = describe(counts(folder));
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
            {node.titles.map((entry) =>
              isMedia(entry) ? (
                <MediaFile key={entry.id} item={entry} />
              ) : (
                <StudyTitle key={entry.id} entry={entry} />
              ),
            )}
            {!node.folders.length && !node.titles.length && (
              <Text p="md" size="sm" c="dimmed">
                Nothing found. Add videos, comics or study documents, then
                Refresh library.
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
              Choose a folder to explore your titles.
            </Text>
          </Stack>
        )}
      </div>
    </Paper>
  );
}

function RepositoryMenu({ repo, busy, refresh, onRemove }) {
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
        <Menu.Label>Folder</Menu.Label>
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
          onClick={() => refresh(repo.id)}
        >
          Refresh folder
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
