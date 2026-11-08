/**
 * Linking a media file to a study document by hand, for when fingerprints don't (the document was
 * timed against another copy, or records no reference). The server keeps the link in the library
 * (PUT/DELETE /api/library/media-links); the document is not changed. These helpers list what can
 * be linked and make the calls.
 */
import { clock } from "../player/quiz.js";
import { responseError } from "../shared/api.js";

const collator = new Intl.Collator(undefined, { numeric: true });

const numbers = (item) =>
  [
    item.season != null && `S${item.season}`,
    item.episode != null && `E${item.episode}`,
    item.volume != null && `Tome ${item.volume}`,
  ]
    .filter(Boolean)
    .join("");

/**
 * The study documents a media file of [kind] could link to: present documents of that kind in a
 * registered folder, those with no media first, then by series, numbers and title.
 */
export function documentChoices(repos, kind) {
  return repos
    .flatMap((repo) =>
      repo.entries
        .filter((entry) => entry.type === kind && entry.documentAvailable)
        .map((entry) => ({
          key: entry.id,
          titleId: entry.id,
          free: !entry.mediaAvailable,
          label: entry.title,
          detail: [
            [entry.series, numbers(entry)].filter(Boolean).join(" "),
            entry.durationSeconds != null && clock(entry.durationSeconds),
            repo.name,
            entry.mediaAvailable ? `has ${entry.mediaPath}` : "no media",
          ]
            .filter(Boolean)
            .join(" · "),
          sort: [entry.series ?? entry.title, entry.relativePath],
        })),
    )
    .sort(order);
}

/**
 * The media files of [kind] a document could link to: those no document has first, then by folder
 * and path. A file another document has would move to this one.
 */
export function mediaChoices(repos, kind) {
  const titles = new Map(
    repos.flatMap((repo) => repo.entries.map((entry) => [entry.id, entry])),
  );
  return repos
    .flatMap((repo) =>
      repo.media
        .filter((item) => item.kind === kind)
        .map((item) => ({
          key: `${repo.id}/${item.relativePath}`,
          repositoryId: repo.id,
          relativePath: item.relativePath,
          free: !item.titleId,
          label: item.relativePath,
          detail: [
            repo.name,
            item.durationSeconds != null && clock(item.durationSeconds),
            item.titleId
              ? `linked to ${titles.get(item.titleId)?.title ?? "another document"}`
              : "no study document",
          ]
            .filter(Boolean)
            .join(" · "),
          sort: [repo.name, item.relativePath],
        })),
    )
    .sort(order);
}

function order(a, b) {
  return (
    Number(b.free) - Number(a.free) ||
    collator.compare(a.sort[0], b.sort[0]) ||
    collator.compare(a.sort[1] ?? "", b.sort[1] ?? "")
  );
}

/** The choices whose label or detail contains every word of [text], ignoring case. */
export function filterChoices(choices, text) {
  const words = text.toLowerCase().split(/\s+/).filter(Boolean);
  return choices.filter((choice) => {
    const haystack = `${choice.label} ${choice.detail}`.toLowerCase();
    return words.every((word) => haystack.includes(word));
  });
}

/** Links the media file at [relativePath] in folder [repositoryId] to title [titleId]. */
export async function linkMedia({ repositoryId, relativePath, titleId }) {
  const response = await fetch("/api/library/media-links", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ repositoryId, relativePath, titleId }),
  });
  if (!response.ok) throw await responseError(response);
}

/** Removes the media file's manual link, so it links by fingerprint again. */
export async function unlinkMedia({ repositoryId, relativePath }) {
  const query = new URLSearchParams({
    repository: repositoryId,
    path: relativePath,
  });
  const response = await fetch(`/api/library/media-links?${query}`, {
    method: "DELETE",
  });
  if (!response.ok) throw await responseError(response);
}
