import { useState } from "react";
import {
  ActionIcon,
  Anchor,
  Breadcrumbs,
  Chip,
  Group,
  Menu,
  SegmentedControl,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { BookOpen, Copy, Ellipsis, Film, TriangleAlert } from "lucide-react";
import { clock } from "../player/quiz.js";
import { responseError, titlePath } from "../shared/api.js";
import { openHref, shelves } from "./shelves.js";
import { postersHref } from "./navigation.js";
import "./Posters.css";

const artUrl = (art) =>
  art &&
  `/api/titles/${encodeURIComponent(art.id)}/${art.kind === "series" ? "series-card" : "card"}`;

/** A stable hue per name, so placeholders are distinguishable but never random. */
function hue(name) {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.codePointAt(0)) >>> 0;
  return hash % 360;
}

function Art({ art, name, type }) {
  const url = artUrl(art);
  const [failed, setFailed] = useState(null);
  if (url && failed !== url)
    return (
      <img
        className="poster-art"
        src={url}
        alt=""
        loading="lazy"
        onError={() => setFailed(url)}
      />
    );
  return (
    <div
      className="poster-art poster-placeholder"
      style={{ "--poster-hue": hue(name) }}
      aria-hidden="true"
    >
      {type === "comic" ? <BookOpen size={28} /> : <Film size={28} />}
    </div>
  );
}

/**
 * Lists every copy of a slot (repository · reference duration) so a specific one can be opened.
 * Different cuts of a video have different durations.
 */
function Copies({ slot, repositoryNames }) {
  if (slot.copies.length < 2) return null;
  return (
    <Menu position="bottom-end" withinPortal>
      <Menu.Target>
        <ActionIcon
          variant="subtle"
          color="gray"
          size="sm"
          aria-label={`${slot.copies.length} copies of ${slot.label}`}
          title={`${slot.copies.length} copies`}
        >
          <Copy size={14} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label>{slot.copies.length} copies</Menu.Label>
        {slot.copies.map((copy) => {
          const href = openHref(copy);
          return (
            <Menu.Item
              key={copy.id}
              component={href ? "a" : "button"}
              href={href ?? undefined}
              disabled={!href}
            >
              <Text size="sm">
                {repositoryNames.get(copy.repositoryId) ?? "Standalone"}
              </Text>
              <Text size="xs" c="dimmed">
                {[
                  copy.durationSeconds != null && clock(copy.durationSeconds),
                  copy.unavailable?.title,
                ]
                  .filter(Boolean)
                  .join(" · ") || "Available"}
              </Text>
            </Menu.Item>
          );
        })}
      </Menu.Dropdown>
    </Menu>
  );
}

/** Forgets a title's resume position: its comic page or its video position. */
async function clearProgress(entry) {
  const url =
    titlePath(entry.id) +
    (entry.type === "comic" ? "/comic/progress" : "/progress");
  const response = await fetch(url, { method: "DELETE" });
  if (!response.ok) throw await responseError(response);
}

/** A card's own actions; shown only when there is something to do, such as clearing progress. */
function More({ slot, onChanged }) {
  const entry = slot.entry;
  if (!(entry.progress > 0)) return null;
  return (
    <Menu position="bottom-end" withinPortal>
      <Menu.Target>
        <ActionIcon
          variant="subtle"
          color="gray"
          size="sm"
          aria-label={`More options for ${slot.label}`}
          title="More options"
        >
          <Ellipsis size={14} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Item
          onClick={() =>
            clearProgress(entry).then(
              () => onChanged(),
              (error) => onChanged(error),
            )
          }
        >
          Clear progress
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}

/**
 * One card: a whole series (opens its tomes/episodes), or a single title/slot (opens it).
 * Unavailable titles are dimmed with the reason on the art; a missing document cannot be opened.
 * A title's reading or viewing progress is a thin bar along the bottom of its art.
 */
function Card({
  name,
  detail,
  art,
  type,
  href,
  slot,
  repositoryNames,
  onChanged,
}) {
  const entry = slot?.entry;
  const problem = entry && Object.values(entry.unavailable ?? {})[0];
  const progress = entry?.progress > 0 ? Math.min(1, entry.progress) : null;
  // The name is only in the caption; the warning sits on the art to keep captions short.
  const content = (
    <div className="poster-frame">
      <Art art={art} name={name} type={type} />
      {problem && (
        <span className="poster-problem" title={problem}>
          <TriangleAlert size={12} />
          <span>{problem}</span>
        </span>
      )}
      {progress !== null && (
        <span
          className="poster-progress"
          role="progressbar"
          aria-label="Progress"
          aria-valuenow={Math.round(progress * 100)}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <span style={{ width: `${progress * 100}%` }} />
        </span>
      )}
    </div>
  );
  return (
    <div className={`poster-card${problem ? " poster-unavailable" : ""}`}>
      {href ? (
        <a className="poster-link" href={href} aria-label={name}>
          {content}
        </a>
      ) : (
        <div className="poster-link">{content}</div>
      )}
      <Group gap={4} wrap="nowrap" justify="space-between" align="flex-start">
        <div className="poster-caption">
          <Text size="sm" fw={600} lineClamp={2} title={name}>
            {name}
          </Text>
          {detail && (
            <Text size="xs" c="dimmed" lineClamp={1}>
              {detail}
            </Text>
          )}
        </div>
        {slot && (
          <Group gap={0} wrap="nowrap">
            <Copies slot={slot} repositoryNames={repositoryNames} />
            <More slot={slot} onChanged={onChanged} />
          </Group>
        )}
      </Group>
    </div>
  );
}

function groupCard(group, selection, repositoryNames, onChanged) {
  const type = group.shelf === "manga" ? "comic" : "video";
  if (group.single) {
    const [slot] = group.slots;
    return (
      <Card
        key={group.key}
        name={group.name}
        art={group.art}
        type={type}
        href={openHref(slot.entry)}
        slot={slot}
        repositoryNames={repositoryNames}
        onChanged={onChanged}
      />
    );
  }
  return (
    <Card
      key={group.key}
      name={group.name}
      detail={group.count}
      art={group.art}
      type={type}
      href={postersHref({ repository: selection.repository, group: group.key })}
      repositoryNames={repositoryNames}
    />
  );
}

/** The tomes or episodes of one series, with a season switch when it has several. */
function SeriesView({ group, selection, follow, repositoryNames, onChanged }) {
  const season =
    group.seasons.length > 1
      ? group.seasons.includes(selection.season)
        ? selection.season
        : group.seasons[0]
      : null;
  const slots = season
    ? group.slots.filter((slot) => slot.entry.season === season)
    : group.slots;
  const back = postersHref({ repository: selection.repository });
  return (
    <Stack gap="lg">
      <Breadcrumbs>
        <Anchor href={back} onClick={(event) => follow(event, back)} size="sm">
          Library
        </Anchor>
        <Text size="sm">{group.name}</Text>
      </Breadcrumbs>
      <Group justify="space-between" align="flex-end">
        <div>
          <Title order={3}>{group.name}</Title>
          <Text size="sm" c="dimmed">
            {group.count}
          </Text>
        </div>
        {season && (
          <SegmentedControl
            size="xs"
            value={String(season)}
            data={group.seasons.map((s) => ({
              value: String(s),
              label: `Season ${s}`,
            }))}
            onChange={(value) =>
              follow(
                null,
                postersHref({
                  repository: selection.repository,
                  group: group.key,
                  season: value,
                }),
              )
            }
          />
        )}
      </Group>
      <div className="poster-grid">
        {slots.map((slot) => (
          <Card
            key={slot.key}
            name={slot.label}
            detail={slot.detail}
            art={slot.art}
            type={slot.entry.type}
            href={openHref(slot.entry)}
            slot={slot}
            repositoryNames={repositoryNames}
            onChanged={onChanged}
          />
        ))}
      </div>
    </Stack>
  );
}

/**
 * Plex-like browsing: Manga, Series and Films shelves across all repositories, grouped from
 * document metadata. The repository chips filter before grouping. [onChanged] reloads the library
 * after a card action, receiving the error when the action failed.
 */
export function Posters({ data, selection, go, onChanged }) {
  const repositoryNames = new Map(
    data.repos.map((repo) => [repo.id, repo.name]),
  );
  const filter = data.repos.some((repo) => repo.id === selection.repository)
    ? selection.repository
    : null;
  const entries = data.repos
    .filter((repo) => !filter || repo.id === filter)
    .flatMap((repo) => repo.entries);
  const result = shelves(entries);
  const follow = (event, href) => {
    if (
      event &&
      (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey)
    )
      return;
    event?.preventDefault();
    go(href);
  };
  const open = selection.group
    ? result
        .flatMap((shelf) => shelf.groups)
        .find((g) => g.key === selection.group)
    : null;
  const shown = { ...selection, repository: filter };

  return (
    <Stack gap="xl">
      {data.repos.length > 1 && (
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
              All repositories
            </Chip>
            {data.repos.map((repo) => (
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
                  const link = event.target.closest("a[href^='/?']");
                  if (link) follow(event, link.getAttribute("href"));
                }}
              >
                {shelf.groups.map((group) =>
                  groupCard(group, shown, repositoryNames, onChanged),
                )}
              </div>
            </Stack>
          ))
      )}
      {!open && entries.length === 0 && (
        <Text c="dimmed">No titles in this repository yet.</Text>
      )}
    </Stack>
  );
}
