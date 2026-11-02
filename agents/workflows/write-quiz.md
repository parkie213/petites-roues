# Writing a general quiz

A general quiz drills a grammar point or a set of words, not a title's lines. It is one Markdown
file in the `quizzes/` folder at the root of a registered content repository, e.g.
`quizzes/passe-compose-vs-imparfait.md`. The library's Quiz me lists those files by their
`# heading` and hands the chosen one to a chat app, followed by the general [quiz
rules](subtitle-quiz.md) (one item at a time, hint, retry). The chat model makes up its own
sentences, so the file only has to describe the topic. The app reads no other structure.

Write the file to the chat model running the quiz:

1. `# Name`: short, as the learner would look for it ("Verb objects and object pronouns").
2. One line naming the studied language and the language to explain in.
3. What to drill: the forms, words or choices covered, concretely (list the verbs or pronouns).
4. `## Exercise`: what one item asks for, how to vary the items and when to step up.
5. `## Traps to look for`: the mistakes learners actually make, each with a correct example;
   for French–English, where English intuition misleads. Follow the [teaching
   guide](../guides/translation-notes.md): explain why a form has its meaning, not only the rule.
6. `## Examples of the style wanted`: two or three natural sentences to set the register.

Check every example: it must be idiomatic, correct and in the stated register. Keep the file
under about 2,000 characters; the rules are added on top. Commit the file to the content
repository; it appears the next time Quiz me opens, with no refresh.
