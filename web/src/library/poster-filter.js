import { warnings } from "./cards.js";

/** Narrows displayed shelf cards without changing their contents or series navigation. */
export function filterShelves(
  shelves,
  { search = "", mediaFilter = "all", attentionOnly = false } = {},
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
