import { ActionIcon, Loader, Tooltip } from "@mantine/core";
import { Square, Volume2 } from "lucide-react";
import { useReadAloud } from "./ReadAloud.jsx";

/**
 * Speaker icon that reads the text aloud on request (also the R key); pressing it again while it
 * plays stops it. A failure turns the icon red and its tooltip explains why.
 */
export function ListenButton({ text }) {
  const { text: playing, loading, error, speak } = useReadAloud();
  const active = playing === text;
  const label = error || (active ? "Stop (R)" : "Listen (R)");
  return (
    <Tooltip label={label} withArrow multiline maw={280}>
      <ActionIcon
        variant={active ? "light" : "subtle"}
        color={error && !active ? "red" : undefined}
        aria-label={active ? "Stop reading aloud" : "Listen"}
        aria-pressed={active}
        disabled={!text}
        onClick={() => speak(text)}
      >
        {active && loading ? (
          <Loader size={14} />
        ) : active ? (
          <Square size={14} />
        ) : (
          <Volume2 size={18} />
        )}
      </ActionIcon>
    </Tooltip>
  );
}
