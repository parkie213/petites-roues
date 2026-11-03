<div align="center">

<img src="branding/logo_v3.png" alt="petites roues logo" width="128">

# petites roues

***Un vélo avec des petites roues***

A bike with training wheels. I know it's not funny if you have to explain the joke, but this is a
project for language learning beginners!

[![License: MIT](https://img.shields.io/badge/license-MIT-e4554a)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25-12294f)
![Node.js 24](https://img.shields.io/badge/Node.js-24-12294f)
![React](https://img.shields.io/badge/React-UI-12294f)
![Android](https://img.shields.io/badge/Android-offline-12294f)

A self-hosted app for anyone who wants help watching or reading in another language,
in the browser or on Android.

[Features](#features) · [Quick start](#quick-start) · [Why petites roues?](#why-petites-roues) ·
[How it works](#how-it-works) · [Design principles](#design-principles)

</div>

## Features

It's like Language Reactor with the auto-pause and translations, but with deeper explanations and
sentences broken up into manageable chunks.

And it applies a similar methodology to reading too. Currently only on comics/manga, but I plan to
apply it to other text media soon.

### Watch

<img src="images/player.jpg" alt="The video player with a subtitle broken into phrases and explained" width="100%">

- Subtitles split into phrases, each with its translation and explanation
- Listen to a line or a single phrase read aloud
- Correct translations and notes as you go; your edits are queued for an agent to apply

### Read

<table>
<tr>
<td width="35%"><img src="images/reader.jpg" alt="The comic reader on a phone explaining a speech bubble"></td>
<td>

- Comics and manga, opening a speech bubble for the same phrase-by-phrase breakdown
- Listen to a bubble or a single phrase read aloud
- Take it with you: the Android app works offline from a synced folder

</td>
</tr>
</table>

### And more

- Quiz yourself and keep study notes alongside your titles

## Quick start

> **Requires** Java 25 and Node.js 24.

```bash
./java/mvnw -f java/pom.xml verify && mkdir -p content
java -jar java/server/target/petites-roues-server-0.1.0-SNAPSHOT.jar
cd web && npm ci && npm run dev   # in a second terminal
```

Open <http://127.0.0.1:8875/> and use **Add repository** to select the `example` directory.
[Getting started](docs/GETTING-STARTED.md) walks through the example, its quiz and note, and
what to try next.

## Why petites roues?

I've been learning French for a while, and iterating on ideas to make consuming French media
easier for almost as long.

It seems to be generally accepted that it's good to hear and read what you don't know, to get used
to not knowing and learn it later.

But as a software developer I wanted a faster feedback loop: encounter an unfamiliar phrase, get
its contextual meaning and explanation, move on.

My first go was a browser extension for reading manga, where I could highlight speech bubbles and
submit a screenshot of the text to Claude to translate and explain it. It worked pretty well but
was rather clunky, and if I wanted to read the manga again, the data was in no format to be re-read.

My next go was a video player called *Sacre Bleu*, inspired by Language Reactor but providing more
than a direct translation. This time I decided it would be an application with a data format,
rather than a crude layer on top of a coding agent.

petites roues builds on top of that player, extends the model and applies it to other media.

## How it works

The project consists of three parts: the document model, study document creation and the player.

### Document model

The model is designed to provide contextual information to the learner, and to break sentences
down into phrases that are hopefully more digestible for a beginner.

### Study document creation

I am currently doing this quite simply with OpenRouter. Annotation happens in three stages, so
that each model request does one job and the phrase chunks are settled before anything is
written about them:

```mermaid
flowchart LR
  media["Video / Comic"] --> first["<b>Voxtral</b> transcribes video<br/><b>Terra</b> boxes and reads comic text"]
  first --> translate["<b>Luna</b><br/>translates"]
  translate --> chunks["<b>Luna</b><br/>breaks lines into phrases"]
  chunks --> review1["<b>Opus</b><br/>reviews the phrases"]
  review1 --> notes["<b>Luna</b><br/>explains each phrase"]
  notes --> review2["<b>Opus</b><br/>reviews and refines"]
  review2 --> doc[("document.json")]
```

Every stage leaves a valid document, so the player shows the work so far. The steps are in
[docs/PREPARE-TITLE.md](docs/PREPARE-TITLE.md).

A later project might be to turn it into an agentic pipeline, but for now this is working well.

### Content repositories

The study documents are simply stored in Git repositories to be as portable as possible.

A repository can also hold:

- **Quizzes** not tied to a title: Markdown prompts in a `quizzes/` folder at its root, offered by
  the library's *Quiz me* (lightbulb) and handed to a chat app. See
  [write-quiz.md](agents/workflows/write-quiz.md).
- **Study notes**: Markdown pages in a `notes/` folder at its root, read in the library's *Notes*.
  See [write-notes.md](agents/workflows/write-notes.md).

### Player

**Java server.** As much logic as possible lives in the Java server, because that is what I am
most comfortable with.

The server has document editing functionality, but to stop an agent and the server fighting over
document writes, the server stores user edits and makes them available over an API. An agent picks
up a batch of changes, updates the documents and tells the server they have been applied. To avoid
serving stale content, the server reads the document together with its pending edits.

```mermaid
flowchart LR
  you["You edit in<br/>the player"] --> queue[("Pending edits<br/>SQLite")]
  queue --> agent["Agent claims<br/>a batch"]
  agent --> doc[("document.json")]
  agent -. completes .-> queue
  doc --> serve["Served with<br/>pending edits applied"]
  queue --> serve
```

**React UI.** Kept as simple as possible, effectively just making the backend functionality usable.

**Android.** An offline phone version of the player and comic reader, without edit mode. It reads
the same study documents from a folder synced to the phone, using the same Java document model.
See [BUILD.md](docs/BUILD.md#android-app).

## Design principles

| | |
|---|---|
| **Portable annotations** | A lot of work goes into creating the study documents, so they are designed to be version controllable and as easy to share as running a `git clone`. |
| **Contextual explanations** | A direct translation is useful, but I want more than that: grammar, idioms and so on. |
| **Minimal noise** | I want to request help, not have it forced upon me. |
| **Iteration is built into the model** | Both people and AIs make mistakes. Stable cue identities and correctable annotations mean study documents can be iterated on and improved. |

<div align="center">
<br>
<sub>MIT licensed</sub>
</div>
