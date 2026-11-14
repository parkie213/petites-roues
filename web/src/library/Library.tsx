import { Group, Loader, Stack, Text } from "@mantine/core";
import { useEffect, useState } from "react";
import { SpeechVoices } from "./SpeechVoices.tsx";
import { ConnectPhone } from "./ConnectPhone.jsx";
import { Documents } from "./Documents.jsx";
import { LibraryBrowser } from "./LibraryBrowser.tsx";
import { Posters } from "./Posters.tsx";
import { EmptyLibrary } from "./components/EmptyLibrary.tsx";
import { LanguageChips } from "./components/LanguageChips.tsx";
import { LibraryActions } from "./components/LibraryActions.tsx";
import { LibraryStatus } from "./components/LibraryStatus.tsx";
import {
  LibraryToolbar,
  NO_FILTERS,
  type PosterFilters,
} from "./components/LibraryToolbar.tsx";
import { RemoveFolderDialog } from "./components/RemoveFolderDialog.tsx";
import { languagesIn, rememberLanguage, storedLanguage } from "./language.ts";
import { librarySummary } from "./summary.ts";
import { useLibrary } from "./useLibrary.ts";
import { useLibraryLocation } from "./useLibraryLocation.ts";
import "./Library.css";

/**
 * The library page: every registered folder's titles as posters, as a list of study documents, or
 * as folders, chosen by the URL. Its actions go in the page header at [actionsTarget].
 */
export function Library({ actionsTarget }: { actionsTarget?: Element | null }) {
  useEffect(() => {
    document.title = "petites roues";
  }, []);
  const { selection, go, navigate, switchView } = useLibraryLocation();
  const library = useLibrary();
  const { data, working } = library;
  const [filters, setFilters] = useState<PosterFilters>(NO_FILTERS);
  const [language, setLanguage] = useState(storedLanguage);
  const [voicesOpened, setVoicesOpened] = useState(false);
  const [connecting, setConnecting] = useState(false);

  const hasFolders = (data?.repos.length ?? 0) > 0;
  const languages = data ? languagesIn(data.repos) : [];
  // A remembered language no longer in the library shows everything.
  const chosen = languages.some((item) => item.tag === language)
    ? language
    : null;
  // Only Posters follow the language. Documents is the master list and Repositories shows folders
  // as they are, so neither hides anything; the summary describes the whole library.
  const summary = data ? librarySummary(data.repos) : undefined;

  return (
    <>
      <SpeechVoices
        opened={voicesOpened}
        onClose={() => setVoicesOpened(false)}
      />
      <ConnectPhone opened={connecting} onClose={() => setConnecting(false)} />
      <Stack gap="xl" mt="lg">
        <LibraryActions
          target={actionsTarget}
          canRefresh={hasFolders}
          working={working}
          onVoices={() => setVoicesOpened(true)}
          onConnect={() => setConnecting(true)}
          onRefresh={library.refreshLibrary}
          onAdd={library.addFolder}
          hasPendingEdits={library.pendingEdits}
          onMenuOpen={library.checkPendingEdits}
          onReset={library.resetLibrary}
        />
        <LibraryToolbar
          view={selection.view}
          summary={summary}
          usage={library.usage}
          canSwitch={hasFolders}
          onView={switchView}
          filters={filters}
          onFilters={setFilters}
        />
        {languages.length > 1 && selection.view === "posters" && (
          <LanguageChips
            languages={languages}
            value={chosen}
            onChange={(tag) => {
              rememberLanguage(tag);
              setLanguage(tag);
            }}
          />
        )}
        <LibraryStatus
          job={library.job}
          error={library.error}
          message={library.message}
        />
        {data?.repos.length === 0 ? (
          <EmptyLibrary working={working} onAdd={library.addFolder} />
        ) : data && selection.view === "documents" ? (
          <Documents data={data} onChanged={library.changed} />
        ) : data && selection.view === "posters" ? (
          <Posters
            data={data}
            search={filters.search}
            mediaFilter={filters.media}
            attentionOnly={filters.attentionOnly}
            language={chosen}
            selection={selection}
            go={go}
            onChanged={library.changed}
          />
        ) : data ? (
          <LibraryBrowser
            data={data}
            selection={selection}
            navigate={navigate}
            busy={working}
            refresh={library.refreshFolder}
            onRemove={library.askToRemove}
            onChanged={library.changed}
          />
        ) : (
          !library.error && (
            <Group justify="center" py={80} role="status">
              <Loader size="sm" />
              <Text c="dimmed">Loading library…</Text>
            </Group>
          )
        )}
        {hasFolders && selection.view === "columns" && (
          <Text size="xs" c="dimmed">
            Browse each repository's folders to find your titles and study
            notes.
          </Text>
        )}
      </Stack>
      <RemoveFolderDialog
        name={library.removing?.name ?? ""}
        opened={library.removing != null}
        busy={library.removing?.underWay ?? false}
        onCancel={library.cancelRemoval}
        onConfirm={library.remove}
      />
    </>
  );
}
