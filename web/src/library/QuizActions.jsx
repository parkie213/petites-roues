import { useState } from "react";
import { Button, Group } from "@mantine/core";
import { Copy, Download, ExternalLink, Share2 } from "lucide-react";
import rules from "../../../agents/workflows/subtitle-quiz.md?raw";
import { generalQuizBrief, generalQuizFileName } from "./quizzes.js";

/** Hands the selected quiz to a chat or a file; no prompt leaves the page automatically. */
export function QuizActions({ quiz }) {
  const [status, setStatus] = useState("");
  const brief = () => generalQuizBrief(rules, quiz);
  async function copy() {
    try {
      await navigator.clipboard.writeText(brief());
      return true;
    } catch {
      setStatus(
        "Could not copy. Download the prompt and attach it to your chat instead.",
      );
      return false;
    }
  }
  function openChat(name, url) {
    // Open during the click so popup blockers do not reject an awaited clipboard operation.
    const chat = window.open(url, "_blank");
    if (chat) chat.opener = null;
    copy().then((ok) => {
      if (ok)
        setStatus(
          chat
            ? `Copied. Paste into ${name} and send to begin.`
            : `Copied. Your browser blocked the new tab; open ${name} and paste to begin.`,
        );
    });
  }
  function download() {
    const url = URL.createObjectURL(
      new Blob([brief()], { type: "text/markdown" }),
    );
    Object.assign(document.createElement("a"), {
      href: url,
      download: generalQuizFileName(quiz),
    }).click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setStatus("Downloaded. Attach the file to your chat.");
  }
  async function share() {
    const file = new File([brief()], generalQuizFileName(quiz), {
      type: "text/plain",
    });
    try {
      await navigator.share(
        navigator.canShare?.({ files: [file] })
          ? { files: [file] }
          : { text: brief() },
      );
    } catch (error) {
      if (error.name !== "AbortError")
        setStatus("Could not share. Try copying or downloading the prompt.");
    }
  }
  return (
    <section className="quiz-launch" aria-label="Continue in a chat">
      <h2>Continue in a chat</h2>
      <Group grow>
        <Button
          color="navy"
          rightSection={<ExternalLink size={15} />}
          onClick={() => openChat("ChatGPT", "https://chatgpt.com/")}
        >
          ChatGPT
        </Button>
        <Button
          color="navy"
          variant="outline"
          rightSection={<ExternalLink size={15} />}
          onClick={() => openChat("Claude", "https://claude.ai/new")}
        >
          Claude
        </Button>
      </Group>
      <p className="study-muted">
        We’ll copy the quiz prompt. Paste it into the new chat to begin.
      </p>
      <Group gap="sm" className="quiz-secondary">
        <Button
          variant="subtle"
          color="navy"
          leftSection={<Copy size={15} />}
          onClick={() =>
            copy().then(
              (ok) => ok && setStatus("Copied. Paste into your chat to begin."),
            )
          }
        >
          Copy prompt
        </Button>
        <Button
          variant="subtle"
          color="navy"
          leftSection={<Download size={15} />}
          onClick={download}
        >
          Download .md
        </Button>
        {typeof navigator.share === "function" && (
          <Button
            variant="subtle"
            color="navy"
            leftSection={<Share2 size={15} />}
            onClick={share}
          >
            Share
          </Button>
        )}
      </Group>
      {status && (
        <p className="quiz-status" role="status">
          {status}
        </p>
      )}
    </section>
  );
}
