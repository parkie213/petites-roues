/** "41.2 GB": decimal units, as file managers and disk tools report sizes. */
export function formatBytes(bytes) {
  const units = ["B", "kB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1000;
    unit++;
  }
  const digits = unit === 0 || value >= 100 ? 0 : 1;
  return `${value.toFixed(digits)} ${units[unit]}`;
}

/**
 * The library header's usage line from GET /api/library/usage: the library's size, then the free
 * space, naming the repositories on each disk when they are spread over several.
 */
export function usageLine({ libraryBytes, disks }) {
  const free = disks.map(
    (disk) =>
      `${formatBytes(disk.freeBytes)} free` +
      (disks.length > 1 ? ` (${disk.repositories.join(", ")})` : ""),
  );
  return [`Library ${formatBytes(libraryBytes)}`, ...free].join(" · ");
}
