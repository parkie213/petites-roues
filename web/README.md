# Frontend structure

`src/` is organized by feature. Keep related JavaScript, components and CSS
together so changes do not require searching unrelated directories.

- `app/`: browser entry point, page routing, shared theme and element styles.
- `library/`: repository browsing, folder navigation and the local folder picker.
  `Library` coordinates requests; `LibraryBrowser` renders the columns.
- `player/`: the player page, with view pieces and their CSS under `components/`.
- `shared/`: HTTP requests and browser storage used by both features.

The library uses Mantine for standard controls, menus, dialogs and layout.
`app/AppTheme.jsx` owns the shared colours and defaults for every screen. Prefer Mantine
components over custom controls; keep custom CSS for application-specific layout.
The player uses Mantine controls, popovers, forms and horizontal panel resizing.
Subtitle overlays, phrase alignment, the waveform and study-height adjustments
retain their specialized interactions and CSS.

The player page composes the screen. Its hooks own these responsibilities:

- `useStudyDocument`: initial document, review queue, waveform and library link.
- `usePlayerPlayback`: video clock, speed, seeking, replay and cue pauses.
- `usePlaybackProgress`: loading and saving playback position.
- `useCueEditing`: cue operations, serialized writes and timing autosaves.
- `usePlayerShortcuts`: keyboard interactions.
- `usePlayerClipboard`: copying study lines and review instructions.

Plain modules such as `alignment.js`, `listening.js` and `timing-edit.js` contain
presentation calculations that can be tested without React. Authoritative
validation, persistence guarantees and application rules belong in the Java API.
The browser owns focus, pointer interactions and pending requests.

`Player.css` contains the remaining media geometry and transcript-row styles.
Component styles beside the alignment and listening views describe their
specialized visualizations and answer feedback. Avoid blanket button or input
rules: standard controls should use Mantine props and the shared theme.

Run `npm test`, `npm run build`, `npm run format:check`, `npm run lint` and `npm run typecheck` from `web/`; CI runs all five. ESLint checks the React hooks rules (hooks in a fixed order, effects listing what they read).

The sources are moving to TypeScript a file at a time. `.ts` and `.tsx` files are checked in strict
mode by `npm run typecheck` (`tsc`; Vite only strips the types). JavaScript files sit beside them
unchecked until they are converted. The API's JSON shapes are written by hand in
`src/shared/types.ts`, following the Java records that produce them; import TypeScript files with
their `.ts` extension.

`npm test` runs Vitest over `test/` (`npx vitest` watches instead). Plain modules and the Node
server are tested in Node. A test that renders components or hooks starts with
`// @vitest-environment jsdom` and uses React Testing Library, as in `listen-button.test.jsx`
and `use-playback-progress.test.jsx`; `test/setup.mjs` supplies the browser APIs Mantine needs
that jsdom lacks. Stand in for `fetch`, `Audio` and other globals with `vi.stubGlobal`, which
Vitest undoes after each test.

## End-to-end tests

`e2e/` holds Playwright journeys through the built app: the library, a series, the player, the
comic reader, notes and quizzes, and adding a folder. `npm run e2e` replays API responses recorded
in `e2e/fixtures/`, so it needs only Node and a browser (`npx playwright install chromium` once).
A request with no recording fails the test.

The journeys also screenshot each main screen, compared with references in `e2e/screenshots/`.
Text and images render slightly differently from machine to machine, so screenshots are only
compared in Playwright's Docker image: `npm run e2e:screenshots` runs the journeys there, as CI
does (it needs Docker), and plain `npm run e2e` skips them. After an intended visual change, run
`npm run e2e:screenshots -- --update-snapshots` and commit the new references; a failing run
leaves the expected, actual and diff images in `test-results/`. A video's frame is hidden in
screenshots, since a pause can land a frame apart between runs.

`npm run e2e:record` records them again from the real Java server: build it first
(`./java/mvnw -f java/pom.xml -pl desktop-server -am package -DskipTests`). It starts the jar with a
throwaway library holding `example/media-fr` and `example/documents-fr`, so your own library and
saved progress are untouched. Re-record after changing what a journey does or what the API
returns, and commit the fixtures with the change. Recording is repeatable (times, random ids,
file versions are saved as fixed stand-ins, and playback PUTs use a checked echo template), so CI records again and
fails if the fixtures changed: that is how the frontend's canned responses are kept honest
against the API.

### Request and outcome assertions

A journey must prove its user-visible outcome. For reads, assert actual content, navigation or
filtering results rather than merely a successful request or a mounted container. For writes:

1. Wrap the initiating action in `expectWrite(page, { method, path, body }, action)`. The helper
   observes a new matching request, waits for its full successful response, and returns JSON.
   Match meaningful fields (or use a predicate for a timing batch) and assert the returned data.
2. Assert independently persisted state with `readJson(page, path)`, then the resulting UI.
   This browser GET goes through recording/replay and gets a unique `journeyCheck` query so its
   snapshot cannot be confused with an initial app load. It must follow the completed write.
3. Await these checks before navigation, a dependent edit, or test completion. Reload when the
   journey claims that an outcome survives reopening; optimistic state and session storage are
   insufficient proof of persistence.

For example, a comic turn checks the PUT payload and returned page ID, independently GETs the
saved page ID, and asserts the visible page. The reader's Back journey also reloads and verifies
that reading resumes on that page. The guard in `e2e/requests.mjs` checks all API response statuses
and drains every finite request, including saves during recorder teardown. Failed, abandoned or
unanswered JSON reads and writes fail the journey. Only aborted image, media and event-stream
reads are exempt from completion checks: navigation, video range seeks and closing a completed
job stream can intentionally abandon them. Their HTTP and transport errors still fail, and their
intended domain effects still need assertions. Do not replace outcome assertions with `networkidle`, sleeps or retries.

The current journeys cover these outcomes:

| Journey                                   | Outcome evidence                                                                                                                                                                    |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Library shelves and repository navigation | Expected series/comic links, counts, and the document's Play link at its repository path                                                                                            |
| Series episodes                           | Expected episode link after opening the series                                                                                                                                      |
| Attention filter                          | Pressed filter state, affected series retained, unaffected comic hidden                                                                                                             |
| Notes                                     | Expected note heading and example text                                                                                                                                              |
| Quiz                                      | Expected quiz heading and continuation action                                                                                                                                       |
| Add folder                                | Chosen folder, matching job command and acknowledgement, successful completion message/job state, registered repository, updated count after reload                                 |
| Player stepping                           | Matching seek saves and returned positions, independently saved position, expected source/translation phrases, Back returns to the series                                           |
| Comic page turns                          | Each saved/returned/read-back page ID and its visible bubble                                                                                                                        |
| Comic bubble and Back                     | Saved page, contextual translation, reload resumes that page, Back returns to the library                                                                                           |
| Subtitle editor                           | Persisted joined boundaries and retained neighbouring data, saved and independently read translation, actual play/pause and pause save, saved position and translation after reload |

Writes are recorded with their payloads. Replay requires a matching payload as well as method
and URL; a saved cover cannot satisfy a save to page 1. Repeated identical PUTs share a contract,
while different payloads keep separate answers. Playback progress uses one explicit echo template:
the recorder first verifies that the real server returned the submitted position, then replaces
that number with `<request.positionSeconds>`. Replay fills it from the actual request. This
keeps wall-clock playback out of fixtures without hiding a server that acknowledges the wrong
position. Independent GET checkpoints still verify persisted state against the real server.
Those saved GET positions remain exact: rounding a 7.02-second cue start to 7.0 would restore
the preceding subtitle.

Video journeys opt into `test.use({ pauseOnOpen: true })` to pause the first play of each video
before its clock advances. Muted autoplay can otherwise bypass the browser's gesture policy.
Later play/pause actions use the real media clock. Guard and recording-contract regression tests
in `test/journey-requests.test.mjs` and `test/write-recordings.test.mjs` run with `npm test`.

The voice catalogue journey uses a local provider fixture to install a real French
Siwis model and preview it through JNI. Before recording, run `bash setup/speech.sh`
(or set `PETITESROUES_TEST_VOICES` to the directory containing that model). Replay needs
neither models nor network. The provider uses port 18879. Its comparison sample is a
fixed MP3 tone; recordings validate real native WAV output and retain a fixed tone for
replay because Piper waveforms vary between calls. Catalogue sizes are recorded to the
nearest MB to accommodate revisions of the same separately installed test model.
