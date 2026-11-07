import { useEffect, useState } from "react";
import {
  Alert,
  Anchor,
  Box,
  Button,
  Container,
  Group,
  Loader,
  Modal,
  Paper,
  SegmentedControl,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from "@mantine/core";
import {
  BookOpen,
  Check,
  NotebookText,
  Plus,
  RefreshCw,
  TriangleAlert,
} from "lucide-react";
import { chooseOnComputer, request } from "../shared/api.js";
import { ImportTitle } from "./ImportTitle.jsx";
import { LibraryBrowser } from "./LibraryBrowser.jsx";
import { LibraryQuiz } from "./LibraryQuiz.jsx";
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
import { rememberLibraryPage } from "../shared/library-return.js";
import { Documents } from "./Documents.jsx";

export function Library() {
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
    [importing, setImporting] = useState(false),
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

  /** Registers any folder: Refresh finds its study documents, videos and comics. */
  async function addFolder() {
    setError("");
    try {
      const path = await chooseOnComputer("folder");
      if (path)
        await act(
          "/api/repositories",
          "POST",
          { localPath: path },
          "Folder added.",
        );
    } catch (e) {
      setError(e.message);
    }
  }

  function refreshLibrary() {
    act("/api/repositories/refresh", "POST", null, "Library refreshed.").catch(
      () => {},
    );
  }

  const summary = data && librarySummary(data.repos);
  return (
    <Box component="main" className="mantine-library">
      <Container size="xl" py={{ base: 28, sm: 48 }}>
        <Stack gap="xl">
          <Group justify="space-between" align="center" gap="lg">
            <Group gap="md" wrap="nowrap">
              <ThemeIcon size={52} radius="lg" variant="light">
                <BookOpen size={26} />
              </ThemeIcon>
              <Title order={1} size={32} lts={-1}>
                Petites Roues
              </Title>
            </Group>
            <Group gap="sm">
              {data?.repos.length > 0 && (
                <Button
                  component="a"
                  href="/notes"
                  variant="default"
                  size="xs"
                  leftSection={<NotebookText size={14} />}
                >
                  Notes
                </Button>
              )}
              {data?.repos.length > 0 && <LibraryQuiz />}
              {data?.repos.length > 0 && (
                <Button
                  leftSection={<Plus size={17} />}
                  disabled={busy}
                  onClick={() => setImporting(true)}
                >
                  Add title
                </Button>
              )}
              {data?.repos.length > 0 && (
                <Button
                  variant="default"
                  leftSection={<RefreshCw size={16} />}
                  loading={busy}
                  onClick={refreshLibrary}
                >
                  Refresh library
                </Button>
              )}
              <Button
                variant="default"
                leftSection={<Plus size={17} />}
                disabled={busy}
                onClick={addFolder}
              >
                Add folder
              </Button>
            </Group>
          </Group>
          <Group justify="space-between" mt="lg">
            <div>
              <Title order={2} size="h3">
                Your library
              </Title>
              {summary && (
                <Text size="sm" mt={4}>
                  {summary.counts}
                  {summary.attention && (
                    <Text span c="yellow.5">
                      {summary.counts && " · "}
                      {summary.attention}
                    </Text>
                  )}
                  {summary.unlinked && (
                    <>
                      {(summary.counts || summary.attention) && " · "}
                      <Anchor
                        href={documentsHref()}
                        c="yellow.5"
                        onClick={(event) => {
                          event.preventDefault();
                          switchView("documents");
                        }}
                      >
                        {summary.unlinked}
                      </Anchor>
                    </>
                  )}
                </Text>
              )}
              {usage && (
                <Text size="sm" c="dimmed" mt={2}>
                  {usage}
                </Text>
              )}
            </div>
            {data && (
              <Group gap="xs">
                {data.repos.length > 0 && (
                  <SegmentedControl
                    size="xs"
                    value={selection.view}
                    onChange={switchView}
                    data={[
                      { value: "columns", label: "Columns" },
                      { value: "posters", label: "Posters" },
                      { value: "documents", label: "Documents" },
                    ]}
                    aria-label="Library view"
                  />
                )}
              </Group>
            )}
          </Group>
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
                  disabled={busy}
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
              busy={busy}
              act={act}
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
              Browse folders to find your titles and study notes.
            </Text>
          )}
        </Stack>
      </Container>
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
      {importing && (
        <ImportTitle
          repos={data.repos}
          selection={selection}
          onClose={() => setImporting(false)}
          onCreated={async (created, kind) => {
            await load();
            setError("");
            setMessage(
              `Created ${created.documentPath}. Next: ${
                kind === "video"
                  ? "transcribe it with prepare/transcribe.py"
                  : "find its text with prepare/comic_text.py"
              }, then notes with prepare/notes.py (docs/PREPARE-TITLE.md).`,
            );
          }}
        />
      )}
    </Box>
  );
}
