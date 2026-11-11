import type { CatalogueItem, MediaItem } from "../shared/types.ts";

/** Media named by its file for the folder browser, retaining its intrinsic media kind. */
export type RepositoryMedia = Omit<MediaItem, "kind" | "title"> & {
  kind: "media";
  mediaKind: MediaItem["kind"];
  id: string;
  title: string;
};

export type RepositoryItem = CatalogueItem | RepositoryMedia;

/** A folder built from indexed relative paths, never by accessing the filesystem. */
export interface RepositoryFolder {
  name: string;
  folders: RepositoryFolder[];
  titles: RepositoryItem[];
}

/** An ancestor folder and the path that opens it in the browser. */
export interface RepositoryColumn {
  node: RepositoryFolder;
  parts: string[];
}

/** Distinguishes file cards from study-document cards in a folder. */
export function isMedia(item: RepositoryItem): item is RepositoryMedia {
  return "kind" in item && item.kind === "media";
}

/**
 * Build a registered folder's tree as it is on disk, from indexed paths; discovery happens only on
 * Add/Refresh. Study documents sit at their document's path; every video and CBZ
 * (`kind: "media"`) at its own path, named after its file.
 */
export function repositoryTree(
  entries: readonly CatalogueItem[],
  media: readonly MediaItem[] = [],
): RepositoryFolder {
  const root: RepositoryFolder = { name: "", folders: [], titles: [] };
  const items: RepositoryItem[] = [
    ...entries,
    ...media.map((item) => ({
      ...item,
      mediaKind: item.kind,
      kind: "media" as const,
      id: `media:${item.relativePath}`,
      title: item.relativePath.split("/").pop() ?? "",
    })),
  ];
  for (const entry of items) {
    const parts = (entry.relativePath ?? "document.json")
      .replaceAll("\\", "/")
      .split("/")
      .slice(0, -1);
    let node = root;
    for (const name of parts) {
      let folder = node.folders.find((child) => child.name === name);
      if (!folder) {
        folder = { name, folders: [], titles: [] };
        node.folders.push(folder);
      }
      node = folder;
    }
    node.titles.push(entry);
  }
  function sort(node: RepositoryFolder) {
    node.folders.sort((a, b) =>
      a.name.localeCompare(b.name, undefined, { numeric: true }),
    );
    node.titles.sort((a, b) =>
      (a.title ?? "").localeCompare(b.title ?? "", undefined, {
        numeric: true,
      }),
    );
    node.folders.forEach(sort);
  }
  sort(root);
  return root;
}

/** Keeps each ancestor visible; stale URL segments stop at the last indexed folder. */
export function repositoryColumns(
  entries: readonly CatalogueItem[],
  requested: readonly string[] = [],
  media: readonly MediaItem[] = [],
): RepositoryColumn[] {
  let node = repositoryTree(entries, media);
  const columns: RepositoryColumn[] = [{ node, parts: [] }];
  for (const part of requested) {
    const folder = node.folders.find((child) => child.name === part);
    if (!folder) break;
    node = folder;
    columns.push({ node, parts: [...columns[columns.length - 1].parts, part] });
  }
  return columns;
}

/** Study documents, videos and comics below a folder, including all descendants. */
function counts(node: RepositoryFolder) {
  const total = { documents: 0, videos: 0, comics: 0 };
  for (const item of node.titles) {
    if (!isMedia(item)) total.documents++;
    else if (item.mediaKind === "comic") total.comics++;
    else total.videos++;
  }
  for (const folder of node.folders) {
    const child = counts(folder);
    total.documents += child.documents;
    total.videos += child.videos;
    total.comics += child.comics;
  }
  return total;
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

/** The folder row's recursive content summary, or “Empty” when it has no indexed items. */
export function describeFolder(node: RepositoryFolder): string {
  const { documents, videos, comics } = counts(node);
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
