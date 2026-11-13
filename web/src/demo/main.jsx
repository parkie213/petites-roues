import { createRoot } from "react-dom/client";
import { useEffect, useState } from "react";
import { AppTheme } from "../app/AppTheme.jsx";
import { ComicPageView } from "../comic/ComicPageView.tsx";
import {
  ComicStage,
  ComicToolbar,
  ViewTools,
  ToolbarToggle,
  useComicView,
} from "../comic/ComicFrame.jsx";
import { Headphones } from "lucide-react";
import recordings from "../../../site/demo-audio/manifest.json";
import { ReadAloudKey, ReadAloudProvider } from "../comic/ReadAloud.jsx";
import { StudyLanguagesProvider } from "../player/StudyLanguages.jsx";
import { playerDocument } from "../shared/api.ts";
import { useStoredState } from "../shared/storage.ts";
import source from "../../../example/documents-fr/comics/lulu/le-parapluie-de-lulu/document.json";
import "@fontsource/manrope/400.css";
import "@fontsource/manrope/500.css";
import "@fontsource/manrope/600.css";
import "@fontsource/manrope/700.css";
import "../app/global.css";
import "./demo.css";

const document = playerDocument(source);
const audioUrl = (text) =>
  recordings.clips[text] ? `./audio/${recordings.clips[text]}` : null;
const imageUrl = (id) => `./pages/${encodeURIComponent(id)}.jpg`;

/** A static, read-only library and the app's shared comic reader, with browser-local progress. */
function Demo() {
  const [reading, setReading] = useState(location.hash === "#read");
  const [storedPage, setPage] = useStoredState("demo:lulu:page", 0);
  const index =
    Number.isInteger(storedPage) &&
    storedPage >= 0 &&
    storedPage < document.pages.length
      ? storedPage
      : 0;
  const [selectedId, setSelectedId] = useState(null);
  const [autoListen, setAutoListen] = useStoredState("demo:auto-listen", false);
  const view = useComicView({
    count: reading ? document.pages.length : 0,
    index,
    onIndex: setPage,
    rtl: false,
    locked: false,
  });
  useEffect(() => {
    const change = () => setReading(location.hash === "#read");
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  useEffect(() => {
    window.document.title = reading
      ? `${document.title} · Petites roues demo`
      : "Library · Petites roues demo";
  }, [reading]);
  if (!reading)
    return (
      <main className="demo-library">
        <header className="demo-header">
          <a className="demo-brand" href="../">
            petites roues
          </a>
          <a href="https://github.com/parkie213/petites-roues">GitHub ↗</a>
        </header>
        <h1>Library</h1>
        <p>
          Try a little French. Open the comic, then tap a speech bubble for its
          translation and phrase explanations.
        </p>
        <section aria-label="Comics">
          <h2>
            Comics <span>French → English</span>
          </h2>
          <a className="demo-comic" href="#read">
            <img
              src={imageUrl(document.pages[0].id)}
              alt=""
              width="800"
              height="1200"
            />
            <strong>{document.title}</strong>
            <span>
              {document.pages.length} pages ·{" "}
              {index > 0 ? `Continue on page ${index + 1}` : "Read comic"}
            </span>
          </a>
        </section>
        <p className="demo-caption">
          An original sample comic. Your place is saved in this browser.
        </p>
      </main>
    );
  const pages = view.visible.map((i) => document.pages[i]);
  const boxes = document.boxes.filter((box) =>
    pages.some((page) => page.id === box.pageId),
  );
  const lines = boxes.flatMap((box) => box.lines);
  const selected = lines.find((line) => line.id === selectedId) ?? lines[0];
  return (
    <StudyLanguagesProvider document={document}>
      <ReadAloudProvider audioUrl={audioUrl} autoListen={autoListen}>
        <ReadAloudKey text={selected?.source} />
        <main className="comic-reader demo-reader">
          <ComicToolbar title={document.title} backHref="#library">
            <ToolbarToggle
              label={autoListen ? "Stop listening on hover" : "Listen on hover"}
              pressed={autoListen}
              onClick={() => setAutoListen((value) => !value)}
              icon={<Headphones size={18} />}
            />
            <ViewTools view={view} />
          </ComicToolbar>
          <div className="demo-reader-hint">
            Tap a speech bubble to explore the French. Use the arrows to turn
            pages.
          </div>
          <div className="comic-reading-area">
            <ComicStage view={view}>
              <ComicPageView
                key={pages.map((p) => p.id).join("+")}
                document={document}
                pages={pages}
                api=""
                pageImageUrl={imageUrl}
                zoom={view.zoom}
                scale={view.scale}
                boxes={boxes}
                rtl={false}
                study={{ selectedId, onSelect: setSelectedId, cards: true }}
              />
            </ComicStage>
          </div>
        </main>
      </ReadAloudProvider>
    </StudyLanguagesProvider>
  );
}

createRoot(window.document.getElementById("root")).render(
  <AppTheme>
    <Demo />
  </AppTheme>,
);
