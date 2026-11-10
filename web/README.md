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

Run `npm test`, `npm run build`, `npm run format:check` and `npm run lint` from `web/`; CI runs all four. ESLint checks the React hooks rules (hooks in a fixed order, effects listing what they read).

## End-to-end tests

`e2e/` holds Playwright journeys through the built app: the library, a series, the player, the
comic reader, notes and quizzes, and adding a folder. `npm run e2e` replays API responses recorded
in `e2e/fixtures/`, so it needs only Node and a browser (`npx playwright install chromium` once).
A request with no recording fails the test.

`npm run e2e:record` records them again from the real Java server: build it first
(`./java/mvnw -f java/pom.xml -pl server -am package -DskipTests`). It starts the jar with a
throwaway library holding `example/media-fr` and `example/documents-fr`, so your own library and
saved progress are untouched. Re-record after changing what a journey does or what the API
returns, and commit the fixtures with the change. Recording is repeatable (times, random ids,
file versions and exact video positions are saved as fixed stand-ins), so CI records again and
fails if the fixtures changed: that is how the frontend's canned responses are kept honest
against the API.
