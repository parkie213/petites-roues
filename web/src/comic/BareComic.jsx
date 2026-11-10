import { useEffect, useState } from "react";
import { Alert, Badge, Button, Loader, Text } from "@mantine/core";
import { ArrowRightLeft } from "lucide-react";
import { responseError } from "../shared/api.ts";
import { findMedia, mediaName } from "../shared/media.ts";
import { libraryReturnPath } from "../shared/library-return.ts";
import { useStoredState } from "../shared/storage.ts";
import { ComicPageView } from "./ComicPageView.jsx";
import {
  ComicStage,
  ComicToolbar,
  ToolbarToggle,
  ViewTools,
  useComicView,
} from "./ComicFrame.jsx";

/**
 * Reads a CBZ that has no study document yet in the same reader frame as an annotated comic
 * (spreads, zoom, turn edges, page picker), with its pages in filename order and no text, notes
 * or editing until a document references it. Without a document there is no reading direction,
 * so the learner picks it (left to right by default); it and the page are remembered per comic in
 * this browser.
 */
export function BareComic({ fingerprint }) {
  const api = `/api/media/${encodeURIComponent(fingerprint)}`;
  const [media, setMedia] = useState(null);
  const [pages, setPages] = useState(null);
  const [error, setError] = useState("");
  const [place, setPlace] = useStoredState(`bare-comic:${fingerprint}`, {
    page: 0,
    direction: "ltr",
  });
  const count = pages?.length ?? 0;
  const rtl = place.direction === "rtl";
  const view = useComicView({
    count,
    index: Math.min(place.page, Math.max(0, count - 1)),
    onIndex: (page) => setPlace((current) => ({ ...current, page })),
    rtl,
    locked: false,
  });

  useEffect(() => {
    findMedia(fingerprint).then(setMedia, (failure) =>
      setError(failure.message),
    );
    fetch(`${api}/pages`)
      .then(async (response) => {
        if (!response.ok) throw await responseError(response);
        setPages(await response.json());
      })
      .catch((failure) => setError(failure.message));
  }, [api, fingerprint]);

  // Fetch the next view ahead, so turning forward shows it at once (pages cache for a day).
  useEffect(() => {
    if (view.next !== null)
      for (const i of [view.next, view.next + 1].filter((i) => i < count))
        new Image().src = `${api}/pages/${i}?attempt=0`;
  }, [api, view.next, count]);

  const title = mediaName(media, "Comic");
  // The page view only needs a title and page ids; a page's id is its index in the CBZ.
  const document = {
    title,
    pages: (pages ?? []).map((_, i) => ({ id: `${i}` })),
  };

  if (error)
    return (
      <main className="comic-message">
        <Alert color="red" title="Could not open comic">
          {error}
        </Alert>
        <Button component="a" href={libraryReturnPath()} mt="md">
          Return to library
        </Button>
      </main>
    );
  if (!pages)
    return (
      <main className="comic-message">
        <Loader aria-label="Loading comic" />
      </main>
    );
  return (
    <main className="comic-reader">
      <ComicToolbar title={title}>
        {(media?.warnings ?? []).map((warning) => (
          <Badge key={warning} size="sm" variant="light" color="yellow">
            {warning}
          </Badge>
        ))}
        <ToolbarToggle
          label={rtl ? "Reading right to left" : "Reading left to right"}
          pressed={rtl}
          onClick={() =>
            setPlace((current) => ({
              ...current,
              direction: rtl ? "ltr" : "rtl",
            }))
          }
          icon={<ArrowRightLeft size={18} />}
        />
        <ViewTools view={view} />
      </ComicToolbar>
      {count === 0 ? (
        <Text p="md" c="dimmed">
          This CBZ has no page images.
        </Text>
      ) : (
        <div className="comic-reading-area">
          <ComicStage view={view}>
            <ComicPageView
              key={view.visible.join("+")}
              document={document}
              pages={view.visible.map((i) => document.pages[i])}
              api={api}
              zoom={view.zoom}
              scale={view.scale}
              boxes={[]}
              rtl={rtl}
              onSelect={() => {}}
              cards={false}
            />
          </ComicStage>
        </div>
      )}
    </main>
  );
}
