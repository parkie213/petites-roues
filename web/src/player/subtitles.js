export function parseSubtitles(text) {
  const timestamp = (h, m, s, ms) => +h * 3600 + +m * 60 + +s + +ms / 1000;
  const cues = text
    .replace(/^\uFEFF/, "")
    .trim()
    .split(/\r?\n\s*\r?\n/)
    .flatMap((block) => {
      const match = block.match(
        /(?:^|\n)(\d{2,}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2,}):(\d{2}):(\d{2})[,.](\d{3})[^\r\n]*\r?\n([\s\S]+)/,
      );
      if (!match) return [];
      return [
        {
          start: timestamp(...match.slice(1, 5)),
          end: timestamp(...match.slice(5, 9)),
          source: match[9].trim().replace(/\r\n/g, "\n"),
        },
      ];
    });
  if (!cues.length || cues.some((c) => c.end <= c.start))
    throw new Error("No valid subtitles found. Choose an SRT file.");
  return cues.sort((a, b) => a.start - b.start);
}
