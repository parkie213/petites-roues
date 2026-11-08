import { createPortal } from "react-dom";
import { useEffect, useState } from "react";
import {
  Alert,
  Anchor,
  Button,
  Group,
  Loader,
  Modal,
  Paper,
  Chip,
  SegmentedControl,
  Stack,
  Text,
  TextInput,
  ActionIcon,
  Title,
} from "@mantine/core";
import { Search, Check, Plus, RefreshCw, TriangleAlert } from "lucide-react";
import { chooseOnComputer, request } from "../shared/api.js";
import { LibraryBrowser } from "./LibraryBrowser.jsx";
import { JobProgress } from "./JobProgress.jsx";
import { jobSucceeded } from "./library-job.js";
import { useLibraryJob } from "./useLibraryJob.js";
import { usageLine } from "./usage.js";
import { Posters } from "./Posters.jsx";
import {
  folderHref,
  documentsHref,
  postersHref,
  readSelection,
  rememberView,
  storedView,
} from "./navigation.js";
import "./Library.css";
import { librarySummary } from "./summary.js";
import { languagesIn, rememberLanguage, storedLanguage } from "./language.js";
import { rememberLibraryPage } from "../shared/library-return.js";
import { Documents } from "./Documents.jsx";

export function Library({ actionsTarget }) {
  useEffect(() => {
    document.title = "petites roues";
  }, []);
  const [selection, setSelection] = useState(() => {
    // A bare library URL opens the last chosen view.
    if (!location.search && storedView() !== "columns")
      history.replaceState(
        null,
        "",
        storedView() === "documents" ? documentsHref() : postersHref(),
      );
    return readSelection();
  });
  function go(next) {
    if (next !== location.pathname + location.search)
      history.pushState(null, "", next);
    setSelection(readSelection());
  }
  const navigate = (repository, parts) => go(folderHref(repository, parts));
  function switchView(view) {
    rememberView(view);
    go(
      view === "posters"
        ? postersHref()
        : view === "documents"
          ? documentsHref()
          : "/",
    );
  }
  // Players and readers return to this exact page.
  useEffect(() => {
    rememberLibraryPage(location.pathname + location.search);
  }, [selection]);
  useEffect(() => {
    const update = () => setSelection(readSelection());
    window.addEventListener("popstate", update);
    return () => window.removeEventListener("popstate", update);
  }, []);
  const [data, setData] = useState(null),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [removing, setRemoving] = useState(null),
    [usage, setUsage] = useState("");
  async function load(signal) {
    const library = await request("/api/library", { signal });
    // Measured separately: walking the folders must not hold up the library itself.
    request("/api/library/usage", { signal }).then(
      (measured) => setUsage(usageLine(measured)),
      () => setUsage(""),
    );
    setData({
      repos: library.repositories.map(({ repository, entries, media }) => ({
        ...repository,
        entries,
        media,
      })),
    });
  }
  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).catch((e) => {
      if (e.name !== "AbortError") setError(e.message);
    });
    return () => controller.abort();
  }, []);
  async function act(url, method, body, success) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await request(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: body ? JSON.stringify(body) : undefined,
      });
      await load();
      setMessage(success);
      setRemoving(null);
    } catch (e) {
      setError(e.message);
      throw e;
    } finally {
      setBusy(false);
    }
  }

  // Add and Refresh run on the server in the background, streaming their progress.
  const { job, start } = useLibraryJob((finished, body) => {
    load()
      .catch((e) => setError(e.message))
      .then(() => {
        if (finished.state === "failed") setError(finished.error);
        else setMessage(jobSucceeded(finished, body));
      });
  });
  const working = busy || job != null;
  async function startJob(body) {
    setError("");
    setMessage("");
    try {
      await start(body);
    } catch (e) {
      setError(e.message);
    }
  }

  /** Registers any folder: Add finds its study documents, videos and comics. */
  async function addFolder() {
    setError("");
    try {
      const path = await chooseOnComputer("folder");
      if (path) await startJob({ kind: "add", localPath: path });
    } catch (e) {
      setError(e.message);
    }
  }

  function refreshLibrary() {
    startJob({ kind: "refresh" });
  }

  const [language, setLanguage] = useState(storedLanguage);
  const languages = data ? languagesIn(data.repos) : [];
  // A remembered language no longer in the library shows everything.
  const chosen = languages.some((item) => item.tag === language)
    ? language
    : null;
  // Only Posters follow the language. Documents is the master list and Repositories shows folders
  // as they are, so neither hides anything; the summary describes the whole library.
  const summary = data && librarySummary(data.repos);
  const [search, setSearch] = useState("");
  const [mediaFilter, setMediaFilter] = useState("all");
  const [attentionOnly, setAttentionOnly] = useState(false);
  return (
    <>
      <Stack gap="xl" mt="lg">
        {actionsTarget &&
          createPortal(
            <Group gap="sm" className="library-actions">
              {data?.repos.length > 0 && (
                <ActionIcon
                  variant="subtle"
                  color="navy"
                  size="lg"
                  aria-label="Refresh library"
                  title="Refresh library"
                  loading={working}
                  onClick={refreshLibrary}
                >
                  <RefreshCw size={21} />
                </ActionIcon>
              )}
              <Button
                color="navy"
                leftSection={<Plus size={17} />}
                disabled={working}
                onClick={addFolder}
              >
                Add folder
              </Button>
            </Group>,
            actionsTarget,
          )}
        <section className="library-toolbar" aria-label="Library controls">
          <div className="library-search-row">
            {selection.view === "posters" && (
              <TextInput
                className="library-search"
                placeholder="Search your library"
                aria-label="Search your library"
                leftSection={<Search size={18} />}
                value={search}
                onChange={(event) => setSearch(event.currentTarget.value)}
              />
            )}
            {summary && (
              <Text
                size="xs"
                c="dimmed"
                className="library-counts"
                title={usage || undefined}
              >
                {summary.counts}
              </Text>
            )}
            {data?.repos.length > 0 && (
              <SegmentedControl
                className="library-views"
                size="xs"
                value={selection.view}
                onChange={switchView}
                data={[
                  { value: "posters", label: "Posters" },
                  { value: "documents", label: "Documents" },
                  { value: "columns", label: "Repositories" },
                ]}
                aria-label="Library view"
              />
            )}
          </div>
          <div className="library-filter-row">
            {selection.view === "posters" && (
              <>
                <div
                  className="library-filters"
                  role="group"
                  aria-label="Media type"
                >
                  {[
                    ["all", "All"],
                    ["comic", "Comics"],
                    ["video", "Films"],
                  ].map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      aria-pressed={mediaFilter === value}
                      onClick={() => setMediaFilter(value)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                {summary?.attention && (
                  <button
                    type="button"
                    className="library-attention-filter"
                    aria-pressed={attentionOnly}
                    onClick={() => setAttentionOnly(!attentionOnly)}
                  >
                    <span
                      aria-hidden="true"
                      className="library-attention-dot"
                    />
                    Needs attention · {parseInt(summary.attention, 10)}
                  </button>
                )}
              </>
            )}
            {summary?.unlinked && (
              <Anchor
                size="xs"
                className="library-unlinked"
                href={documentsHref()}
                onClick={(event) => {
                  event.preventDefault();
                  switchView("documents");
                }}
              >
                {summary.unlinked}
              </Anchor>
            )}
          </div>
        </section>
        {languages.length > 1 && selection.view === "posters" && (
          <Chip.Group
            value={chosen ?? ""}
            onChange={(value) => {
              rememberLanguage(value || null);
              setLanguage(value || null);
            }}
          >
            <Group gap="xs" role="group" aria-label="Language">
              <Chip value="" size="sm" variant="light">
                All languages
              </Chip>
              {languages.map((item) => (
                <Chip
                  key={item.tag}
                  value={item.tag}
                  size="sm"
                  variant="light"
                  lang={item.tag}
                >
                  {item.label}
                </Chip>
              ))}
            </Group>
          </Chip.Group>
        )}
        {job && <JobProgress job={job} />}
        {error && (
          <Alert
            color="red"
            icon={<TriangleAlert size={18} />}
            title="Something went wrong"
            role="alert"
          >
            {error}
          </Alert>
        )}
        {message && (
          <Alert color="sage" icon={<Check size={18} />} role="status">
            {message}
          </Alert>
        )}
        {data?.repos.length === 0 ? (
          <Paper withBorder p={{ base: "xl", sm: 64 }} radius="lg">
            <Stack align="center" gap="md" ta="center">
              <Title order={2}>There's nothing here</Title>
              <Text c="dimmed" maw={460}>
                Add a folder of videos, comics or study documents, or one
                holding all three. Videos link to their study documents by
                fingerprint, wherever each one lives.
              </Text>
              <Button
                mt="sm"
                leftSection={<Plus size={17} />}
                disabled={working}
                onClick={addFolder}
              >
                Add folder
              </Button>
            </Stack>
          </Paper>
        ) : data && selection.view === "documents" ? (
          <Documents data={data} />
        ) : data && selection.view === "posters" ? (
          <Posters
            data={data}
            search={search}
            mediaFilter={mediaFilter}
            attentionOnly={attentionOnly}
            language={chosen}
            selection={selection}
            go={go}
            onChanged={(failure) => {
              if (failure) setError(failure.message);
              else load().catch((e) => setError(e.message));
            }}
          />
        ) : data ? (
          <LibraryBrowser
            data={data}
            selection={selection}
            navigate={navigate}
            busy={working}
            refresh={(repositoryId) =>
              startJob({ kind: "refresh", repositoryId })
            }
            onRemove={setRemoving}
          />
        ) : (
          !error && (
            <Group justify="center" py={80} role="status">
              <Loader size="sm" />
              <Text c="dimmed">Loading library…</Text>
            </Group>
          )
        )}
        {data?.repos.length > 0 && selection.view === "columns" && (
          <Text size="xs" c="dimmed">
            Browse each repository's folders to find your titles and study
            notes.
          </Text>
        )}
      </Stack>
      <Modal
        opened={Boolean(removing)}
        onClose={() => {
          if (!busy) setRemoving(null);
        }}
        title="Remove folder?"
        centered
        closeOnEscape={!busy}
        closeOnClickOutside={!busy}
        closeButtonProps={{ disabled: busy, "aria-label": "Close dialog" }}
      >
        <Stack>
          <Text size="sm">
            Remove{" "}
            <Text span fw={600}>
              {data?.repos.find((repo) => repo.id === removing)?.name}
            </Text>{" "}
            from the library? Its files stay on disk. Local review state for its
            titles will be removed.
          </Text>
          <Group justify="flex-end" mt="md">
            <Button
              variant="default"
              disabled={busy}
              onClick={() => setRemoving(null)}
            >
              Cancel
            </Button>
            <Button
              color="red"
              loading={busy}
              onClick={() =>
                act(
                  `/api/repositories/${removing}`,
                  "DELETE",
                  null,
                  "Folder removed. Files were left untouched.",
                ).catch(() => {})
              }
            >
              Remove from library
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
