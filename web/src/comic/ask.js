import { alignmentState } from "../player/alignment.js";
import { languageName } from "../player/languages.js";
import { boxLines, pageBoxes } from "./annotations.js";

/**
 * A short, self-contained message asking a chat app (ChatGPT, Claude) about one speech bubble,
 * matching the phone reader's: the learner's question, the bubble with its translation and notes,
 * the phrase being explored, and the page's other bubbles for context. Plain text, so it can start
 * a new chat or be pasted into an existing one. A blank question asks for more depth.
 */
export function askMessage(document, cue, phrase, question) {
  const source = languageName(document.sourceLanguage);
  const notes = languageName(document.notesLanguage);
  const index = document.pages.findIndex((page) => page.id === cue.pageId);
  const pageName = document.pages[index]?.label || String(index + 1);
  const alignment = alignmentState(cue) === "ready" ? cue.alignment : null;
  const explored = phrase == null ? null : alignment?.source[phrase];
  const lines = [
    `I'm learning ${source} by reading “${document.title}” and have a question about a ` +
      `speech bubble on page ${pageName}.`,
    "",
    question.trim()
      ? `My question: ${question.trim()}`
      : "Please explain this bubble in more depth.",
  ];
  if (explored) lines.push(`I'm asking about the phrase “${explored}”.`);
  lines.push("", `Bubble: ${cue.source}`);
  if (cue.translation?.trim()) lines.push(`Translation: ${cue.translation}`);
  const phrases = alignment
    ? alignment.source.flatMap((text, i) => {
        const links = alignment.links.filter((link) => link.source.includes(i));
        if (!links.length) return [];
        const translation = [
          ...new Set(links.flatMap((link) => link.translation)),
        ]
          .sort((a, b) => a - b)
          .map((chunk) => alignment.translation[chunk])
          .join(" … ");
        const explanation = [...new Set(links.map((link) => link.note))]
          .filter((note) => note?.trim())
          .join(" ");
        return [
          `- “${text}” → ${translation}` +
            (explanation ? `: ${explanation}` : ""),
        ];
      })
    : [];
  if (phrases.length)
    lines.push("", "Phrase notes from my reader:", ...phrases);
  if (cue.notes?.length)
    lines.push("", "Sentence notes:", ...cue.notes.map((note) => `- ${note}`));
  const others = boxLines(pageBoxes(document, cue.pageId)).filter((other) =>
    other.source?.trim(),
  );
  if (others.length > 1)
    lines.push(
      "",
      "The page's bubbles in reading order, for context (→ marks this one):",
      ...others.map((other) => {
        const line =
          other.source +
          (other.translation?.trim() ? ` (${other.translation})` : "");
        return other.id === cue.id ? `→ ${line}` : `- ${line}`;
      }),
    );
  lines.push(
    "",
    `Please answer in ${notes}. Explain why the ${source} means what it does here — idioms, ` +
      "pronominal verbs and grammar — not just a translation. Keep it concise.",
  );
  return lines.join("\n");
}

/** Chat apps that open a new conversation with a prefilled prompt from a link. */
export const askTargets = [
  {
    name: "ChatGPT",
    url: (text) => `https://chatgpt.com/?q=${encodeURIComponent(text)}`,
  },
  {
    name: "Claude",
    url: (text) => `https://claude.ai/new?q=${encodeURIComponent(text)}`,
  },
];
