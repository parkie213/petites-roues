import { isDemo } from "../shared/app-location.js";
import {
  ActionIcon,
  Button,
  Menu,
  Tooltip,
  Popover,
  Text,
} from "@mantine/core";
import {
  Ellipsis,
  Headphones,
  HelpCircle,
  Trash2,
  PanelRight,
  ScanText,
  SquareDashed,
} from "lucide-react";
import { useCallback, type Dispatch, type SetStateAction } from "react";
import type { ComicBoxView, ComicView } from "../shared/types.ts";
import { QuizShare } from "../player/components/QuizShare.jsx";
import { ComicToolbar, ToolbarToggle, ViewTools } from "./ComicFrame.jsx";
import type { useComicView } from "./ComicFrame.jsx";
import { chapterAt, chapterName } from "./navigation.js";
import { prepareChapterQuiz } from "./quiz.js";
import type { useComicAnnotations } from "./useComicAnnotations.ts";

type Toggle = Dispatch<SetStateAction<boolean>>;

/** Chapter navigation, view preferences, quiz scope and annotation tools for the study reader. */
export function ComicReaderToolbar({
  id,
  document,
  index,
  view,
  autoListen,
  setAutoListen,
  studyOpen,
  setStudyOpen,
  editing,
  debug,
  setDebug,
  annotations,
  selectedBox,
  onDeleteSelected,
}: {
  selectedBox: ComicBoxView | undefined;
  onDeleteSelected: () => void;
  id: string;
  document: ComicView;
  index: number;
  view: ReturnType<typeof useComicView>;
  autoListen: boolean;
  setAutoListen: Toggle;
  studyOpen: boolean;
  setStudyOpen: Toggle;
  editing: boolean;
  debug: boolean;
  setDebug: Toggle;
  annotations: ReturnType<typeof useComicAnnotations>;
}) {
  const prepareQuiz = useCallback(
    (
      rules: string,
      type: { label: string; instructions: string } | null,
      chapter: string,
      signal: AbortSignal,
    ) =>
      prepareChapterQuiz(
        id,
        document.chapters![Number(chapter)].pageId,
        rules,
        type,
        signal,
      ),
    [id, document],
  );
  const { locked } = view;
  const { annotate, savingBox, draftRegion } = annotations;
  const chapters = document.chapters ?? [];
  const chapter = chapterAt(document.pages, chapters, index);
  const chapterStart = (c: number) =>
    document.pages.findIndex((p) => p.id === chapters[c].pageId);
  return (
    <ComicToolbar
      title={document.title}
      middle={
        chapters.length > 0 && (
          <select
            className="comic-page-select comic-chapter-select"
            aria-label="Chapter"
            title="Go to a chapter"
            disabled={locked}
            value={chapter}
            onChange={(event) =>
              view.go(chapterStart(Number(event.target.value)))
            }
          >
            {chapter < 0 && (
              <option value={-1} disabled>
                Before the first chapter
              </option>
            )}
            {chapters.map((c, i) => (
              <option key={c.pageId} value={i}>
                {chapterName(c)}
              </option>
            ))}
          </select>
        )
      }
    >
      <ToolbarToggle
        label={autoListen ? "Stop listening on hover" : "Listen on hover"}
        disabled={false}
        pressed={autoListen}
        onClick={() => setAutoListen((value) => !value)}
        icon={<Headphones size={18} />}
      />
      <ViewTools view={view} />
      <Button
        size="compact-sm"
        className="comic-region-edit-toggle"
        variant={annotate ? "light" : "subtle"}
        aria-pressed={annotate}
        disabled={isDemo || savingBox || !!draftRegion || editing}
        onClick={annotations.toggleAnnotate}
        leftSection={<SquareDashed size={18} />}
      >
        <span className="comic-region-edit-label">
          <span
            style={{ visibility: annotate ? "hidden" : "visible" }}
            aria-hidden={annotate}
          >
            Edit mode
          </span>
          <span
            style={{ visibility: annotate ? "visible" : "hidden" }}
            aria-hidden={!annotate}
          >
            Exit edit mode
          </span>
        </span>
      </Button>
      <Tooltip
        label={
          selectedBox && selectedBox.lines.length > 1
            ? "Delete selected text block (Delete)"
            : "Delete selected bubble (Delete)"
        }
      >
        <ActionIcon
          variant="subtle"
          color="red"
          aria-label={
            selectedBox && selectedBox.lines.length > 1
              ? "Delete selected text block"
              : "Delete selected bubble"
          }
          disabled={
            !annotate || !selectedBox || savingBox || editing || !!draftRegion
          }
          onClick={onDeleteSelected}
        >
          <Trash2 size={16} />
        </ActionIcon>
      </Tooltip>
      <Popover width={280} position="bottom-end" withArrow>
        <Popover.Target>
          <ActionIcon variant="subtle" aria-label="Editing help">
            <HelpCircle size={18} />
          </ActionIcon>
        </Popover.Target>
        <Popover.Dropdown>
          <Text size="sm">
            Turn on Edit mode. Drag a region to move it; select it and drag its
            corners to resize. Drag on empty page space to add a bubble. Press
            Delete to remove the selected box. Use Edit text in the side panel
            to correct transcription, then Save text or Cancel. Region changes
            save automatically; text corrections save when you choose Save text.
            Both are queued for review.
          </Text>
        </Popover.Dropdown>
      </Popover>
      <QuizShare
        doc={document}
        disabled={chapters.length === 0}
        reason="This volume has no chapters listed yet"
        scopes={{
          label: "Chapter",
          initial: String(Math.max(chapter, 0)),
          options: chapters.map((c, i) => ({
            value: String(i),
            label: chapterName(c),
          })),
        }}
        fileName={undefined}
        brief={undefined}
        onOpen={undefined}
        prepare={prepareQuiz}
      />
      <Menu width={230} position="bottom-end" shadow="md">
        <Menu.Target>
          <Tooltip label="More options" withArrow>
            <ActionIcon variant="subtle" size="lg" aria-label="More options">
              <Ellipsis size={18} />
            </ActionIcon>
          </Tooltip>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item
            leftSection={<PanelRight size={16} />}
            rightSection={studyOpen ? "✓" : null}
            disabled={editing || annotate}
            onClick={() => setStudyOpen((value) => !value)}
          >
            Study panel
          </Menu.Item>
          <Menu.Item
            leftSection={<ScanText size={16} />}
            rightSection={debug ? "✓" : null}
            onClick={() => setDebug((value) => !value)}
          >
            Show bubble outlines
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
    </ComicToolbar>
  );
}
