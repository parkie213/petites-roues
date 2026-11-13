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
  learner-state-model/   Java-only learner resume positions and progress rules
  content-model/    Java-only study documents, Markdown pages and validation
  study-prompts/      Prepare material and instructions for LLM study prompts
  document-json/     JSON mapping, codec and loading
  content-library/    Shared discovery, shelving, linking and fingerprints
  sync/               Desktop–phone protocol, client and transfer logic
  document-test-fixtures/ Shared model samples for downstream tests
  desktop-test-fixtures/  Generic database helpers and synthetic media
  desktop-application/ Services, local models, persistence and migrations
  desktop-server/     HTTP, configuration and executable startup
web/                  Independent React, Vite and Node application
android/              Offline phone player and comic reader (Gradle)
```

`java/content-model` owns immutable records in `app.petitesroues.model.document`
and Markdown quiz/note pages in `app.petitesroues.model.pages`, plus intrinsic
validation and page-title extraction, with no production dependencies. `java/document-json`
owns JSON mapping, the codec and file loading, depending on the model and Jackson.
Jackson annotations and configuration live entirely in the adapter. Both compile
production code for Java 17 for Android; tests use Java 25.
`java/content-library` depends on `content-model` and owns shared repository
identities and located Markdown entries, plus the library rules shared with Android: one walk of a
registered folder through a small `ContentTree` interface (java.nio on the desktop, the
Storage Access Framework on Android), what counts as a document, video or comic, where
posters are, and the v1 media fingerprint. Like the document modules it has no Spring and targets
Java 17, and the Android app uses its jar.
`java/sync` is how the phone and the desktop talk: the wire records (`app.petitesroues.model.sync`),
the phone listener's routes, the QR pairing code reader, certificate pinning (`CertificatePins`)
and the phone's `DesktopClient`, on `HttpURLConnection` because `java.net.http` is missing on
Android. The server answers with the
same records and its integration test pairs through the same client, so the two sides cannot drift.
It depends on content-library and Jackson and targets Java 17.
`java/desktop-application` depends on content-model, learner-state-model, study-prompts, document-json, content-library and sync, and owns services, stores, media
readers, local application models and canonical database migrations. `java/desktop-server` depends on desktop-application
and supplies HTTP controllers, framework wiring, Flyway startup integration and executable startup.
The dependency direction is `desktop-server → desktop-application → content-model, learner-state-model, study-prompts, document-json, content-library, sync`.

Unit tests live beside their owning module. Documents tests load examples from
`java/document-json/src/test/resources/documents/`. Pure model tests stay in
`content-model`; reusable sample documents and helpers live in the ordinary
`document-test-fixtures` JAR. Model tests construct their own inputs to avoid a
dependency cycle.
Non-HTTP database tests and canonical Flyway
migrations belong to desktop-application, while HTTP integration tests stay in
desktop-server and verify Spring Boot migration startup. Application tests require
no server dependency. The ordinary desktop-test-fixtures JAR shares database
setup and synthetic media between application and server tests, without depending
on the application. All fixture dependencies have test scope and are excluded
from production artifacts; no test-jar attachments or sibling source paths are used.
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
java -jar java/desktop-server/target/desktop-server-0.1.0-SNAPSHOT.jar
```

To let a phone pair with this desktop (experimental), click the phone icon in the library and
switch on **Allow phones to connect**. That opens the phone listener, which serves only
`/phone/**` on port 8878 (`--petitesroues.phone.port`) on every network interface; the choice is
remembered across restarts. Scan the QR code from the app's computer icon. The phone must be on
the same network; allow the port through a firewall (`sudo ufw allow 8878/tcp`). The listener
speaks only HTTPS (TLS 1.3) with a self-signed certificate the desktop creates the first time and
keeps in its SQLite library. The QR code carries the SHA-256 of the certificate's public key, and
the phone trusts only a desktop presenting that key, so scanning the code is what establishes
trust: no certificate authority or host name is involved, and the desktop's address can change.
Each phone then authenticates with its own token. A new library has a new certificate, so phones
pair again.

While the listener is open, the desktop also announces it on the local network with multicast DNS
(`_petitesroues._tcp`, with the certificate's pin; JmDNS). When the address a phone saved stops
answering, for example after the router gives the computer a new one, the phone looks for that
announcement, connects to the new address (still checking the pin) and saves it. A firewall must
let multicast DNS through (UDP 5353; Ubuntu's ufw allows it by default).

Once paired, the app's desktop button opens **Sync**: one row per series (or standalone title),
saying how many of its titles are on the phone. Its checkbox follows the series. Ticking it copies
the titles the phone lacks, and later syncs bring the ones the desktop adds. Every sync also
fetches updates to whatever is already on the phone, followed or not. Unfollowing deletes nothing.
Tapping a series shows its titles, each with its own checkbox to copy just that one without
following the series.
The app checks the desktop when the library opens and badges the desktop button with how many
titles a sync would fetch. The Sync button fetches them, and the pairing dialog is the computer
icon on that screen. Each title's media, posters and document (as the player serves it, with
pending edits) download into `Internal storage/Petites roues`, laid out by title, not by where
the desktop keeps the files. Each title's files are named by its ID, side by side in a folder per
series (at the top without a series): `Demon Slayer/demon-slayer-t01-comic.cbz`, its `.json`
document and its poster (`.jpg`, `.webp`…), with the series poster as `Demon Slayer/poster.*`.
Moving or renaming media on the desktop, or registering its folders again, changes nothing on
the phone, and the phone keeps reading and playback progress by document ID, so files moving
there keep it too. Notes and quizzes keep a subfolder per desktop repository. That folder is the app's own content folder: it needs **All files access**
(`MANAGE_EXTERNAL_STORAGE`), granted once from the empty library or the copy screen, and it stays
when the app is uninstalled. **Change** picks another folder with the system picker instead. A title is offered only when its media
matches its document's reference fingerprint; hand links do not reach the phone yet. Downloads
run as a user-initiated job with a notification and keep going with the screen off. They resume
from a hidden `.part` file after a cut, and are checked against the media's fingerprint before
they are moved into place. Syncing again skips files already there. Documents and the notes and
quizzes pages carry a hash of the bytes served, so a title whose document changed on the desktop
counts as an update and fetches only the document. The desktop's notes and quizzes are one
row at the top of the list, **Notes and quizzes**, followed like a series and copied to `<repository>/notes|quizzes/` and
read by the app's Notes and Quiz me screens. The listing is
`GET /phone/titles`, and each file names its own route: `GET /phone/titles/{id}/{role}` (media
and posters accept `Range`) or `GET /phone/pages/{repository}/{folder}/{name}`.

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

The comic reader's Listen button (and the R key) speaks text with local
[Piper JNI](https://github.com/jvoice-project/piper-jni) voices. The Maven dependency
bundles Piper, ONNX Runtime and eSpeak data for Linux x64/arm64, macOS x64/arm64 and
Windows x64. It loads native libraries into the JVM on the first speech request;
no standalone Piper executable is needed. Voice models are separate downloads.

Install the French Siwis voice once from the repository root (requires `curl`):

```bash
bash setup/speech.sh
```

The script reuses existing files. For other languages, put an `.onnx` model and its
matching `.onnx.json` configuration in `~/.local/share/piper/voices`, or set
`--petitesroues.speech.voices=…`. Open **Read-aloud voices** from the library header to choose an installed voice for
each source language. Preferences are saved in SQLite on this desktop. **Automatic**
uses the first complete model/configuration pair by filename whose name starts with
the source language followed by `_` or `-` (for example, `fr_FR-siwis-medium.onnx`
for `fr`). Regional source tags share their primary-language preference. A missing
selected model reports unavailable rather than switching voices silently. Reopen the
dialog after adding model files manually.

**Browse voices** loads the [Piper catalogue](https://huggingface.co/rhasspy/piper-voices)
on demand. Filter by language or search by name, region or quality, then **Listen**
to an upstream sample before installing. Samples use speaker zero for multi-speaker
models; speaker selection is not yet exposed. Each entry links its model card and
license and shows the model's download size. **Install** downloads the model,
configuration and model card, verifies their catalogue sizes and checksums, and makes
the complete pair available in the selector. Existing installations are not overwritten.
Download failures can be retried. Installation does not save a voice preference.

Enter a sentence in **Text for installed voice previews** and use **Preview** on an
installed catalogue entry to compare the same text through local synthesis without
changing your saved choice. The button beside the selector previews its current choice. Playback stops when the dialog closes. Installed selection and previews
work offline; the catalogue and prerecorded samples need internet access. Catalogue
metadata is cached in memory for an hour; **Refresh voice catalogue** bypasses that
cache. Model deletion remains manual. Missing voices do not prevent application startup;
the Listen button reports that reading aloud is unavailable.

Spring owns the loaded voices and closes them at shutdown. Piper handles concurrent
calls and native resource cleanup. Audio is encoded as WAV in memory, with no audio
files or child processes. Native libraries and eSpeak data are extracted by the JNI
library into the JVM temporary directory. Native calls cannot be forcibly interrupted;
a native crash would affect the server process.

On JDK 25, allow native access with `--enable-native-access=ALL-UNNAMED` when
launching Java (or `JAVA_TOOL_OPTIONS=--enable-native-access=ALL-UNNAMED`).
See [third-party notices](THIRD-PARTY-NOTICES.md) for the bundled runtime licenses.

The real-native test is enabled explicitly and runs in CI after installing the voice:

```bash
./java/mvnw -f java/pom.xml -pl desktop-server -am verify \
  -Dpetitesroues.test.voices="$HOME/.local/share/piper/voices"
```

For Java-only iteration, install sibling modules first:

```bash
./java/mvnw -f java/pom.xml install -DskipTests
./java/mvnw -f java/desktop-server/pom.xml spring-boot:run -Dspring-boot.run.workingDirectory="$PWD"
```

## Android app

`android/` is a standalone, read-only phone app: the video player and comic reader
without edit mode. It reads its own folder, `Internal storage/Petites roues`, where titles copied
from a paired desktop (above) go, plus any folders added through the Storage Access Framework,
such as one Syncthing keeps. It never edits documents and keeps its own index and progress in two
local SQLite databases (Room): the index is a cache rebuilt by Refresh, and progress is kept across
updates. Kotlin, Jetpack Compose, Media3, Hilt and Room; Android 14 or newer. [android/README.md](../android/README.md)
maps the code and explains the databases.

It finds and links content with the desktop's rules from `java/content-library`: every study document
and every video or CBZ in the chosen folders, documents linked to media by fingerprint from any
folder, and posters beside the media. Sync the media folder and the documents folder to the
phone and choose both. Media is fingerprinted once (8 MiB read per file) and again only when it
changes.

The app uses the shared Java jars (`java/content-model`, `java/learner-state-model`, `java/study-prompts`, `java/document-json`, `java/content-library`, `java/sync`) from `~/.m2`,
the same jars the server builds against. Gradle installs it with Maven whenever it configures the
build, including an Android Studio sync, so the app never compiles against a stale jar; Maven
skips work that is up to date. Jackson comes with the jar, at the version Spring Boot manages.
Keep `petitesRoues` in `android/gradle/libs.versions.toml` equal to the project version in
`java/pom.xml`. Building needs the JDK in `.java-version`, as Maven does; Gradle passes the JDK
it runs on to Maven, so Android Studio's Gradle JDK must be Java 25 (its bundled JDK is).

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug   # needs ANDROID_HOME or local.properties
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch choose the synced folder; the folder button adds more (for example a
second content repository) or removes them. Use Refresh whenever new titles have synced.
Titles are indexed under their content folder's name, so two folders with the same name
cannot both be indexed. The library shows fixed Manga, Series and Films shelves; it doesn't read a document's
`shelf` yet, unlike the desktop's posters (see [card images](DOCUMENT-FORMAT.md#card-images)); unlike the desktop,
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
build using their dependencies. Use `-pl desktop-server -am verify` from `java/` to build
the server and the modules it requires.

CI builds and tests the shared library once, then builds the server and the Android app against
that jar in parallel jobs (Gradle's `-PprebuiltLibrary` skips its own install), plus the existing preparation-tool and workflow
tests. Generated sources, frontend bundles, node_modules and Maven targets are ignored.
