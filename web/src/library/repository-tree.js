/**
 * Build a registered folder's tree as it is on disk, from indexed paths; discovery happens only on
 * Add/Refresh. Study documents sit at their document's path; every video and CBZ
 * (`kind: "media"`) at its own path, named after its file.
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
