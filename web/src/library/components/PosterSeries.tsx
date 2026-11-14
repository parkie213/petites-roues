import {
  Anchor,
  Breadcrumbs,
  Group,
  SegmentedControl,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import type { MouseEvent } from "react";
import type { PosterGroup } from "../../shared/types.ts";
import type { LinkActions } from "../browser.ts";
import { openHref, warnings } from "../cards.js";
import { postersHref, type Selection } from "../navigation.ts";
import { nativeName } from "../language.ts";
import { PosterCard } from "./PosterCard.tsx";

/** Internal library navigation preserves modified clicks for the browser. */
export type FollowPoster = (event: MouseEvent | null, href: string) => void;

/** The class of a grid of cards; a landscape shelf's cards are wide, so its cells are too. */
export function gridClass(landscape: boolean) {
  return landscape ? "poster-grid poster-grid-landscape" : "poster-grid";
}

export function groupCard(
  group: PosterGroup,
  selection: Selection,
  repositoryNames: Map<string, string>,
  actions: LinkActions,
  tagged: boolean,
  landscape = false,
) {
  const { onChanged, onLink } = actions;
  const type = group.type;
  const language = tagged ? group.language : null;
  if (group.single) {
    const [slot] = group.slots;
    return (
      <PosterCard
        key={group.key}
        name={group.name}
        language={language}
        art={group.art}
        type={type}
        landscape={landscape}
        href={openHref(slot.entry)}
        slot={slot}
        repositoryNames={repositoryNames}
        onChanged={onChanged}
        onLink={onLink}
      />
    );
  }
  const needing = group.slots.filter(
    (slot) => warnings(slot.entry).length > 0,
  ).length;
  return (
    <PosterCard
      key={group.key}
      name={group.name}
      detail={group.count}
      language={language}
      attention={
        needing > 0
          ? [`${needing} of ${group.slots.length} need attention`]
          : []
      }
      art={group.art}
      type={type}
      landscape={landscape}
      href={postersHref({ repository: selection.repository, group: group.key })}
      repositoryNames={repositoryNames}
    />
  );
}

/** The tomes or episodes of one series, with a season switch when it has several. */
export function SeriesView({
  group,
  selection,
  follow,
  repositoryNames,
  onChanged,
  onLink,
  landscape = false,
}: LinkActions & {
  group: PosterGroup;
  /** Whether the shelf the series is on draws 16:9 cards. */
  landscape?: boolean;
  selection: Selection;
  follow: FollowPoster;
  repositoryNames: Map<string, string>;
}) {
  const season =
    group.seasons.length > 1
      ? selection.season !== null && group.seasons.includes(selection.season)
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
            {[group.language && nativeName(group.language), group.count]
              .filter(Boolean)
              .join(" · ")}
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
                  season: Number(value),
                }),
              )
            }
          />
        )}
      </Group>
      <div className={gridClass(landscape)}>
        {slots.map((slot) => (
          <PosterCard
            key={slot.key}
            name={slot.label}
            detail={slot.detail}
            art={slot.art}
            type={slot.entry.type}
            landscape={landscape}
            href={openHref(slot.entry)}
            slot={slot}
            repositoryNames={repositoryNames}
            onChanged={onChanged}
            onLink={onLink}
          />
        ))}
      </div>
    </Stack>
  );
}
