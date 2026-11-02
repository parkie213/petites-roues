# Writing study notes

Study notes are the learner's own reference pages on a grammar point or a set of words, read in
the library's Notes (desktop and phone) when something comes up while reading or watching. Each
note is one Markdown file in the `notes/` folder at the root of a registered content repository,
e.g. `notes/verb-objects.md`, independent of the general quizzes in `quizzes/`. The app lists the
files by their `# heading` and renders them; it reads nothing else and never writes them.

Write for the learner, in the notes language, to the [teaching guide](../guides/translation-notes.md)'s
standard: say why a form has its meaning, not only the rule.

1. `# Name`, as the learner would look for it ("Verb objects and object pronouns").
2. `## ` sections, each one point, in the order a learner meets them.
3. Short examples in the studied language with the key word in **bold**, and tables for
   paradigms or comparisons. Keep tables to four columns or fewer so they fit a phone; put a
   longer sequence on one line (« me, te… → le, la, les → … »).
4. Where English intuition misleads, say so with a contrasting pair.

Check every example: idiomatic, correct and in the stated register. Bold must not end on an
apostrophe right before a letter (`**l'**ai` stays literal in Markdown); bold the whole word
instead (`**l'ai**`). Commit the file to the content repository; it appears the next time Notes
opens.
