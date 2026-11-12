import { useEffect, useRef, useState } from "react";

/** Measures the shared viewport and resets its native scroll position when the zoom mode changes. */
export function useComicViewport(zoom: string) {
  const viewport = useRef<HTMLElement>(null);
  const [area, setArea] = useState({ width: 0, height: 0 });
  useEffect(() => {
    const observer = new ResizeObserver(([entry]) =>
      setArea({
        width: entry.contentRect.width,
        height: entry.contentRect.height,
      }),
    );
    observer.observe(viewport.current!);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    viewport.current?.scrollTo({ top: 0, left: 0 });
  }, [zoom]);
  return { viewport, area };
}
