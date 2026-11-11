import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Group,
  Modal,
  Radio,
  Select,
  Stack,
  Text,
  TextInput,
  Tooltip,
} from "@mantine/core";
import { Lightbulb } from "lucide-react";
import rules from "../../../../agents/workflows/subtitle-quiz.md?raw";
import types from "../../../../agents/workflows/quiz-types.md?raw";
import {
  clock,
  parseQuizTime,
  QUIZ_TYPES,
  quizBrief,
  quizFileName,
  quizType,
} from "../quiz.js";
import { useStoredState } from "../../shared/storage.ts";

const chats = [
  { name: "ChatGPT", url: "https://chatgpt.com/" },
  { name: "Claude", url: "https://claude.ai/new" },
];

/** Exports a whole episode or the learner's chosen playback interval. */
export function VideoQuizShare({ doc, cues, time }) {
  const [scope, setScope] = useState("whole");
  const [from, setFrom] = useState("0:00");
  const [to, setTo] = useState("0:00");
  const start = parseQuizTime(from);
  const end = parseQuizTime(to);
  const invalid =
    scope === "range" && (start === null || end === null || end <= start);
  const range = scope === "range" ? { start, end } : null;
  const empty =
    !invalid &&
    !cues.some(
      (cue) =>
        cue.source?.trim() && (!range || (cue.start < end && cue.end > start)),
    );
  return (
    <QuizShare
      doc={doc}
      fileName={() =>
        quizFileName(doc, range ? `${clock(start)} to ${clock(end)}` : "")
      }
      brief={(rules, type) => quizBrief(rules, doc, cues, range, type)}
      onOpen={() => {
        setFrom("0:00");
        setTo(clock(time));
      }}
      exportDisabled={invalid || empty}
      scopeContent={
        <Stack gap="sm">
          <Radio.Group label="Quiz scope" value={scope} onChange={setScope}>
            <Stack gap={6} mt={6}>
              <Radio value="whole" label="Whole episode" />
              <Radio value="range" label="Time range" />
            </Stack>
          </Radio.Group>
          {scope === "range" && (
            <>
              <Group grow align="flex-start">
                <TextInput
                  label="From"
                  value={from}
                  onChange={(event) => setFrom(event.currentTarget.value)}
                  placeholder="0:00"
                />
                <TextInput
                  label="To"
                  value={to}
                  onChange={(event) => setTo(event.currentTarget.value)}
                  placeholder="30:00"
                />
              </Group>
              <Text size="xs" c={invalid ? "red" : "dimmed"}>
                {invalid
                  ? "Enter valid times with To later than From."
                  : "Use M:SS or H:MM:SS."}
              </Text>
            </>
          )}
          {empty && (
            <Text size="xs" c="red">
              No subtitle lines in this selection.
            </Text>
          )}
        </Stack>
      }
    />
  );
}

/**
 * "Quiz me": a dialog that builds a quiz brief (the quiz rules from the repository's agents/, the
 * chosen quiz type's instructions and the title's real lines) and hands it to a chat app.
 * [prepare], when supplied, asynchronously prepares {text, fileName} before enabling export,
 * cancelling stale selections and preserving clipboard/share user gestures. Otherwise
 * [brief] builds it from the rules, the chosen type and the chosen [scopes] option (such as a
 * comic chapter, or a general quiz); the dialog offers no scope choice when [scopes] is absent, and
 * reports the chosen option to [scopes.onChange] (to remember it). With [withTypes] false there is
 * no quiz type choice and [brief] receives a null type. The brief is too
 * long for a prefilled link, so ChatGPT and Claude open a new chat after copying it for pasting;
 * Download saves it to attach, and Share uses the system share sheet (with the file where
 * supported), titled after [subject] (the document's title by default). The chosen type is
 * remembered in this browser. When [disabled], the button explains why in a tooltip. [onOpen] is
 * called when the dialog opens.
 */
export function QuizShare({
  doc,
  subject = doc?.title,
  withTypes = true,
  fileName,
  brief: build,
  prepare,
  scopes,
  scopeNote = "",
  scopeContent,
  exportDisabled = false,
  disabled = false,
  reason = "",
  onOpen,
}) {
  const [opened, setOpened] = useState(false);
  const [status, setStatus] = useState("");
  const [typeId, setTypeId] = useStoredState("quiz.type", QUIZ_TYPES[0].id);
  const [scope, setScope] = useState(null);
  const chosen = scope ?? scopes?.initial ?? null;
  const type = useMemo(
    () => (withTypes ? quizType(types, typeId, doc) : null),
    [withTypes, typeId, doc],
  );
  const [prepared, setPrepared] = useState(null);
  const [attempt, setAttempt] = useState(0);
  const [opening, setOpening] = useState(0);
  useEffect(() => {
    if (!opened || !prepare) return;
    let cancelled = false;
    const controller = new AbortController();
    const selection = { prepare, type, chosen, attempt, opening };
    Promise.resolve()
      .then(() => prepare(rules, type, chosen, controller.signal))
      .then(
        (data) => {
          if (!cancelled) setPrepared({ ...selection, data });
        },
        (error) => {
          if (!cancelled) setPrepared({ ...selection, error });
        },
      );
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [opened, prepare, type, chosen, attempt, opening]);
  const current =
    prepared?.prepare === prepare &&
    prepared?.type === type &&
    prepared?.chosen === chosen &&
    prepared?.attempt === attempt &&
    prepared?.opening === opening;
  const ready = current && prepared?.data;
  const prepareError = current && prepared?.error;
  const blocked = exportDisabled || Boolean(prepare && !ready);
  const brief = () => (prepare ? ready.text : build(rules, type, chosen));
  const name = () => (prepare ? ready.fileName : fileName(chosen));
  const copy = (done) =>
    navigator.clipboard.writeText(brief()).then(
      () => setStatus(done),
      () => setStatus("Could not copy: your browser blocked the clipboard."),
    );
  const download = () => {
    const url = URL.createObjectURL(
      new Blob([brief()], { type: "text/markdown" }),
    );
    const link = Object.assign(document.createElement("a"), {
      href: url,
      download: name(),
    });
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setStatus("Downloaded: attach it to a chat.");
  };
  const share = () => {
    const file = new File([brief()], name(), { type: "text/plain" });
    const data = navigator.canShare?.({ files: [file] })
      ? {
          files: [file],
          text: `Quiz me on ${subject} using the attached file.`,
        }
      : { text: brief() };
    navigator.share(data).catch(() => {});
  };
  return (
    <>
      <Tooltip label={reason} disabled={!disabled || !reason} withArrow>
        <Button
          size="xs"
          variant="default"
          leftSection={<Lightbulb size={14} />}
          aria-haspopup="dialog"
          // Styled as disabled but still hoverable, so the tooltip can say why.
          data-disabled={disabled || undefined}
          aria-disabled={disabled || undefined}
          onClick={() => {
            if (disabled) return;
            setStatus("");
            setScope(null);
            setOpening((value) => value + 1);
            setOpened(true);
            onOpen?.();
          }}
        >
          Quiz me
        </Button>
      </Tooltip>
      <Modal
        opened={opened}
        onClose={() => setOpened(false)}
        title="Quiz me"
        size="md"
        portalProps={{ target: document.fullscreenElement || document.body }}
      >
        <Stack gap="md">
          {scopeContent ||
            (scopes ? (
              <Select
                label={scopes.label}
                data={scopes.options}
                value={chosen}
                onChange={(value) => {
                  if (!value) return;
                  setScope(value);
                  setStatus("");
                  scopes.onChange?.(value);
                }}
                allowDeselect={false}
                comboboxProps={{ withinPortal: false }}
              />
            ) : (
              <Text size="sm">{scopeNote}</Text>
            ))}
          {type && (
            <Radio.Group
              label="Quiz type"
              value={type.id}
              onChange={(value) => {
                setTypeId(value);
                setStatus("");
              }}
            >
              <Stack gap={6} mt={6}>
                {QUIZ_TYPES.map((t) => (
                  <Radio key={t.id} value={t.id} label={t.label(doc)} />
                ))}
              </Stack>
            </Radio.Group>
          )}
          {prepare &&
            !ready &&
            (prepareError ? (
              <Group>
                <Text size="xs" c="red" role="alert">
                  Could not prepare this quiz.
                </Text>
                <Button
                  size="compact-xs"
                  variant="subtle"
                  onClick={() => setAttempt((value) => value + 1)}
                >
                  Retry
                </Button>
              </Group>
            ) : (
              <Text size="xs" c="dimmed" role="status">
                Preparing quiz…
              </Text>
            ))}
          <Stack gap={6}>
            <Text size="xs" c="dimmed">
              The quiz is too long for a link, so these buttons copy it and open
              a new chat: paste it there (Ctrl+V) and send.
            </Text>
            <Group gap={6}>
              {chats.map((chat) => (
                <Button
                  disabled={blocked}
                  key={chat.name}
                  size="compact-sm"
                  variant="light"
                  onClick={() => {
                    const copied = copy(
                      `Copied: paste it into the new ${chat.name} chat (Ctrl+V) and send.`,
                    );
                    window.open(chat.url, "_blank", "noopener");
                    return copied;
                  }}
                >
                  Copy & open {chat.name} ↗
                </Button>
              ))}
              <Button
                size="compact-sm"
                variant="default"
                disabled={blocked}
                onClick={() => copy("Copied: paste it into a chat.")}
              >
                Copy
              </Button>
              <Button
                size="compact-sm"
                variant="default"
                disabled={blocked}
                onClick={download}
              >
                Download
              </Button>
              {typeof navigator.share === "function" && (
                <Button
                  size="compact-sm"
                  variant="default"
                  disabled={blocked}
                  onClick={share}
                >
                  Share…
                </Button>
              )}
            </Group>
            {status && (
              <Text size="xs" c="dimmed" role="status">
                {status}
              </Text>
            )}
          </Stack>
        </Stack>
      </Modal>
    </>
  );
}
