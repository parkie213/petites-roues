import { useEffect, useState } from "react";
import { Container } from "@mantine/core";
import { LibraryHeader } from "../library/LibraryHeader.jsx";
import "../library/Library.css";
import { BareComic } from "../comic/BareComic.jsx";
import { ComicReader } from "../comic/ComicReader.jsx";
import { Library } from "../library/Library.jsx";
import { QuizPage } from "../library/QuizPage.jsx";
import { Notes } from "../library/Notes.jsx";
import { BareVideo } from "../player/BareVideo.jsx";
import { Watch } from "../player/Watch.jsx";

export function App() {
  const [path, setPath] = useState(location.pathname);
  const [actionsTarget, setActionsTarget] = useState(null);
  useEffect(() => {
    const update = () => setPath(location.pathname);
    window.addEventListener("popstate", update);
    return () => window.removeEventListener("popstate", update);
  }, []);
  const url = new URL(location.href);
  const section =
    path === "/"
      ? "library"
      : /^\/quizzes\/?$/.test(path)
        ? "quizzes"
        : /^\/notes(?:\/[^/]+\/[^/]+)?\/?$/.test(path)
          ? "notes"
          : null;
  if (section)
    return (
      <main className="mantine-library">
        <Container size={1320} py={0}>
          <LibraryHeader active={section} actionsRef={setActionsTarget} />
          {section === "library" ? (
            <Library actionsTarget={actionsTarget} />
          ) : section === "notes" ? (
            <Notes />
          ) : (
            <QuizPage />
          )}
        </Container>
      </main>
    );
  const reader = url.pathname.match(/^\/reader\/([A-Za-z0-9_-]+)\/?$/);
  if (reader) return <ComicReader key={reader[1]} id={reader[1]} />;
  const watch = url.pathname.match(/^\/watch\/([A-Za-z0-9_-]+)\/?$/);
  if (watch) return <Watch key={watch[1]} id={watch[1]} />;
  const bare = url.pathname.match(/^\/video\/([^/]+)\/?$/);
  if (bare) {
    const fingerprint = decodeURIComponent(bare[1]);
    return <BareVideo key={fingerprint} fingerprint={fingerprint} />;
  }
  const comic = url.pathname.match(/^\/comic\/([^/]+)\/?$/);
  if (comic) {
    const fingerprint = decodeURIComponent(comic[1]);
    return <BareComic key={fingerprint} fingerprint={fingerprint} />;
  }
  const match = url.pathname.match(/^\/player\/([A-Za-z0-9_-]+)\/?$/);
  if (match) return <Watch key={match[1]} id={match[1]} />;
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
