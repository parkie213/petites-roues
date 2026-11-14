import type { KeyboardEvent } from "react";
import type { PosterShelves } from "../../shared/types.ts";

/**
 * Tabs narrowing the posters to one shelf: "All" first, then every shelf in the server's order
 * with its number of cards. [value] is the chosen shelf's key, or null for All; it must already
 * name a shelf in [shelves] (the caller falls back to All otherwise). Arrow keys, Home and End
 * move between tabs, and the row scrolls sideways when the shelves do not fit.
 */
export function ShelfTabs({
  shelves,
  value,
  onChange,
}: {
  shelves: PosterShelves["shelves"];
  value: string | null;
  onChange: (shelf: string | null) => void;
}) {
  const tabs = [
    {
      key: null,
      label: "All",
      count: shelves.reduce((sum, shelf) => sum + shelf.groups.length, 0),
    },
    ...shelves.map((shelf) => ({
      key: shelf.key,
      label: shelf.label,
      count: shelf.groups.length,
    })),
  ];
  const move = (event: KeyboardEvent<HTMLDivElement>) => {
    const at = tabs.findIndex((tab) => tab.key === value);
    const next =
      event.key === "ArrowRight"
        ? (at + 1) % tabs.length
        : event.key === "ArrowLeft"
          ? (at - 1 + tabs.length) % tabs.length
          : event.key === "Home"
            ? 0
            : event.key === "End"
              ? tabs.length - 1
              : -1;
    if (next < 0) return;
    event.preventDefault();
    onChange(tabs[next].key);
    const buttons =
      event.currentTarget.querySelectorAll<HTMLElement>("[role=tab]");
    buttons[next]?.focus();
  };
  return (
    <div
      className="library-filters"
      role="tablist"
      aria-label="Shelves"
      onKeyDown={move}
    >
      {tabs.map((tab) => (
        <button
          key={tab.key ?? "all"}
          type="button"
          role="tab"
          aria-selected={tab.key === value}
          tabIndex={tab.key === value ? 0 : -1}
          onClick={() => onChange(tab.key)}
        >
          {tab.label}
          <span className="library-filter-count">{tab.count}</span>
        </button>
      ))}
    </div>
  );
}
