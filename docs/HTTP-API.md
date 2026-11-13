# Current HTTP endpoint surface

Updated 13 November 2026 after capability consolidation: **58 explicit method/path
routes**, down from the 80-route baseline. HEAD/OPTIONS and framework/static routes
are not counted. Alternative representations on one URL retain explicit contracts.

| Capability | Before | After |
| --- | ---: | ---: |
| Document edits and feedback | 13 | 1 |
| Learner progress | 6 | 3 |
| Discovery and job lifecycle | 7 | 4 |
| Review work management | 7 | 5 |
| Document loading/export/validation | 4 | 2 |
| Other capabilities | 43 | 43 |
| Total | 80 | 58 |

[STUDY-API.md](STUDY-API.md) describes document/study/download representations and
progress. [COMMAND-API.md](COMMAND-API.md) defines typed atomic command batches,
expected versions and compact retry receipts. [DISCOVERY-API.md](DISCOVERY-API.md)
defines job admission and per-target results. [REVIEW-API.md](REVIEW-API.md) covers
review listings, claims, decisions and discard.

These changes do not implement accepted-document storage or isolate phone routes;
those remain in [STATE-AND-CONCURRENCY-PLAN.md](STATE-AND-CONCURRENCY-PLAN.md).
The [original inventory](ENDPOINT-OPERATIONS.md) and
[overlap proposal](ENDPOINT-CONSOLIDATION.md) record the baseline and rationale.
Optional artwork/speech/library-projection consolidation remains deferred.

## Routes

### CardController

| Method | Path |
| --- | --- |
| GET | `/api/titles/{id}/card` |
| GET | `/api/titles/{id}/card/{version}` |
| GET | `/api/titles/{id}/series-card` |
| GET | `/api/titles/{id}/series-card/{version}` |
| GET | `/api/media/{fingerprint}/card` |
| GET | `/api/media/{fingerprint}/card/{version}` |
| GET | `/api/media/{fingerprint}/series-card` |
| GET | `/api/media/{fingerprint}/series-card/{version}` |

### ChapterQuizController

| Method | Path |
| --- | --- |
| POST | `/api/titles/{id}/comic/quiz` |

### ComicController

| Method | Path |
| --- | --- |
| GET | `/api/titles/{id}/comic/pages/{pageId}` |
| GET | `/api/media/{fingerprint}/pages` |
| GET | `/api/media/{fingerprint}/pages/{index}` |

### LibraryController

| Method | Path |
| --- | --- |
| GET | `/api/library` |
| GET | `/api/library/posters` |
| GET | `/api/library/titles` |
| GET | `/api/library/usage` |
| PUT | `/api/library/media-links` |
| DELETE | `/api/library/media-links` |

### LibraryJobController

| Method | Path |
| --- | --- |
| POST | `/api/jobs` |
| GET | `/api/jobs/latest` |
| GET | `/api/jobs/{id}` |

### MarkdownPageController

| Method | Path |
| --- | --- |
| GET | `/api/quizzes` |
| GET | `/api/notes` |

### MediaController

| Method | Path |
| --- | --- |
| GET | `/api/titles/{id}/media` |
| GET | `/api/titles/{id}/media/{version}` |
| GET | `/api/media/{fingerprint}/video` |
| GET | `/api/media/{fingerprint}/video/{version}` |

### PhoneController

| Method | Path |
| --- | --- |
| POST | `/phone/pair` |
| GET | `/phone/hello` |
| GET | `/phone/pages/{repository}/{folder}/{name}` |
| GET | `/phone/titles` |
| GET | `/phone/titles/{id}/{role}` |

### PhonesController

| Method | Path |
| --- | --- |
| GET | `/api/phone` |
| PUT | `/api/phone` |
| POST | `/api/phone/pairings` |
| GET | `/api/phones` |
| DELETE | `/api/phones/{id}` |

### ProgressController

| Method | Path |
| --- | --- |
| GET | `/api/titles/{id}/progress` |
| DELETE | `/api/titles/{id}/progress` |
| PUT | `/api/titles/{id}/progress` |

### RepositoriesController

| Method | Path |
| --- | --- |
| GET | `/api/repositories` |
| DELETE | `/api/repositories/{id}` |
| POST | `/api/files/choose` |

### ReviewController

| Method | Path |
| --- | --- |
| GET | `/api/review/pending` |
| GET | `/api/titles/{id}/review` |
| POST | `/api/titles/{id}/review/batches` |
| POST | `/api/titles/{id}/review/batches/{batch}/complete` |
| POST | `/api/titles/{id}/review/discard` |

### SpeechController

| Method | Path |
| --- | --- |
| GET | `/api/titles/{id}/speech` |

### SpeechVoicesController

| Method | Path |
| --- | --- |
| GET | `/api/speech/voices` |
| PUT | `/api/speech/voices/{language}` |
| GET | `/api/speech/voices/catalogue` |
| POST | `/api/speech/voices/catalogue/{id}/install` |
| GET | `/api/speech/voices/catalogue/{id}/sample` |
| GET | `/api/speech/voices/{language}/preview` |

### StudyController

| Method | Path |
| --- | --- |
| GET | `/api/titles/{id}` |
| POST | `/api/titles/{id}/validate` |
| POST | `/api/titles/{id}/commands` |
