import { useStudyLanguages } from "../StudyLanguages.jsx";
import { useMemo } from "react";
import {
  Badge,
  Button,
  Checkbox,
  Group,
  Stack,
  Text,
  TextInput,
  UnstyledButton,
} from "@mantine/core";
import { alignmentState } from "../alignment.js";
import { formatTime } from "../model.js";
import { selectTranscriptRows } from "../transcript-selection.js";
import { PlayerMenu } from "./PlayerMenu.jsx";

/** Displays filtered transcript rows and the shared selection used by merge operations. */
export function Transcript({
  cues,
  showReviewChecks,
  active,
  query,
  setQuery,
  notesOnly,
  setNotesOnly,
  checksOnly,
  setChecksOnly,
  rows,
  search,
  manualScroll,
  selectionMode,
  setSelectionMode,
  mergeSelection,
  setMergeSelection,
  selectionAnchor,
  merging,
  actionBusy,
  editor,
  dirty,
  mergeSelected,
  setSelected,
  pause,
  seek,
}) {
  const { sourceLanguage, translationLanguage } = useStudyLanguages();
  const renderedRows = useMemo(
    () =>
      cues.map((line, index) => {
        const teaching =
          alignmentState(line) === "ready"
            ? line.alignment.links.map((link) => link.note)
            : line.notes;
        const hay = [
          line.source,
          line.translation,
          line.listeningCheck,
          line.textCheck,
          ...teaching,
        ]
          .join(" ")
          .toLocaleLowerCase();
        if (
          (query && !hay.includes(query.toLocaleLowerCase())) ||
          (notesOnly && !teaching.length) ||
          (checksOnly &&
            !line.listeningCheck &&
            !line.textCheck &&
            alignmentState(line) !== "stale")
        )
          return null;
        return (
          <UnstyledButton
            key={line.id}
            ref={(element) => {
              if (element) rows.current.set(line.id, element);
              else rows.current.delete(line.id);
            }}
            className={
              "cue-row" +
              (index === active ? " current" : "") +
              (mergeSelection.has(line.id) ? " merge-selected" : "")
            }
            aria-current={index === active ? "true" : undefined}
            aria-pressed={mergeSelection.has(line.id)}
            disabled={merging || actionBusy || Boolean(editor)}
            onClick={(event) => {
              if (
                event.ctrlKey ||
                event.metaKey ||
                event.shiftKey ||
                selectionMode
              ) {
                const ids = cues
                  .filter((c) => rows.current.has(c.id))
                  .map((c) => c.id);
                setMergeSelection((old) =>
                  selectTranscriptRows(
                    ids,
                    old,
                    selectionAnchor.current,
                    line.id,
                    { toggle: true, range: event.shiftKey },
                  ),
                );
                if (!event.shiftKey || !selectionAnchor.current)
                  selectionAnchor.current = line.id;
                return;
              }
              selectionAnchor.current = line.id;
              setMergeSelection(new Set());
              setSelected(index);
              pause();
              seek(line.start);
            }}
          >
            <time>{formatTime(line.start).slice(0, -4)}</time>
            <span className="transcript-copy">
              <span className={line.continuesPrevious ? "continuation" : ""}>
                <span lang={sourceLanguage}>{line.source}</span>
              </span>
              {line.translation && (
                <span
                  className="transcript-translation"
                  lang={translationLanguage}
                >
                  {line.translation}
                </span>
              )}
              {(line.continuesPrevious ||
                (showReviewChecks && line.listeningCheck) ||
                line.textCheck ||
                alignmentState(line) === "stale") && (
                <Group component="span" gap={4}>
                  {" "}
                  {line.continuesPrevious && (
                    <Badge size="xs" variant="light" color="gray">
                      cont
                    </Badge>
                  )}
                  {showReviewChecks && line.listeningCheck && (
                    <Badge size="xs" variant="light" color="yellow">
                      Listening check
                    </Badge>
                  )}
                  {line.textCheck && (
                    <Badge size="xs" variant="light" color="yellow">
                      Text check
                    </Badge>
                  )}
                  {alignmentState(line) === "stale" && (
                    <Badge size="xs" variant="light" color="yellow">
                      Regenerate phrase links
                    </Badge>
                  )}
                </Group>
              )}
            </span>
          </UnstyledButton>
        );
      }),
    [
      cues,
      active,
      query,
      notesOnly,
      checksOnly,
      showReviewChecks,
      selectionMode,
      editor,
      mergeSelection,
      merging,
      actionBusy,
      dirty,
    ],
  );

  return (
    <aside className="right" aria-label="Transcript">
      <Stack gap="xs" p="sm" className="list-header">
        <>
          <TextInput
            ref={search}
            aria-label="Search subtitles and notes"
            placeholder="search text/notes…"
            size="xs"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <Group gap="xs">
            <PlayerMenu
              label={notesOnly || checksOnly ? "Filters •" : "Filters"}
            >
              <Checkbox
                label="With teaching notes"
                size="xs"
                checked={notesOnly}
                onChange={(event) => setNotesOnly(event.currentTarget.checked)}
              />
              <Checkbox
                label="Needs review"
                size="xs"
                checked={checksOnly}
                onChange={(event) => setChecksOnly(event.currentTarget.checked)}
              />
            </PlayerMenu>
            <Button
              size="xs"
              variant={selectionMode ? "light" : "default"}
              aria-pressed={selectionMode}
              onClick={() => {
                setSelectionMode(!selectionMode);
                setMergeSelection(new Set());
              }}
            >
              Select
            </Button>
          </Group>
        </>
        <Text size="xs" c="dimmed">
          {cues.length} lines
          {cues.some((c) => c.translation != null)
            ? " · translation+notes"
            : ""}
        </Text>
      </Stack>
      {(mergeSelection.size > 0 || selectionMode) && (
        <Group gap="xs" p="xs" bg="dark.6">
          <span>
            {mergeSelection.size
              ? mergeSelection.size + " selected"
              : "Choose subtitles"}
          </span>
          {mergeSelection.size > 1 && (
            <Button
              size="xs"
              variant="default"
              disabled={merging || Boolean(editor)}
              onClick={mergeSelected}
            >
              Merge {mergeSelection.size} subtitles
            </Button>
          )}
          <Button
            size="xs"
            variant="default"
            disabled={merging}
            onClick={() => {
              setMergeSelection(new Set());
              setSelectionMode(false);
              selectionAnchor.current = null;
            }}
          >
            Cancel selection
          </Button>
        </Group>
      )}
      <Text size="10px" c="dimmed" px="sm" py={6} m={0}>
        Click to seek · Ctrl/⌘-click to select · Shift-click for a range
      </Text>
      <div
        className="cue-list"
        onScroll={() => {
          manualScroll.current = performance.now() + 4000;
        }}
      >
        {renderedRows}
      </div>
    </aside>
  );
}
