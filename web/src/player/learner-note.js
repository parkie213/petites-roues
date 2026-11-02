// A deliberately small inline format: backticks mark source-language teaching spans.
// Everything else stays literal text, including HTML and unmatched backticks.
export function noteSegments(text) {
  return String(text)
    .split(/(`[^`\r\n]+`)/g)
    .filter(Boolean)
    .map((part) => ({
      text:
        part.startsWith("`") && part.endsWith("`") && part.length > 2
          ? part.slice(1, -1)
          : part,
      highlighted:
        part.startsWith("`") && part.endsWith("`") && part.length > 2,
    }));
}
