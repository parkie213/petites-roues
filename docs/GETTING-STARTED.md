# Getting started

Run petites roues in the browser and try it on the bundled example: a one-minute Peppa Pig
clip in French with English translations, plus a quiz and a study note.

## Run it

> **Requires** Java 25 and Node.js 24.

**1.** From the repository root, build the Java modules and start the API:

```bash
./java/mvnw -f java/pom.xml verify
mkdir -p content
java -jar java/server/target/petites-roues-server-0.1.0-SNAPSHOT.jar
```

**2.** From the repository root in a second terminal, start the Node development server:

```bash
cd web
npm ci
npm run dev
```

**3.** Open <http://127.0.0.1:8875/>

## A tour of the example

### Add the example

<img src="../images/getting-started/01-empty-library.png" alt="The empty library" style="max-width: 100%; height: auto;">

The library should start empty. Click **Add repository** in the middle.

<img src="../images/getting-started/02-locate-example-directory.png" alt="Locate the example directory" style="max-width: 100%; height: auto;">

Select the `example/documents` directory in the petites roues repository.

A repository is simply a content folder; Git is optional. The example keeps its study documents
and its videos in separate folders. Four one-minute titles cover three language pairs: French,
German and Mandarin, each to English.

```text
example/
  documents/                  the repository: study documents, notes and quizzes
    peppa-pig/
      poster.jpg              optional series card, placeholder when absent
      s01e01/document.json    subtitles, translations, phrases and notes
      s01e02/document.json
      s01e34-zh/document.json the Mandarin dub, a second language for one series
    masha-and-the-bear/
      083-de/document.json    German, a second series
    quizzes/
      verb-plus-infinitive.md
    notes/
      verb-plus-infinitive.md
  media/                      the videos, linked to documents by fingerprint
    peppa-pig/
    masha-and-the-bear/
```

Today, add `example/documents` as the repository; the titles open without video. Once the
folders change lands, you add `example` itself and Refresh finds both the documents and the
videos, linking each video to its document by fingerprint.

A repository can contain titles, notes, quizzes, or any combination of them. For example,
you can keep videos in one repository, comics in another, and notes and quizzes in a third.

<img src="../images/getting-started/03-example-added-to-library.png" alt="The example added to the library" style="max-width: 100%; height: auto;">

Peppa Pig should be here. Click on it to go through to the episodes. Then click ep01.

### Watch and explore phrases

<img src="../images/getting-started/04-hover-phrases.png" alt="Hover over phrases to read learner notes" style="max-width: 100%; height: auto;">

Hover over phrases to see their notes.

### Practise the episode

<img src="../images/getting-started/05-quiz-options.png" alt="Choose the subtitle quiz scope and type" style="max-width: 100%; height: auto;">

Click **Quiz me** at the bottom. Choose **Whole episode** or **Time range**, then choose a
quiz type. Click **Copy & open ChatGPT** or **Copy & open Claude**, paste into the new chat
and send. The quiz instructions are bundled in the app.

<img src="../images/getting-started/06-agent-quiz-chatgpt.png" alt="Practise subtitle translations in ChatGPT" style="max-width: 100%; height: auto;">

Have your agent of choice quiz you on the lines from the episode.

### Read your study notes

<img src="../images/getting-started/07-check-notes.png" alt="Read the study notes" style="max-width: 100%; height: auto;">

To check your study notes, go back to the library and click **Notes** at the top.
The example comes with one: **Verbs followed by an infinitive**.

### Try a general quiz

<img src="../images/getting-started/08-general-quiz.png" alt="Choose a general quiz" style="max-width: 100%; height: auto;">

Back in the library, click **Quiz me** and choose **Verbs followed by an infinitive**.
This general quiz practises a grammar topic using new sentences, rather than the episode
lines. Click **Copy & open ChatGPT** or **Copy & open Claude**, paste into the new chat
and send.

<img src="../images/getting-started/09-general-quiz-chatgpt.png" alt="Practise the general quiz in ChatGPT" style="max-width: 100%; height: auto;">

Have your agent of choice quiz you.

## Next steps

- **Your own titles:** **Add title** in the library creates a title folder from a video or
  comic, and [PREPARE-TITLE.md](PREPARE-TITLE.md) walks through transcribing, translating and
  annotating it.
- **Quizzes and notes:** [write-quiz.md](../agents/workflows/write-quiz.md) and
  [write-notes.md](../agents/workflows/write-notes.md).
- **On your phone:** the offline Android app reads the same folders; see
  [BUILD.md](BUILD.md#android-app).
- **The document format:** [DOCUMENT-FORMAT.md](DOCUMENT-FORMAT.md).
- **The domain model:** [DOMAIN-MODEL.md](DOMAIN-MODEL.md), documents, editing and the library with diagrams.
