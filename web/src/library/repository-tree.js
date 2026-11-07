/**
 * Where a library item sits in its folder: a title with linked media at the media's path, any
 * other title at its document's path, and unlinked media at its own path.
 */
export function shownPath(item) {
  return item.mediaPath ?? item.relativePath ?? "document.json";
}

/**
 * Build the displayed tree from indexed paths; discovery happens only on Add/Refresh. Unlinked
 * media (`kind: "media"`) is placed beside the titles, named after its file.
 */
export function repositoryTree(entries, media = []) {
  const root = { name: "", folders: [], titles: [] };
  const items = [
    ...entries,
    ...media.map((item) => ({
      ...item,
      mediaKind: item.kind,
      kind: "media",
      id: `media:${item.relativePath}`,
      title: item.relativePath.split("/").pop(),
    })),
  ];
  for (const entry of items) {
    const parts = shownPath(entry)
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
  function sort(node) {
    node.folders.sort((a, b) =>
      a.name.localeCompare(b.name, undefined, { numeric: true }),
    );
    node.titles.sort((a, b) =>
      a.title.localeCompare(b.title, undefined, { numeric: true }),
    );
    node.folders.forEach(sort);
  }
  sort(root);
  return root;
}

// Keep each ancestor visible. Invalid or stale URL segments stop at the last indexed folder.
export function repositoryColumns(entries, requested = [], media = []) {
  let node = repositoryTree(entries, media);
  const columns = [{ node, parts: [] }];
  for (const part of requested) {
    const folder = node.folders.find((child) => child.name === part);
    if (!folder) break;
    node = folder;
    columns.push({ node, parts: [...columns.at(-1).parts, part] });
  }
  return columns;
}
