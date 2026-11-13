# petites roues website

The landing page links to GitHub and a demo at `demo/`. The demo runs the **actual web app**:
`web/build-site.mjs` uses the normal Vite entry point and copies its output, then opts the demo HTML
into canned responses. There is no separate demo library or reader UI.

Run `npm ci --prefix web`, then `npm run build:site --prefix web`. Preview with
`npm run preview:site --prefix web` and open http://localhost:18876/.
The build bundles only the original Le parapluie de Lulu document, its four CBZ page images and
recorded French audio from `example/` and `site/demo-audio/`. Videos are not included yet.
Demo notes and general quiz prompts reuse `example/documents-fr/notes/` and
`example/documents-fr/quizzes/`. Chapter scopes come directly from the comic’s
`document.json`, just as they do in the desktop app. The demo adds no chapter override.
The comic’s source text, translations, media references, images and audio are unchanged.

Notes and the general Quiz me page use the real Markdown renderer and prompt export controls.
The reader’s chapter Quiz me prepares a selected chapter and quiz type using shared browser
brief-formatting helpers, equivalent to the server’s chapter quiz response. Preparation is
read-only and requires no chat API: copy or download the prompt, then use it in a chat.

A service worker scoped to `/demo/` answers the app's ordinary API requests with the sample
library, comic, page images and audio. Progress writes are persisted in browser Cache Storage
before returning success; the normal reader restores them through its progress API. View and
listening preferences use the app's normal browser storage. Add/Refresh folder, phone pairing,
voice installation and content editing controls are disabled. Unsupported writes are rejected.
The worker does not control the landing page or change how the desktop server runs.

Demo routes are encoded in `?route=` so library views, reader links, browser history and refresh
work on static hosting without server rewrites. Service workers require HTTPS or localhost.
The Pages workflow builds and deploys `site/dist` from `main` to
[petitesroues.app](https://petitesroues.app/). Domain and DNS settings are managed outside this repo.

## Updating the demo voice

The recordings use the installed French Piper `fr_FR-siwis-medium` voice. Models and binaries
are not published. After changing comic text or aligned source phrases, regenerate and commit
the clips and their text-to-file manifest:

```sh
python3 site/demo_audio.py --piper ~/.local/share/piper/piper/piper --model ~/.local/share/piper/voices/fr_FR-siwis-medium.onnx
```

The separate Python recorder needs Piper, the voice and ffmpeg; Pages builds and browser
tests use only Node. The Node packager uses Vite’s build API and fflate to extract CBZ images.
`web/build-site.mjs` rejects missing recordings. Listen, pinned-phrase listening, R replay and
listen-on-hover all use the real reader's controls and playback code with saved MP3 responses.
