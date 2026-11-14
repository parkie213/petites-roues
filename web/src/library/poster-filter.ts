import { warnings } from "./cards.js";
import type { PosterShelves } from "../shared/types.ts";
import type { PosterFilters } from "./components/LibraryToolbar.tsx";

/**
 * Narrows displayed shelf cards to the shelf whose key is [shelf] (null keeps every shelf), then
 * by [search] and warnings. Cards keep their contents and series navigation; shelves left with no
 * cards are dropped and the order of the rest is unchanged.
 */
export function filterShelves(
  shelves: PosterShelves["shelves"],
  {
    search = "",
    shelf = null,
    attentionOnly = false,
  }: {
    search?: string;
    shelf?: PosterFilters["shelf"];
    attentionOnly?: boolean;
  } = {},
) {
  const query = search.trim().toLocaleLowerCase();
  return shelves
    .filter((candidate) => shelf === null || candidate.key === shelf)
    .map((candidate) => ({
      ...candidate,
      groups: candidate.groups.filter(
        (group) =>
          (!query ||
            group.name.toLocaleLowerCase().includes(query) ||
            group.slots.some((slot) =>
              slot.label.toLocaleLowerCase().includes(query),
            )) &&
          (!attentionOnly ||
            group.slots.some((slot) => warnings(slot.entry).length > 0)),
      ),
    }))
    .filter((candidate) => candidate.groups.length > 0);
}
