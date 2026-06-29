# CampusCue

CampusCue is the attendance, timetable, and grades app I wished my university had.
Galgotias runs on iCloudEMS — a generic student portal that's slow, online-only, and
makes you tap through "select semester → select session" dropdowns *every single time*
just to see whether you can afford to skip tomorrow's 9 AM. I built CampusCue so I
could answer "what's my attendance?" in one tap: instantly, from cache, even with no
signal in the lecture hall.

> A personal project I built for myself as a Galgotias University student. GU-only by
> design — the tenant naming in the code is just configuration hygiene, not a roadmap.

<!--
  Add screenshots to docs/screenshots/ then uncomment:
  <p align="center">
    <img src="docs/screenshots/dashboard.png" width="22%" alt="Dashboard" />
    <img src="docs/screenshots/attendance.png" width="22%" alt="Attendance" />
    <img src="docs/screenshots/timetable.png" width="22%" alt="Timetable" />
    <img src="docs/screenshots/grades.png" width="22%" alt="Grades" />
  </p>
-->

## Why I built it

The official app genuinely got in my way:

- **It didn't work offline at all.** No signal in a classroom (which is most of them) meant no attendance, no timetable — nothing.
- **The things I check daily were buried.** Attendance and the day's timetable sat behind several screens and dropdown selects. Every day. For information I look at every day.

So the whole premise of CampusCue is inverted: the stuff I care about most should be the
*first* thing I see, should load instantly from cache, and should work with no internet.
Open the app and the dashboard already shows today's classes, my overall attendance, and
exactly how many lectures I can still skip before I drop below the line.

## What it does

- **Dashboard** — overall %, at-risk subjects, "bunkable" budget, today's schedule with a live next-class countdown.
- **Attendance** — per-subject breakdown with how many classes I can skip or must attend, plus a recovery forecast.
- **Timetable** — the week at a glance with live in-progress indicators.
- **Day-wise** — a month calendar of present/absent/partial days.
- **Planner** — a leave simulator: tap future dates, see the projected hit to each subject.
- **Grades** — component-wise marks insights and semester result cards (with PDF export).
- **QR attendance** — scan the classroom QR right inside the app.

Everything is cache-first, so it opens instantly and keeps working offline.

## The parts I'm proud of

- **I reverse-engineered an undocumented API.** iCloudEMS ships no public API and no docs.
  I traced how the official client actually talks to the server and rebuilt those calls in
  the app — matching its request shapes, auth, and (often messy, inconsistent) responses —
  then layered on everything the portal lacks: caching, offline fallback, and the
  "can I skip this?" analytics that were the whole point.

- **In-app QR attendance.** GU marks attendance with classroom QR codes that normally send
  you back to the clunky portal. CampusCue does the entire flow natively — a selfie step,
  then live QR scanning with CameraX + ZXing, with digital zoom so a QR across the hall
  still decodes. This is the feature I'm happiest with day to day.

- **I built my own distribution + auto-update pipeline — no Play Store.** A single-university
  app doesn't belong on the Play Store, so I rolled the whole thing myself: a Cloudflare
  Worker backed by R2 hosts a one-tap install page and serves the signed APK, an in-app
  updater checks for new versions and verifies the download's SHA-256 before handing it to
  the installer, and an access-gating layer lets me approve who's allowed in. One command
  (`scripts/release.ps1`) builds, signs, publishes, and verifies a release end to end.

- **Offline-first architecture.** Cache-first repositories with TTLs, stale-while-revalidate
  on the timetable, and any-age cache fallback when the network dies — so the app is useful
  on the metro, in a basement classroom, anywhere.

On the app's own security side: tokens live in `EncryptedSharedPreferences` behind the
Android Keystore with an optional biometric lock, API traffic is certificate-pinned, and
release builds strip debug logging so nothing sensitive lands in logcat.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · MVVM with `StateFlow` · Hilt · Retrofit + OkHttp ·
Room · DataStore · WorkManager · CameraX + ZXing · kotlinx.serialization ·
Cloudflare Workers + R2 · JUnit 5 + MockK.

Two-module Gradle project — `:app` (CampusCue) on top of `:core` (a reusable Android
library: security, networking, storage, theming, Compose components) — plus `worker/` for
the distribution backend. The full per-file tour and the build/reverse-engineering journey
are in **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Build & run

```bash
git clone <repo>
cd campus-cue
cp local.properties.template local.properties   # fill in the values below
./gradlew assembleDebug
```

`local.properties` (gitignored — never commit it) holds the secrets/signing config the
build injects via `BuildConfig`:

```
API_AUTH_TOKEN=<iCloudEMS auth token>
APP_SECRET=<Cloudflare Worker shared secret>
WORKER_URL=https://YOUR_WORKER.workers.dev
SENTRY_DSN=<optional; blank disables Sentry>
RELEASE_STORE_FILE=<keystore path>     # release builds only
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

A debug build works without the release/keystore values. The app is access-gated, so a
fresh build still needs an approved account to show real data.

## Deployment

CampusCue ships as a private, signed APK. After bumping the version in
`app/build.gradle.kts`, `./scripts/release.ps1 -ReleaseNotes "<what changed>"` (needs
Node.js for the Worker publish) verifies, builds, signs, publishes to R2, and confirms the
live update endpoint returns the new version with a matching hash. Users install once from
the Worker's `/install` page; the in-app updater handles everything after.
See **[docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md)**.

## License

All rights reserved. Published for review and as a record of how it was built — not
licensed for reuse or redistribution.
