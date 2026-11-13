# petites roues website

The landing page links to GitHub and a static comic demo at `demo/`. The demo reuses the app's
comic page viewer, navigation, translation popovers and phrase explanations. Its only title is
the original Le parapluie de Lulu sample in `example/`; videos are not included yet.

Run `npm ci --prefix web`, then `python3 site/build.py`. Preview with
`python3 -m http.server 18876 --directory site/dist` and open http://localhost:18876/.
The build bundles the document and extracts its four page images from the example CBZ.
There is no API server, service worker, production content or API recording in the demo.
Reading progress and view preferences stay in browser storage. Editing and local speech tools
are omitted. Hash navigation supports direct reader links and refresh on static hosting.

The public domain is [petitesroues.app](https://petitesroues.app/).
The [Pages workflow](../.github/workflows/pages.yml) builds and deploys `site/dist` from `main`.
Custom domain and DNS settings remain managed outside this repository.
