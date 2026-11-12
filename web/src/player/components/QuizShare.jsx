import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Group,
  Modal,
  Radio,
  Select,
  Stack,
  Text,
  Tooltip,
} from "@mantine/core";
import { Lightbulb } from "lucide-react";
import rules from "../../../../agents/workflows/subtitle-quiz.md?raw";
import types from "../../../../agents/workflows/quiz-types.md?raw";
import { QUIZ_TYPES, quizType } from "../quiz.js";
import { useStoredState } from "../../shared/storage.ts";

const chats = [
  { name: "ChatGPT", url: "https://chatgpt.com/" },
  { name: "Claude", url: "https://claude.ai/new" },
];

/**
 * "Quiz me": a dialog that builds a quiz brief (the quiz rules from the repository's agents/, the
 * chosen quiz type's instructions and the title's real lines) and hands it to a chat app.
 * [prepare], when supplied, asynchronously prepares {text, fileName} before enabling export,
 * cancelling stale selections and preserving clipboard/share user gestures. Otherwise
 * [brief] builds it from the rules, the chosen type and the chosen [scopes] option (such as a
 * comic chapter, or a general quiz); the picker reports its choice to [scopes.onChange]
 * (to remember it). With [withTypes] false there is no quiz type choice and [brief] receives a
 * null type. The brief is too long for a prefilled link, so ChatGPT and Claude open a new chat
 * after copying it for pasting;
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
  disabled = false,
  reason = "",
  onOpen,
}) {
  const [opened, setOpened] = useState(false);
  const [status, setStatus] = useState("");
  const [typeId, setTypeId] = useStoredState("quiz.type", QUIZ_TYPES[0].id);
  const [scope, setScope] = useState(null);
  const chosen = scope ?? scopes.initial ?? null;
  const type = useMemo(
    () => (withTypes ? quizType(types, typeId, doc) : null),
    [withTypes, typeId, doc],
  );
  const [prepared, setPrepared] = useState(null);
  // Opening and retrying both request a fresh preparation of the current selection.
  const [revision, setRevision] = useState(0);
  const selection = useMemo(
    () => ({ prepare, type, chosen, revision }),
    [prepare, type, chosen, revision],
  );
  useEffect(() => {
    const { prepare, type, chosen } = selection;
    if (!opened || !prepare) return;
    const controller = new AbortController();
    Promise.resolve()
      .then(() => prepare(rules, type, chosen, controller.signal))
      .then(
        (data) => {
          if (!controller.signal.aborted) setPrepared({ selection, data });
        },
        (error) => {
          if (!controller.signal.aborted) setPrepared({ selection, error });
        },
      );
    return () => controller.abort();
  }, [opened, selection]);
  // Match during render too: a changed selection must disable exports before its effect runs.
  const current = prepared?.selection === selection;
  const ready = current && prepared?.data;
  const prepareError = current && prepared?.error;
  const blocked = Boolean(prepare && !ready);
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
            setRevision((value) => value + 1);
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
                  onClick={() => setRevision((value) => value + 1)}
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
