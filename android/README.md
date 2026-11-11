# Petites roues for Android

The phone app: the video player and comic reader, offline, read-only. It never edits documents.
It reads its own folder (`Internal storage/Petites roues`, where titles synced from a paired
desktop go) and any folders added through the Storage Access Framework. Building, installing and
pairing are described in [docs/BUILD.md](../docs/BUILD.md#android-app); this page is a map of
the code.

Kotlin, Jetpack Compose, Media3, Hilt and Room; Android 14 or newer.

## Opening it

Open this `android/` folder as its own project in Android Studio (or IntelliJ with the Android
plugin), not the repository root. The Gradle JDK (**Settings → Build Tools → Gradle**) must be
Java 25; Android Studio's bundled JDK is.

Every Gradle sync and build first installs the shared Java library (`../java`: content-model, learning-model, document-json,
content-library and sync) into `~/.m2` with Maven, using the JDK Gradle runs on, so the app never
compiles against a stale jar. If that step fails, the sync shows Maven's error. It runs the
Java formatting check, so unformatted Java fails the sync until you run
`./java/mvnw -f java/pom.xml spotless:apply`.

```bash
./gradlew spotlessApply                               # format the Kotlin
./gradlew :app:lintDebug :app:verifyRoborazziDebug :app:assembleDebug  # lint, JVM tests, APK
./gradlew :app:connectedDebugAndroidTest              # database tests, on a connected phone
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Kotlin is formatted by ktfmt (Google style, as google-java-format formats the Java modules)
through Spotless: `./gradlew spotlessApply` formats and removes unused imports, and CI runs
`spotlessCheck`. ktfmt judges imports by name, so an import whose name only appears as an argument
name (`size(width = …)`) survives it; Android Studio's **Code → Optimize Imports** catches those.

`./gradlew :app:lintDebug` runs Android Lint; CI fails on any warning, and Kotlin compiler warnings
fail the build too. Lint's notices about newer library versions are switched off. Debug builds
turn on StrictMode, which logs disk, database and network access on the main thread and leaked
resources: `adb logcat -s StrictMode`.

JVM tests run on Robolectric, so they reach Android APIs and draw real screens without a phone.
ViewModel tests (`LibraryViewModelTest`, `TitleViewModelTest`) use real Room databases in memory and refresh the
repository's `example/` folders through the real `LibraryService`; the desktop's
(`DesktopTitlesViewModelTest`, `DesktopPairingViewModelTest`) talk to a `FakeDesktop` in place of
the network, `PlayerViewModelTest` runs a real ExoPlayer on the example episode, and `ReaderViewModelTest` the
example comic's CBZ. They run with `kotlinx-coroutines-test`
(`runTest`, `MainDispatcherRule`) and Turbine for the states a `StateFlow` passes through.
`NavigationTest` starts the whole Hilt app over the same in-memory databases (`TestDataModule`
replaces `DataModule`) and taps from the library into a title and back. The screenshot tests
(`app/src/test/.../screens/`) draw the library, Notes and Quiz me, the comic reader and the
player and compare them with the PNGs committed in `app/src/test/screenshots/`. The library uses
a small made-up catalogue (`Fixtures`) with the `example/` comic and poster as card art, and the
example note and quiz beside a couple of made-up ones; the reader and the player open the
`example/` comic and episode. `verifyRoborazziDebug` fails when a screen no longer matches, and writes a
before/after/diff image to `app/build/outputs/roborazzi/` (CI keeps it as the `screenshot-diffs`
artifact). When a change is meant to alter a screen, re-record and commit the new images with the
code, so the review shows them:

```bash
./gradlew :app:recordRoborazziDebug     # rewrite the screenshots
./gradlew :app:verifyRoborazziDebug     # compare against them
```

Screens that load on a background thread (card images, comic pages, Markdown, the video's length) wait for it before
capturing, so the images don't depend on timing. The tests run on Robolectric's Android 15 image
(`SDK` in `LibraryScreenshotTest.kt`): its Android 16 image carries an old commons-compress that
hides the app's, so the reader can't open a CBZ there.

Device tests leave the app installed with its data (`gradle.properties`), so running them
doesn't wipe the phone's pairing, folders or progress.

## How it fits together

| Package | What is in it |
| --- | --- |
| `PetitesRouesApp`, `di/` | The Hilt application and `DataModule`, which provides the databases, DAOs and app-wide scope |
| `MainActivity`, `Routes` | The single activity and its Navigation 3 back stack: the library (with Notes and Quiz me), an open title, the desktop's titles |
| `data/` | Room: the library index and reading/playback progress |
| `library/` | Refreshing the index from the content folders (`LibraryService`), the library's state and actions (`LibraryViewModel`), the library screen, card images |
| `player/`, `comic/` | The video player and the comic reader |
| `desktop/` | Pairing (`DesktopPairingViewModel`) and syncing with the desktop (`DesktopTitlesViewModel`), as a user-initiated background job (`Transfers`) |
| `pages/`, `quiz/`, `study/` | Notes and quiz pages, quiz sharing, and the phrase and note display shared by both players |
| `Settings` | Small per-device preferences in SharedPreferences: folders, pairing, followed series, toggles |

Hilt injects the app-wide objects (`Settings`, `LibraryService`, `ProgressStore`, `CardImages`,
`Transfers`, `DesktopClient`, the DAOs) into `MainActivity`, the sync job (`TransferJobService`) and ViewModels. A screen's work
(loading, refreshing, saving) belongs in a `@HiltViewModel` that exposes `StateFlow`s and is
obtained with `hiltViewModel()`; the composables draw that state and call back into it.
`LibraryViewModel` owns the library and `TitleViewModel` opens a title for the player or reader;
each title on the back stack gets its own, created by path with assisted injection
(`hiltViewModel<TitleViewModel, TitleViewModel.Factory> { it.create(path) }`) and cleared when
the title is popped. The back stack is saved, so an open title survives process death;
the desktop's two ViewModels pair with it and sync from it; `PlayerViewModel` owns the video's
player and saves its position, so playback survives the activity being recreated, and `ReaderViewModel` does the same for a
comic's archive, read-aloud voice and page. `LibraryViewModel` also counts what a sync with the
desktop would fetch, so no composable does file or network work. Blocking work runs on the injected `@IoDispatcher`, so tests can control it. Discovery, linking documents to media by fingerprint, shelves and
sync rules come from the shared Java library, so the phone and the desktop agree.

## Databases

The phone has its own SQLite databases, separate from the desktop's (which Flyway manages), in
the app's private storage. There are two because they are kept differently:

- **`index.sqlite`** (`IndexDatabase`): the titles and media fingerprints the last Refresh found.
  It is a cache, rebuilt from the folders by every Refresh, so it has no migrations. Change its
  entities and bump its version, and Room drops and recreates it on the next launch, and the
  library refreshes because the index is empty.
- **`progress.sqlite`** (`ProgressDatabase`): where each title was left, keyed by document ID. It
  is the only data a Refresh can't rebuild. Room exports its schema to `app/schemas/`, one JSON
  file per version, committed with the code. To change it, bump the version and add an
  `@AutoMigration` (or a hand-written `Migration` where Room can't infer the step, such as a
  rename); without one, Room refuses to open it rather than losing the data. `ProgressStore`
  runs its reads and saves off the main thread, in order.

Room refuses queries on the main thread. To look inside on a phone, run the app from Android
Studio and open **App Inspection → Database Inspector**.
