# petites roues website

The landing page links to GitHub and a static comic demo at `demo/`. The demo reuses the app's
comic page viewer, navigation, translation popovers and phrase explanations. Its only title is
the original Le parapluie de Lulu sample in `example/`; videos are not included yet.

Run `npm ci --prefix web`, then `python3 site/build.py`. Preview with
`python3 -m http.server 18876 --directory site/dist` and open http://localhost:18876/.
The build bundles the document and extracts its four page images from the example CBZ.
There is no API server, service worker, production content or API recording in the demo.
Reading progress and view preferences stay in browser storage. Editing is omitted.
Read aloud uses committed MP3 recordings: whole bubbles, pinned phrases, the R key and
listen-on-hover all work without a speech server. Hash navigation supports direct reader links and refresh on static hosting.

The public domain is [petitesroues.app](https://petitesroues.app/).
The [Pages workflow](../.github/workflows/pages.yml) builds and deploys `site/dist` from `main`.
Custom domain and DNS settings remain managed outside this repository.

## Updating the demo voice

The recordings use the installed French Piper `fr_FR-siwis-medium` voice. Models and binaries
are not published. After changing the comic source text or its aligned source phrases, regenerate
and commit the clips and their text-to-file manifest:

```sh
python3 site/demo_audio.py --piper ~/.local/share/piper/piper/piper --model ~/.local/share/piper/voices/fr_FR-siwis-medium.onnx
```

The manual recorder needs Piper, the voice and ffmpeg; Pages builds need none of them.
`site/build.py` checks that every bubble and source phrase has a committed recording before copying
it into the static output. Playback starts after a visitor gesture, subject to browser audio settings.
