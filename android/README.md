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

Every Gradle sync and build first installs the shared Java library (`../java`: documents,
library and sync) into `~/.m2` with Maven, using the JDK Gradle runs on, so the app never
compiles against a stale jar. If that step fails, the sync shows Maven's error. It runs the
Java formatting check, so unformatted Java fails the sync until you run
`./java/mvnw -f java/pom.xml spotless:apply`.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug   # JVM tests and the APK
./gradlew :app:connectedDebugAndroidTest              # database tests, on a connected phone
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Device tests leave the app installed with its data (`gradle.properties`), so running them
doesn't wipe the phone's pairing, folders or progress.

## How it fits together

| Package | What is in it |
| --- | --- |
| `PetitesRouesApp`, `di/` | The Hilt application and `DataModule`, which provides the databases, DAOs and app-wide scope |
| `MainActivity` | The single activity: library, Notes and Quiz me tabs, and the open title |
| `data/` | Room: the library index and reading/playback progress |
| `library/` | Refreshing the index from the content folders (`LibraryService`), the library screen, card images |
| `player/`, `comic/` | The video player and the comic reader |
| `desktop/` | Pairing and syncing with the desktop, as a user-initiated background job (`Transfers`) |
| `pages/`, `quiz/`, `study/` | Notes and quiz pages, quiz sharing, and the phrase and note display shared by both players |
| `Settings` | Small per-device preferences in SharedPreferences: folders, pairing, followed series, toggles |

Hilt injects the app-wide objects (`Settings`, `LibraryService`, `ProgressStore`, `CardImages`,
the DAOs) into `MainActivity` and the sync job (`TransferJobService`). Compose screens receive
what they need as parameters. Discovery, linking documents to media by fingerprint, shelves and
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
