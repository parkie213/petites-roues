import { useState } from "react";
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
import { currentCue } from "../model.js";
import {
  QUIZ_LINES,
  QUIZ_TYPES,
  quizBrief,
  quizFileName,
  quizType,
} from "../quiz.js";
import { useStoredState } from "../../shared/storage.js";

const chats = [
  { name: "ChatGPT", url: "https://chatgpt.com/" },
  { name: "Claude", url: "https://claude.ai/new" },
];

/** The video players' "Quiz me": the last lines watched, as on the phone player. */
export function VideoQuizShare({ doc, cues, time }) {
  const current = currentCue(cues, time);
  return (
    <QuizShare
      doc={doc}
      fileName={() => quizFileName(doc)}
      brief={(rules, type) => quizBrief(rules, doc, cues, current, type)}
      scopeNote={`Uses the last ${QUIZ_LINES} lines you watched.`}
    />
  );
}

/**
 * "Quiz me": a dialog that builds a quiz brief (the quiz rules from the repository's agents/, the
 * chosen quiz type's instructions and the title's real lines) and hands it to a chat app.
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
  scopes,
  scopeNote = "",
  disabled = false,
  reason = "",
  onOpen,
}) {
  const [opened, setOpened] = useState(false);
  const [status, setStatus] = useState("");
  const [typeId, setTypeId] = useStoredState("quiz.type", QUIZ_TYPES[0].id);
  const [scope, setScope] = useState(null);
  const chosen = scope ?? scopes?.initial ?? null;
  const type = withTypes ? quizType(types, typeId, doc) : null;
  const brief = () => build(rules, type, chosen);
  const name = () => fileName(chosen);
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
          {scopes ? (
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
          )}
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
          <Stack gap={6}>
            <Text size="xs" c="dimmed">
              The quiz is too long for a link, so these buttons copy it and open
              a new chat: paste it there (Ctrl+V) and send.
            </Text>
            <Group gap={6}>
              {chats.map((chat) => (
                <Button
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
                onClick={() => copy("Copied: paste it into a chat.")}
              >
                Copy
              </Button>
              <Button size="compact-sm" variant="default" onClick={download}>
                Download
              </Button>
              {typeof navigator.share === "function" && (
                <Button size="compact-sm" variant="default" onClick={share}>
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
