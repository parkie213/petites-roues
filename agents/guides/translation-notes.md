# Translations, phrase breakdowns and learner notes

The standard for teaching material on a cue: written for an English-speaking beginner who
should come away able to **reuse** the language, not just decode one line. Read the
document's `sourceLanguage`, `translationLanguage` and `notesLanguage`: translate into the
translation language and write explanations in the notes language. Examples here are
French–English; teach the real distinctions of other languages instead of forcing French
grammar onto them (German case and separable verbs; Korean particles, endings and speech
levels).

First-pass annotation builds these layers in stages ([preparing a title](../../docs/PREPARE-TITLE.md#annotate-in-three-stages)):
the translation first (*Translation*), then the breakdown's chunks and links (*Chunking*,
*Links*), and after those are reviewed, the link notes and sentence notes (the remaining
sections). Apply the sections for the stage you are doing; a reviewer of a full cue applies
them all.

Each cue carries three layers that work together:

- **Translation** (`study.translation`): natural, accurate English for the line.
- **Phrase breakdown** (`study.alignment`): the French divided into chunks, each linked to
  its English and explained by that link's `note`.
- **Sentence notes** (`study.notes`): only what a beginner still would not understand after
  reading every link note — how the parts fit together.

## Translation

Read the line with its neighbours first: the previous and next balloon or subtitle, who is
speaking, and whether this cue continues or is continued by another (`continuesPrevious`).
Translate the meaning in context with natural English. When the natural English departs
from the French, keep the natural English and explain the gap in the notes; never bend the
translation into word-for-word English to make the breakdown tidy.

## Chunking: the core rule

**A chunk is a piece of French a beginner could learn and reuse in another sentence,
paired with the English that naturally translates it.** Chunks exist to teach. A
breakdown with fewer, meaningful chunks and rich notes beats a word-by-word map.

**First mark what must stay whole, then split what is left.** Never cut inside:

- an article or possessive and its noun (`le Nouvel An`, `tes bottes`);
- a subject pronoun and its verb, also after `que` (`que je donne`, never `que je | donne`);
- a negation and its verb, with both parts of the negation: `ne … pas`, `ne … plus`,
  `ne … jamais`, and `ne … que` with what it limits (`il ne me reste que deux euros`, one chunk);
- object pronouns, `y` and `en` and their verb (`ne te sauve pas`, `j'y pense`);
- an auxiliary and its participle (`je n'avais jamais eu`);
- a verb and the construction that gives it its meaning: `arriver à` + infinitive (`je n'arrive
  pas à dormir` → I can't sleep), `être en train de` + infinitive, `avoir besoin de`, and idioms
  (`se remplir le ventre`, `il y a deux jours`, `prendre la fuite`);
- a frame the English wraps around: `c'est … qui/que` when the English drops the cleft (`c'est
  Paul qui paie` → Paul's paying), causative `faire` + infinitive (`il fait réparer la voiture`
  → he's having the car repaired), `voir` + someone + infinitive;
- `être` and its complement in a short clause (`tu es trempé` → you're soaked; never `tu es |
  trempé`);
- a connector (`mais`, `et`, `car`, `donc`, `alors`) and the chunk that follows (`mais je suis
  en retard`).

**Then split** where a piece carries its own lesson and has its own natural English: a clause,
a time or place expression, a reusable adverbial (`encore une fois` → "once again"), a gerund
(`en courant` → while running). A chunk is at most one clause; a sentence of two clauses is at
least two chunks. A short exclamation or name is one chunk. A one-word chunk is the exception,
for a word with a lesson of its own. Between a clause-sized chunk and fragments, prefer the
clause.

**Test every pairing alone.** Would a learner who reused that chunk with that English be right
in another sentence? `bientôt` means "soon", so a separate `bientôt` → "almost" chip teaches a
false equivalence; the chunk `c'est bientôt…` → "it's almost…" is true. If a French chunk would
need English words that are not next to each other, or a meaning only true in this sentence,
the split is wrong: keep the construction as one chunk and teach its inside in the note.

**The note tells you when a chunk is too long.** If its link note has to gloss three or
more separate pieces one after another ("`il` means he… `aucun bruit` means no noise…
`en courant` means while running"), the chunk is probably several chunks:

| Too long | Better |
|---|---|
| `IL NE FAIT ABSOLUMENT AUCUN BRUIT EN COURANT !` → He makes absolutely no noise while running! | `IL NE FAIT ABSOLUMENT AUCUN BRUIT` → He makes absolutely no noise · `EN COURANT !` → while running! |
| `SA MÉTAMORPHOSE DE TOUT À L'HEURE AUSSI A DÛ LA FATIGUER ÉNORMÉMENT.` → Her transformation earlier must have worn her out too. | `SA MÉTAMORPHOSE` → Her transformation · `DE TOUT À L'HEURE` → earlier · `AUSSI A DÛ LA FATIGUER ÉNORMÉMENT.` → must have worn her out too. |

And when a split would be wrong:

| Over-split | Right |
|---|---|
| `JE N'ARRIVE PLUS` · `À DORMIR LA NUIT.` | `JE N'ARRIVE PLUS À DORMIR` → I can't sleep anymore · `LA NUIT.` → at night. |
| `IL NE NOUS RESTE` · `QUE DEUX JOURS.` | `IL NE NOUS RESTE QUE DEUX JOURS.` → We only have two days left. |
| `C'EST MA SŒUR` · `QUI CONDUIT.` (English: My sister's driving.) | `C'EST MA SŒUR QUI CONDUIT.` → My sister's driving. |
| `MAIS` · `TU ES` · `TREMPÉ !` | `MAIS TU ES TREMPÉ !` → But you're soaked! |

When the English keeps the same cleft, split it like the English: `CE N'EST PAS MOI` → it
wasn't me · `QUI AI CASSÉ LE VASE.` → who broke the vase. When the translation reorders the
line, one link may join French chunks that are not next to each other (`PUISQUE` … `JE SUIS EN
RETARD` → since I'm late, with `DE TOUTE FAÇON` → anyway); prefer that to a chunk whose English
is false outside this sentence.

Links:

- Each link's English side is **one unbroken stretch** of the translation. Links may cross
  (French and English order differ); that is fine. A French chunk needing two separated
  English pieces is a sign the French was over-split.
- Default to one-to-one links. Use an empty side only for a real absence (French `ne` has no
  English word; English adds a word French leaves implicit) and say so in the note.
- Mechanics, checked by the tools: chunks joined with single spaces reproduce the source
  text exactly, punctuation included; the same holds for the English; every chunk is in a
  link; every link has a note.

## Link notes: explain the chunk

A link note must be useful on its own when the learner taps that link. It answers: **why
does this French mean this English here?**

- **Meaning in context.** Gloss the chunk, then explain its inside: what each content word
  means and how the construction produces the meaning. A multi-word chunk is not an excuse
  to leave its words unexplained.
- **Every verb form.** Displayed form → infinitive → meaning here (`puisse → pouvoir` = be
  able to), with its subject, and tense and mood in plain words. Include small forms inside
  expressions (`est` in `c'est`, `a` in `il y a`). For compound tenses separate the
  auxiliary from the participle and name the tense. Say why an infinitive is an infinitive
  (after a modal, after `de`). **A past participle is not automatically a past tense**: say
  whether it forms a compound tense, a passive or a description, with any agreement. Keep
  pronominal verbs whole (`se remplir`, `se sauver`) and explain what the pronoun changes.
- **Familiar word, different sense.** When a word the learner knows means something else
  here, contrast the two senses (`sauver` = save, `se sauver` = run away).
- **Constructions behind idiomatic English.** When the translation is idiomatic, name the
  construction that produces it and explain how it works: causative `faire` + infinitive
  (`te faire passer l'épreuve` = make you take the test, so "put you through it"),
  `ça fait` + duration + `que`, `avoir l'intention de`, `ne … que`. A gloss is not an
  explanation. If the French and English differ in a way a learner would notice (negative
  French, positive English), say so.
- **Small words that change meaning**: `donc`, `là`, `en fait`, `il y a` + duration = ago.

Keep notes compact: a few short sentences. No conjugation tables; give a small contrast only
when it helps recognise the form.

## Sentence notes: how the parts fit

Ask: **what would a beginner still not understand after reading every link note?** Write
only that; a simple line often needs no sentence notes. Good candidates:

- how the clauses connect: who does what, what depends on what;
- a construction spanning several chunks (`vouloir que` + subjunctive, `ne … que`,
  `si` + present then future), showing both ends and what they govern;
- unusual word order: give the ordinary order, say what moved and why (emphasis, question,
  literary style) and what stayed put;
- pronoun referents, the scope of negation, agreement across chunks;
- register, or a literal versus natural translation gap too big for one link;
- for balloons or subtitles that split one sentence, which part completes which.

One teaching point per string: the player shows each string as its own bullet. Split a point
that joins two independent constructions with "and" or a semicolon. Do not repeat link notes.

## Style

- Surround French words, phrases and patterns under discussion with single backticks; the
  player highlights them. Mark a whole pattern as one span (`aussi … que`), keep English and
  grammar terms outside spans, and never put backticks in the source, translation or chunk
  text.
- Plain language. Define a grammar term the first time it matters ("subjunctive: a verb form
  used after expressions of wishing, among others").
- Mark invented examples as examples and give their English: `je veux manger` — I want to eat.
- No filler, no trivia, no restating the translation.

## Accuracy

- Teach the construction actually displayed: the right tense, the right object type.
- Scope rules to the construction; avoid absolute rules ("all reflexive participles agree").
- Do not reduce the subjunctive to "uncertainty" or the imperfect to "background" when that
  misexplains the line; name what selects the form here.
- Never invent etymology, character motives, scene details, pronunciation or grammar to make
  a note livelier. Verify doubtful senses and grammar against a dictionary or grammar.
- Distinguish an irregular verb from an irregular form: `menti` follows the ordinary
  `-ir → -i` pattern even though `mentir` is irregular elsewhere.
- Uncertain or garbled source text: teach what is clear, do not build grammar on a guess,
  and keep colloquial or deliberately simplified speech as it is. Machine transcripts mishear:
  a malformed sentence, a name spelled differently from neighbouring cues, or a word that
  makes no sense in the scene is a transcription doubt, not unusual French. Never explain it
  as valid grammar; recording the doubt is covered in
  [review-content.md](../workflows/review-content.md).

## Traps seen in first-pass notes

Machine-written notes got these wrong; check them every time.

- **Negative `falloir`.** `Il ne faut pas toucher` = you mustn't touch, a prohibition, not
  "you don't have to". Absence of obligation is `pas besoin de` / `ce n'est pas la peine de`.
- **`ne … personne`.** `Personne` with `ne` means nobody / not anyone, not "a person":
  `Je ne vois personne` — I don't see anyone; as subject, `Personne ne vient` — nobody is
  coming. Contrast `une personne` — a person. The same applies to `ne … rien` and `rien`.
- **A familiar verb with a different complement.** `Devoir` + infinitive is must
  (`tu dois partir` — you must leave), but `devoir` + a thing means owe (`tu me dois dix euros`
  — you owe me ten euros). When a line uses the less familiar sense, contrast the two in the
  link note; a bare gloss ("`devoir` = owe") leaves the learner wondering why "must" became
  "owe". Common cases: `trouver` (find / think of someone), `aller à` (go / suit), `rendre`
  (give back / `se rendre à` go to / `rendre visite` visit), `compter` (count / intend /
  matter), `attendre` (wait / expect), `apprendre` (learn / teach), `arriver à` (arrive /
  manage), `marcher` (walk / work), `manquer` (`tu me manques` — I miss you), `se servir de`
  (use), `se battre` (fight), `se moquer de` (mock / `s'en moquer` not care).
- **Possessives before a vowel.** `Mon`, `ton`, `son` come before any word starting with a
  vowel sound, including feminine nouns (`mon amie`, `son épée`); this does not make the noun
  masculine. `Son`/`sa` agree with the thing owned, never the owner, so `sa mère` can be "his"
  or "her" mother: decide from context.
- **Pronoun referents.** Work out what `le`, `la`, `l'`, `lui`, `leur`, `y` and `en` stand for
  from the neighbouring cues before translating. `L'` hides gender and `lui` is "to him" or
  "to her". If the context does not settle it, say so instead of guessing.
- **`Tout` as an adverb.** Before an adjective `tout` means "completely", not "all":
  `tout neuf` — brand new, `tout seul` — all alone. It still takes the feminine form before a
  feminine adjective starting with a consonant, so `elles ne sont pas toutes neuves` is "they're
  not brand new", not "they're not all new". Decide from the meaning, not the agreement.
- **Agreement the transcript cannot hear.** Silent endings are guesses in a transcript:
  `vu`/`vue`, `excité`/`excitée` and `domino`/`dominos` sound the same. When the written form
  disagrees with the speaker or the noun (a woman saying `je me suis faufilé`, `le carcasse`),
  correct the source; never explain the mismatch as unusual French. When fixing it would
  change the sound, record a listening check instead.
- **`Allez` as an interjection.** `Allez !` is the `vous` command of `aller`, but speakers use
  it to anyone, `tu` included, for "come on!" or "go on!". Say so instead of calling the
  speaker formal or the listener plural.
- **English in a dub.** A spoken English line in a French dub is usually a transcription
  error: record a listening check rather than annotating it as source. Lyrics of an English
  soundtrack song get one short note saying so; do not translate or quote them further.
- **Names that are puns.** Comedies and comics often build names from phrases: in Astérix,
  `Malococsis` is `mal au coccyx` (a pain in the tailbone) and `Idéfix` is `idée fixe` (an
  obsession). Explain the pun at the character's first appearance, give the phrase and its English,
  and don't stop at "a name". Look up the cast list and other sources rather than guessing; say
  "probably" when no source confirms it, and leave the pun out if you can't find one. A transcript
  can turn a pun name into an ordinary phrase (`Fais l'artifice` for `Feudartifis`), and it can
  "correct" a deliberately wrong name into the right one. Both are transcription doubts: never
  explain them as plain French.
- **Invented words.** Comic dialogue coins words: `charagiste` blends `char` (chariot) and
  `garagiste` (car mechanic), and `surfemme` is a joke feminine of `surhomme`. Check any word you
  don't know in a dictionary. If it isn't there, say plainly that it is invented, show how it is
  built, and give the ordinary word. Never call a coinage "uncommon", "informal" or a job title: a
  learner who reuses it will look silly.
- **English that wraps around an object.** "Put … away", "pick … up" and "turn … off" split
  around a pronoun in English: `Range-le` — put it away. A link's English must be one unbroken
  stretch, so link the whole `put it away` to `Range-le` and explain the parts in its note;
  never link `put` and `away` separately.
- **"Set phrase" needs a dictionary.** Don't call a combination a fixed expression unless a
  dictionary lists it. `exploser en larmes` is not one: the set phrases are `fondre en larmes`
  and `éclater en sanglots`, and `exploser` is a vivid free choice. Say that instead.
- **Reading order decides referents.** Manga read right to left, and a document's cue order
  can differ from the reading order on the page. Before saying what `il`, `y` or `ça` refers
  to, or which balloon a fragment completes, check the page: on Demon Slayer T01 p. 116 the
  `il` that "gets in the way" is the katana in the balloon read just before, not a person.
  Hedge ("presumably…") when the page doesn't settle it, and never invent scene details.

## Worked example: a manga balloon

French: **C'EST BIENTÔT LE NOUVEL AN. JE VEUX QU'ON PUISSE TOUS SE REMPLIR LE VENTRE.**
English: **It's almost New Year. I want us all to be able to eat our fill.**

| Chunk | English |
|---|---|
| `C'EST BIENTÔT LE NOUVEL AN.` | It's almost New Year. |
| `JE VEUX` | I want |
| `QU'ON PUISSE TOUS` | us all to be able to |
| `SE REMPLIR LE VENTRE.` | eat our fill. |

Link notes:

1. `C'est bientôt` + noun = "it will soon be…": `bientôt` means soon; English naturally says
   "it's almost". `Est → être`, present, with `ce` as subject. `Le Nouvel An` = New Year;
   `nouvel` is the form `nouveau` takes before a vowel sound.
2. `Veux → vouloir` = want; first person singular present. Here it is followed by `que` and
   a whole clause, the pattern for wanting someone else to do something.
3. `Qu'on puisse tous` = "that we all can". `Qu'` is `que` before a vowel. `On` is everyday
   "we" (the family). `Puisse → pouvoir` = be able to, present subjunctive, required by
   `vouloir que`. `Tous` = all of us, referring to `on`.
4. `Se remplir le ventre` = literally "fill one's belly": eat one's fill. `Remplir` = fill;
   `se` shows the subject fills its own belly, so French says `le ventre` where English says
   "our". Infinitive after `puisse`.

Sentence notes:

- English says "want + person + to do" (I want us to be able to). French cannot: when the
  person wanting and the person doing differ, it uses `vouloir que` + a subjunctive clause.
  When they are the same, it uses the infinitive: `je veux manger` — I want to eat.

The rejected breakdown split this line into seven chunks (`C'EST` | `BIENTÔT` | `LE NOUVEL AN.` |
`JE VEUX` | `QU'ON PUISSE` | `TOUS` | `SE REMPLIR LE VENTRE.`) with "us" and "to be able to"
both linked to `QU'ON PUISSE`. The beginner saw fragments, and lost the one pattern worth
learning.

## Worked example: a familiar word in a new sense

French: **Attends-moi, ne te sauve pas comme un voleur.**
English: **Wait for me — don't run off like a thief!**

Chunks: `Attends-moi,` → Wait for me — | `ne te sauve pas` → don't run off |
`comme un voleur.` → like a thief!

- `Attends-moi`: `attends → attendre` = wait for, an informal command. French takes the person
  directly, with no word for "for": `J'attends Paul` — I'm waiting for Paul.
- `Ne te sauve pas`: `sauver quelqu'un` = save someone, but `se sauver` usually means run
  away: `Le voleur s'est sauvé` — the thief ran away. The pronoun makes a different verb.
  A negative command keeps `te` before the verb; the positive is `sauve-toi !` — run!
- `Comme un voleur` = like a thief.

An insufficient note: "`Ne te sauve pas` = don't run away; the pronoun comes before the verb."
It gives the answer and a word-order rule but never resolves the obvious confusion: why does
"save" mean "run away"?

## Final check

- Could a beginner reuse each chunk in another sentence?
- Is each link's English one unbroken stretch, and every pairing true beyond this sentence?
- Does every content word and every verb form get explained somewhere?
- Do the sentence notes add something the link notes do not, one point per string?
- Is anything invented, repeated or there only to fill space?
