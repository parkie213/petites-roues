# Build and development

The Java backend and Node frontend have independent builds. Requires Java 25,
Node.js 24 and npm. Run these commands from the repository root:

```bash
./java/mvnw -f java/pom.xml clean verify
npm --prefix web ci
npm --prefix web run build
npm --prefix web test
```

## Layout

```text
images/               Screenshots and branding assets
java/
  pom.xml             Java-only Maven parent and reactor
  mvnw                Maven wrapper (mvnw.cmd on Windows)
  documents/          Document records, JSON codec and validation
  application/        Services, local models and persistence
  server/             HTTP, configuration, migrations and executable startup
web/                  Independent React, Vite and Node application
android/              Offline phone player and comic reader (Gradle)
```

`java/documents` owns the document format: immutable records in
`com.petitesroues.model.document`, the codec that reads and writes their canonical JSON,
and the validation and phrase alignment rules. It depends only on Jackson, with no Spring,
JDBC or HTTP dependencies, so the Android app compiles the same sources. Its main code
is pinned to Java 17 (`maven.compiler.release`) because Android's library and D8 need it;
its tests stay on Java 25.
`java/application` depends on documents and owns services, stores, content discovery,
media readers and local application models. `java/server` depends on application
and supplies HTTP controllers, framework wiring, database migrations and startup.
The dependency direction is `server → application → documents`.

Unit tests live beside their owning module. Documents tests load examples from
`java/documents/src/test/resources/documents/` and publish a test-only fixtures JAR for application/server tests. HTTP
and real-database integration tests stay in server, alongside the canonical Flyway
migrations; application unit tests require no server dependency. Synthetic media
fixtures live in application test resources; server tests also load that directory.
The Maven build does not invoke npm or package the frontend. There are no copied
schemas or sibling-checkout dependencies. Content repositories remain separate.

## IntelliJ

Open the repository folder, then link `java/pom.xml` as a Maven project (or open
that POM as a project). Select JDK 25 for the project and Maven importer/runner.
Import the parent POM, which discovers all three Java modules and their dependencies.

If the previous root Maven project is still linked, unlink its removed POM in the
Maven tool window and reload `java/pom.xml`. Do not import module POMs separately.

The frontend remains an ordinary npm project using `web/package.json`; run its
`dev` script from IntelliJ or a terminal. IDE metadata is local and ignored.

## Running

Start the Java API from the repository root so the default `content/library.sqlite`
path resolves consistently:

```bash
mkdir -p content
java -jar java/server/target/petites-roues-server-0.1.0-SNAPSHOT.jar
```

In a second terminal:

```bash
cd web
npm ci
npm run dev
```

Open `http://127.0.0.1:8875`. The Node server serves the UI, supports Vite reloads
and proxies API requests to Java on port 8877. For a built frontend, use
`npm run build` followed by `npm start` instead of `npm run dev`. Java serves the
API only; its JAR does not contain the UI.

### Read aloud (optional)

The comic reader's Listen button (and the R key) speaks text with a local
[Piper](https://github.com/rhasspy/piper) voice. Java keeps one Piper process per
language running and generates audio per request; nothing is stored. It expects the
standalone Piper binary and voice models in these locations by default:

```text
~/.local/share/piper/piper/piper           # petitesroues.speech.piper
~/.local/share/piper/voices/fr_FR-*.onnx   # petitesroues.speech.voices (directory)
```

The voice is the first `.onnx` model, by file name, whose name starts with the
document's `sourceLanguage` followed by `_` or `-`; keep its `.onnx.json` beside it.
Override either location with `--petitesroues.speech.piper=…` or
`--petitesroues.speech.voices=…`. Without Piper the rest of the app works and the
Listen button reports that reading aloud is unavailable.

For Java-only iteration, install sibling modules first:

```bash
./java/mvnw -f java/pom.xml install -DskipTests
./java/mvnw -f java/server/pom.xml spring-boot:run -Dspring-boot.run.workingDirectory="$PWD"
```

## Android app

`android/` is a standalone, read-only phone app: the video player and comic reader
without edit mode. It reads a content folder synced to the phone (for example with
Syncthing) through the Storage Access Framework, never writes documents and keeps its
own index and progress in a local SQLite database. Kotlin, Jetpack Compose and Media3;
Android 14 or newer.

Its `documents` Gradle module compiles `java/documents/src/main/java` directly, so
there is no Maven install step. Keep the Jackson version in
`android/gradle/libs.versions.toml` in step with the one Spring Boot manages.

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug   # needs ANDROID_HOME or local.properties
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch choose the synced folder; the folder button adds more (for example a
second content repository) or removes them. Use Refresh whenever new titles have synced.
Titles are indexed under their content folder's name, so two folders with the same name
cannot both be indexed. The library shows the same Manga, Series and Films shelves as the
desktop's posters (see [card images](DOCUMENT-FORMAT.md#card-images)); unlike the desktop,
posters are only picked up on Refresh. Card images are cached in the app's cache and
rebuilt after each Refresh. The paused player's quiz button shares a Markdown brief through
the share sheet (pick the Claude app): `agents/workflows/subtitle-quiz.md` and
`agents/guides/translation-notes.md`, bundled at build time, plus the last 30 lines watched with
their translations and notes. The comic reader's top bar can lock landscape for two-page
spreads when the phone's auto-rotate is off. Hidden folders (`.git`, `.stversions`) are skipped. Text to speech uses the
phone's own voice for the document's `sourceLanguage`. Edits still queued on the
desktop are not visible until an agent has written them into the documents.

To install on a phone over Wi-Fi: Developer options → Wireless debugging → Pair device
with pairing code, then `adb pair <ip>:<pair-port> <code>` and
`adb connect <ip>:<port>` (the port shown on the Wireless debugging screen).

## Further Java modules

Add a sibling directory under `java/` with a POM inheriting `../pom.xml`, register
it in `java/pom.xml`'s `modules`, and declare dependencies explicitly in consumers.
Preserve the dependency direction above: lower modules cannot import server
controllers or configuration. Library and editing workflows currently stay together
in application so their existing package dependencies do not create Maven cycles.
The parent owns shared versions and formatting; only `server` applies Spring Boot
executable packaging. Ordinary modules produce dependency JARs. Maven orders the
build using their dependencies. Use `-pl server -am verify` from `java/` to build
the server and the modules it requires.

CI runs Maven, npm and the Android Gradle build separately, plus the existing preparation-tool and workflow
tests. Generated sources, frontend bundles, node_modules and Maven targets are ignored.
