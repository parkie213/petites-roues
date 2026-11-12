import { ActionIcon, Button, Menu, Tooltip } from "@mantine/core";
import {
  Ellipsis,
  Headphones,
  PanelRight,
  ScanText,
  SquareDashed,
} from "lucide-react";
import { useCallback, type Dispatch, type SetStateAction } from "react";
import type { ComicView } from "../shared/types.ts";
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
}: {
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
        variant={annotate ? "light" : "subtle"}
        aria-pressed={annotate}
        disabled={savingBox || !!draftRegion || (editing && !annotate)}
        onClick={annotations.toggleAnnotate}
        leftSection={<SquareDashed size={18} />}
      >
        Edit bubble regions
      </Button>
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
            disabled={editing}
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
