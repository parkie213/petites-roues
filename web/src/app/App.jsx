import { ComicReader } from "../comic/ComicReader.jsx";
import { Library } from "../library/Library.jsx";
import { Notes } from "../library/Notes.jsx";
import { Player } from "../player/Player.jsx";
import { Watch } from "../player/Watch.jsx";

export function App() {
  const url = new URL(location.href);
  if (url.pathname === "/") return <Library />;
  if (/^\/notes\/?$/.test(url.pathname)) return <Notes />;
  const note = url.pathname.match(/^\/notes\/([^/]+)\/([^/]+)\/?$/);
  if (note)
    return (
      <Notes
        repository={decodeURIComponent(note[1])}
        id={decodeURIComponent(note[2])}
      />
    );
  const reader = url.pathname.match(/^\/reader\/([A-Za-z0-9_-]+)\/?$/);
  if (reader) return <ComicReader key={reader[1]} id={reader[1]} />;
  const watch = url.pathname.match(/^\/watch\/([A-Za-z0-9_-]+)\/?$/);
  if (watch) return <Watch key={watch[1]} id={watch[1]} />;
  const match = url.pathname.match(/^\/player\/([A-Za-z0-9_-]+)\/?$/);
  if (match) return <Player key={match[1]} id={match[1]} />;
  const oldId = url.searchParams.get("title");
  if (url.pathname === "/player/" && oldId && /^[A-Za-z0-9_-]+$/.test(oldId)) {
    location.replace("/player/" + oldId + "/" + url.search);
    return null;
  }
  return (
    <main className="page">
      <h1>Page not found</h1>
      <a href="/">Return to library</a>
    </main>
  );
}
