import { useState, type CSSProperties } from "react";
import { Group, Text, Tooltip } from "@mantine/core";
import { BookOpen, Film } from "lucide-react";
import type { PosterArt, PosterEntry, PosterSlot } from "../../shared/types.ts";
import type { LinkActions } from "../browser.ts";
import { warnings } from "../cards.js";
import { nativeName } from "../language.ts";
import { PosterActions, PosterCopies } from "./PosterActions.tsx";

/** Shared visual card props; series cards have no slot actions. */
interface PosterCardProps extends Partial<LinkActions> {
  name: string;
  detail?: string | null;
  art?: PosterArt | null;
  type: PosterEntry["type"];
  href: string | null;
  slot?: PosterSlot;
  repositoryNames: Map<string, string>;
  attention?: string[];
  language?: string | null;
}

/** A stable hue per name, so placeholders are distinguishable but never random. */
function hue(name: string) {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.codePointAt(0)!) >>> 0;
  return hash % 360;
}

function Art({
  art,
  name,
  type,
}: Pick<PosterCardProps, "art" | "name" | "type">) {
  const url = art?.url;
  const [failed, setFailed] = useState<string | null>(null);
  if (url && failed !== url)
    return (
      <img
        className="poster-art"
        src={url}
        alt=""
        loading="lazy"
        onError={() => setFailed(url)}
      />
    );
  return (
    <div
      className="poster-art poster-placeholder"
      style={{ "--poster-hue": hue(name) } as CSSProperties}
      aria-hidden="true"
    >
      {type === "comic" ? <BookOpen size={28} /> : <Film size={28} />}
    </div>
  );
}

/**
 * A quiet marker beside the caption; hovering or focusing it lists what needs attention.
 */
function Attention({ lines }: { lines: string[] }) {
  return (
    <Tooltip
      label={lines.map((line) => (
        <div key={line}>{line}</div>
      ))}
      multiline
      maw={260}
      withArrow
      events={{ hover: true, focus: true, touch: true }}
    >
      <span
        className="poster-attention"
        role="img"
        tabIndex={0}
        aria-label={`Needs attention: ${lines.join("; ")}`}
      >
        !
      </span>
    </Tooltip>
  );
}

/**
 * One card: a whole series (opens its tomes/episodes), or a single title/slot (opens it).
 * Anything that needs the user has an accessible attention marker beside the caption. A card that cannot be opened (no study
 * document yet) or whose media is missing is dimmed.
 * A title's reading or viewing progress is a thin bar along the bottom of its art. [language], when
 * given, is a pill on the art's top-left corner.
 */
export function PosterCard({
  name,
  detail,
  art,
  type,
  href,
  slot,
  repositoryNames,
  onChanged,
  onLink,
  attention = [],
  language = null,
}: PosterCardProps) {
  const entry = slot?.entry;
  const lines = entry ? warnings(entry) : attention;
  const dimmed = Boolean(entry && (!href || entry.unavailable?.media));
  const progress =
    (entry?.progress ?? 0) > 0 ? Math.min(1, entry!.progress!) : null;
  const content = (
    <div className="poster-frame">
      <Art art={art} name={name} type={type} />
      {language && (
        <span className="poster-language" lang={language}>
          {nativeName(language)}
        </span>
      )}
      {progress !== null && (
        <span
          className="poster-progress"
          role="progressbar"
          aria-label="Progress"
          aria-valuenow={Math.round(progress * 100)}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <span style={{ width: `${progress * 100}%` }} />
        </span>
      )}
    </div>
  );
  return (
    <div className={`poster-card${dimmed ? " poster-unavailable" : ""}`}>
      {href ? (
        <a className="poster-link" href={href} aria-label={name}>
          {content}
        </a>
      ) : (
        <div className="poster-link">{content}</div>
      )}

      <Group gap={4} wrap="nowrap" justify="space-between" align="flex-start">
        <div className="poster-caption">
          {lines.length > 0 && <Attention lines={lines} />}
          <Text size="sm" fw={600} lineClamp={2} title={name}>
            {name}
          </Text>
          {detail && (
            <Text size="xs" c="dimmed" lineClamp={1}>
              {detail}
            </Text>
          )}
        </div>
        {slot && (
          <Group gap={0} wrap="nowrap">
            <PosterCopies slot={slot} repositoryNames={repositoryNames} />
            <PosterActions
              slot={slot}
              onChanged={onChanged!}
              onLink={onLink!}
            />
          </Group>
        )}
      </Group>
    </div>
  );
}
