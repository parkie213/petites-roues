# petites roues document format

This directory is the documentation home of the portable format, consolidated from
`petites-roues-spec`. Documents are plain JSON, described in
[the document format](../docs/DOCUMENT-FORMAT.md) and defined by the Java records in
[java/documents](../java/documents/src/main/java/com/petitesroues/model/document/).
Example documents live in [examples/](examples/).
See [build and development](../docs/BUILD.md) for the reference player modules.

Sacre Bleu is a language agnostic annotation format for language learners.

## Motivation

I've been learning French for a while, and iterating on ideas to make consuming French media for almost as long.

I have found it seems to be a generally accepted practice that it's good to hear/read what you dont know to get used to not knowing and learning it later.

But as a software developer I was wanting a faster feedback loop. Encounter an unfamiliar phrase, get its contextual meaning and explanation, move on.

My first go was a browser extension for reading manga where I could highlight speech bubbles and submit a screenshot of the text to Claude to translate and explain the text.

It worked pretty well but was rather clunky. And if I wanted to read the manga again, the data was in no format to be accessed again.

My next go was a video player called 'Sacre Bleu', inspired by Language Reactor but providing more than a direct translation. And this time I decided it would be an application with a data format rather than a crude layer on top of a coding agent.

Well it was still somewhat crude, and still a layer over a coding agent, but at least this time the effort from the first watching/reading was not lost.

The model was in java pojos in the java layer of the video player, so I have extracted the model to something more portable and began applying it to other media.

## Principles

### Local ownership, portable annotations

I like owning my own media. And a lot of work goes into the creation of the study documents, so they are designed to be version controllable and as easy to share are running a git clone.

### Contextual explanations

A direct translation is useful, but I want more than that. Grammar, idioms etc

### Minimal noise

This applies in multiple ways.

I want to request help, not have it forced upon me.

And in the case of text documents, the amount of text on the screen at once should be comparable to a mobile news reader.

### Iteration is built into the model

Stable cue identities and annotations that can be corrected.

Both people and AIs make mistakes, the study documents are intended to be interated on and improved, so that is built into the model.

## Intended experiences

| Structure | Examples |
|---|---|
| Timed media | Video |
| Fixed pages | Comics, manga, magazines |
| Structured text | Novels, news articles, magazine articles |

The is a data model, but it is designed to achieve the following user experiences:

### Video

Subtitles are broken into phrases. Hovering a phrase displays the translation and accompanying notes. Also whole sentance notes are available.

### Comics, manga and magazines

In the case of a comic, when I hover a speech bubble, its text should appear broken into phrase chunks, like a subtitle line, with nothing translated yet. Hovering a phrase shows its translation and notes, as with video. The whole-bubble translation and sentence notes are available on request. Clicking should pin the bubble until I click somewhere else.

Magazine handling tbc. Potentially opening long articles in the reflowed article view would make sense.

### Novels and articles

The UX I want it comparable to the FTs app, that amount of text on screen at once. Clicking on a sentance should highlight the phrase chunks.

## Format

The format is plain JSON. I tried protobuf for a while, but nothing outside the Java player used the schema, so the model went back to simple Java records. My preference is still for the validation to be in the code.

## Related projects

[mermaid diagram with three nodes -> repo, player, notes generation]

### Reference player implementation

[Java server](../java/server/) and [React UI](../web/).

It's not beautiful but it does what I want. A simple java server and react UI.

### My notes generation tools

[links]

This is where most of my attention is going currently. It started life as some agent skills and one day it will hopefully be an automated agentic pipeline.
