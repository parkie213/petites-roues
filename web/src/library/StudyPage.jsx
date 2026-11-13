import { appLocation, appHistory, appHref } from "../shared/app-location.js";
import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Group,
  Loader,
  TextInput,
  Typography,
} from "@mantine/core";
import { Search } from "lucide-react";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { request } from "../shared/api.ts";
import { notePath } from "./notes.js";
import { quizKey } from "./quizzes.js";
import { QuizActions } from "./QuizActions.jsx";
import "./Library.css";
import "./Notes.css";

function selectedKey(kind) {
  if (kind === "quizzes")
    return new URLSearchParams(appLocation().search).get("topic");
  const parts = appLocation()
    .pathname.split("/")
    .filter(Boolean)
    .slice(1)
    .map(decodeURIComponent);
  return parts.length === 2 ? parts.join("/") : null;
}

/** Shared read-only topic browser. Selection has a URL and follows browser Back/Forward. */
export function StudyPage({ kind }) {
  const quiz = kind === "quizzes";
  const title = quiz ? "Quiz me" : "Notes";
  const [items, setItems] = useState(null);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState(() => selectedKey(kind));
  const [browse, setBrowse] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    request(`/api/${kind}`, { signal: controller.signal }).then(
      setItems,
      (e) => {
        if (e.name !== "AbortError") setError(e.message);
      },
    );
    const update = () => setSelected(selectedKey(kind));
    window.addEventListener("popstate", update);
    return () => {
      controller.abort();
      window.removeEventListener("popstate", update);
    };
  }, [kind]);
  const current =
    items &&
    (selected ? items.find((item) => quizKey(item) === selected) : items[0]);
  useEffect(() => {
    document.title = current ? `${current.title} · ${title}` : title;
  }, [current, title]);
  const filtered = (items || []).filter((item) =>
    item.title.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()),
  );
  const several =
    new Set((items || []).map((item) => item.repository)).size > 1;
  return (
    <>
      {error && (
        <Alert color="red" role="alert" mt="lg">
          {error}
        </Alert>
      )}
      {!items && !error && (
        <Group justify="center" py={80}>
          <Loader aria-label={`Loading ${title}`} />
        </Group>
      )}
      {items && (
        <>
          <Button
            className="study-browse"
            variant="subtle"
            onClick={() => setBrowse(!browse)}
            aria-expanded={browse}
          >
            {" "}
            {browse
              ? "Back to reading"
              : quiz
                ? "Browse quizzes"
                : "Browse notes"}
          </Button>
          <div className={`study-layout${browse ? " is-browsing" : ""}`}>
            <aside
              className="study-sidebar"
              aria-label={quiz ? "Quiz topics" : "Study notes"}
            >
              <div className="study-sidebar-heading">
                <h2>{title}</h2>
                <span>
                  {items.length} {quiz ? "topics" : "notes"}
                </span>
              </div>
              <TextInput
                aria-label={quiz ? "Find a quiz" : "Find a note"}
                placeholder={quiz ? "Find a quiz" : "Find a note"}
                leftSection={<Search size={16} />}
                value={search}
                onChange={(e) => setSearch(e.currentTarget.value)}
              />
              <nav
                className="study-topics"
                aria-label={quiz ? "Choose a quiz" : "Choose a note"}
              >
                {filtered.map((item) => (
                  <a
                    key={quizKey(item)}
                    href={appHref(
                      quiz
                        ? `/quizzes?topic=${encodeURIComponent(quizKey(item))}`
                        : notePath(item),
                    )}
                    aria-current={current === item ? "page" : undefined}
                    onClick={(e) => {
                      if (
                        e.button ||
                        e.metaKey ||
                        e.ctrlKey ||
                        e.shiftKey ||
                        e.altKey
                      )
                        return;
                      e.preventDefault();
                      appHistory(e.currentTarget.href);
                      setSelected(quizKey(item));
                      setBrowse(false);
                    }}
                  >
                    {item.title}
                    {several && <small>{item.repositoryName}</small>}
                  </a>
                ))}
                {!filtered.length && (
                  <p className="study-muted">
                    {items.length
                      ? "No matching topics."
                      : quiz
                        ? "No quizzes yet. Add Markdown files to a repository’s quizzes/ folder."
                        : "No notes yet. Add Markdown files to a repository’s notes/ folder."}
                  </p>
                )}
              </nav>
            </aside>
            <article
              className="study-reading"
              key={current && quizKey(current)}
            >
              {current ? (
                <>
                  <div className="study-breadcrumb">
                    {title} / {current.repositoryName}
                  </div>
                  {quiz && (
                    <>
                      <h1>{current.title}</h1>
                      <p className="study-muted">
                        Practise with a chat, one question at a time.
                      </p>
                      <QuizActions quiz={current} />
                    </>
                  )}
                  <Typography className="note-page">
                    <Markdown remarkPlugins={[remarkGfm]}>
                      {quiz &&
                      current.text
                        .split("\n")[0]
                        .replace(/^#\s+/, "")
                        .trim() === current.title
                        ? current.text.slice(current.text.indexOf("\n") + 1)
                        : current.text}
                    </Markdown>
                  </Typography>
                </>
              ) : (
                <p className="study-muted">
                  {selected
                    ? "This topic is no longer available. Choose another from the list."
                    : "Choose a topic when one is available."}
                </p>
              )}
            </article>
          </div>
        </>
      )}
    </>
  );
}
