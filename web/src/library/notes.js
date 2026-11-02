/**
 * Study notes: Markdown pages in a content repository's notes/ folder, read in the library
 * (served by GET /api/notes). Independent of the general quizzes.
 */

/** The address of [note]'s page. */
export const notePath = (note) =>
  `/notes/${encodeURIComponent(note.repository)}/${encodeURIComponent(note.id)}`;

/** The note a /notes/{repository}/{id} address names, matched against the decoded parts. */
export const findNote = (notes, repository, id) =>
  notes.find((note) => note.repository === repository && note.id === id);

/**
 * The notes grouped by repository, in their served order: [{ name, notes }]. One group with a
 * null name when every note comes from the same repository, so no heading is shown.
 */
export function noteGroups(notes) {
  const groups = new Map();
  for (const note of notes) {
    if (!groups.has(note.repository))
      groups.set(note.repository, { name: note.repositoryName, notes: [] });
    groups.get(note.repository).notes.push(note);
  }
  const list = [...groups.values()];
  return list.length === 1 ? [{ name: null, notes: list[0].notes }] : list;
}
