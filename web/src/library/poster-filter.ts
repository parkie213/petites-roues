import { warnings } from "./cards.js";
import type { PosterShelves } from "../shared/types.ts";
import type { PosterFilters } from "./components/LibraryToolbar.tsx";

/** Narrows displayed shelf cards without changing their contents or series navigation. */
export function filterShelves(
  shelves: PosterShelves["shelves"],
  {
    search = "",
    mediaFilter = "all",
    attentionOnly = false,
  }: {
    search?: string;
    mediaFilter?: PosterFilters["media"];
    attentionOnly?: boolean;
  } = {},
) {
  const query = search.trim().toLocaleLowerCase();
  return shelves
    .map((shelf) => ({
      ...shelf,
      groups: shelf.groups.filter(
        (group) =>
          (mediaFilter === "all" || group.type === mediaFilter) &&
          (!query ||
            group.name.toLocaleLowerCase().includes(query) ||
            group.slots.some((slot) =>
              slot.label.toLocaleLowerCase().includes(query),
            )) &&
          (!attentionOnly ||
            group.slots.some((slot) => warnings(slot.entry).length > 0)),
      ),
    }))
    .filter((shelf) => shelf.groups.length > 0);
}
