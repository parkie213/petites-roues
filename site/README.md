# petites roues website

The landing page links to GitHub and a demo at `demo/`. The demo runs the **actual web app**:
`site/build.py` uses the normal Vite entry point and copies its output, then opts the demo HTML
into canned responses. There is no separate demo library or reader UI.

Run `npm ci --prefix web`, then `python3 site/build.py`. Preview with
`python3 -m http.server 18876 --directory site/dist` and open http://localhost:18876/.
The build bundles only the original Le parapluie de Lulu document, its four CBZ page images and
recorded French audio from `example/` and `site/demo-audio/`. Videos are not included yet.

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

The recorder needs Piper, the voice and ffmpeg; Pages builds need none of them.
`site/build.py` rejects missing recordings. Listen, pinned-phrase listening, R replay and
listen-on-hover all use the real reader's controls and playback code with saved MP3 responses.
