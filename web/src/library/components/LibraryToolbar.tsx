import { Anchor, SegmentedControl, Text, TextInput } from "@mantine/core";
import { Search } from "lucide-react";
import { documentsHref, type LibraryView } from "../navigation.ts";
import type { librarySummary } from "../summary.ts";

/** How the posters are narrowed: by words in their titles, to one shelf, and to warnings. */
export interface PosterFilters {
  search: string;
  /** Key of the chosen shelf, or null for All; the posters offer the shelves as tabs. */
  shelf: string | null;
  attentionOnly: boolean;
}

export const NO_FILTERS: PosterFilters = {
  search: "",
  shelf: null,
  attentionOnly: false,
};

/**
 * Above the library: what it holds (with disk [usage] on hover), the view switcher once there are
 * folders, and in the posters view the search and warnings toggle (the shelf tabs sit above the
 * posters). Study documents without media link to the documents view, which lists them.
 */
export function LibraryToolbar({
  view,
  summary,
  usage,
  canSwitch,
  onView,
  filters,
  onFilters,
}: {
  view: LibraryView;
  summary: ReturnType<typeof librarySummary> | undefined;
  usage: string;
  /** Whether there are folders to show in other views. */
  canSwitch: boolean;
  onView: (view: LibraryView) => void;
  filters: PosterFilters;
  onFilters: (filters: PosterFilters) => void;
}) {
  const posters = view === "posters";
  return (
    <section className="library-toolbar" aria-label="Library controls">
      <div className="library-search-row">
        {posters && (
          <TextInput
            className="library-search"
            placeholder="Search your library"
            aria-label="Search your library"
            leftSection={<Search size={18} />}
            value={filters.search}
            onChange={(event) =>
              onFilters({ ...filters, search: event.currentTarget.value })
            }
          />
        )}
        {summary && (
          <Text
            size="xs"
            c="dimmed"
            className="library-counts"
            title={usage || undefined}
          >
            {summary.counts}
            {posters && summary.attention && (
              <>
                {summary.counts && " · "}
                <button
                  type="button"
                  className="library-warnings"
                  aria-pressed={filters.attentionOnly}
                  title="Show only what needs attention"
                  onClick={() =>
                    onFilters({
                      ...filters,
                      attentionOnly: !filters.attentionOnly,
                    })
                  }
                >
                  <span aria-hidden="true" className="library-attention-dot" />
                  {summary.attention}
                </button>
              </>
            )}
          </Text>
        )}
        {canSwitch && (
          <SegmentedControl
            className="library-views"
            size="xs"
            value={view}
            onChange={(value) => onView(value as LibraryView)}
            data={[
              { value: "posters", label: "Posters" },
              { value: "documents", label: "Documents" },
              { value: "columns", label: "Repositories" },
            ]}
            aria-label="Library view"
          />
        )}
      </div>
      {summary?.unlinked && (
        <div className="library-filter-row">
          <Anchor
            size="xs"
            className="library-unlinked"
            href={documentsHref()}
            onClick={(event) => {
              event.preventDefault();
              onView("documents");
            }}
          >
            {summary.unlinked}
          </Anchor>
        </div>
      )}
    </section>
  );
}
