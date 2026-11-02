// Build the displayed tree from indexed paths; title discovery happens only on Add/Refresh.
export function repositoryTree(entries) {
  const root = { name: "", folders: [], titles: [] };
  for (const entry of entries) {
    const parts = (entry.relativePath || "document.json")
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
export function repositoryColumns(entries, requested = []) {
  let node = repositoryTree(entries);
  const columns = [{ node, parts: [] }];
  for (const part of requested) {
    const folder = node.folders.find((child) => child.name === part);
    if (!folder) break;
    node = folder;
    columns.push({ node, parts: [...columns.at(-1).parts, part] });
  }
  return columns;
}
