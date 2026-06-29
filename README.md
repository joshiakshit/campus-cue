# CampusCue

A fast, polished Android client for **Galgotias University** students. CampusCue
turns the university's iCloudEMS student portal into a native Jetpack Compose app
with attendance analytics, a timetable, a leave planner, marks/results, and
self-serve QR attendance — all wrapped in a private, self-updating distribution
pipeline.

> Single-university by design. CampusCue targets GU only; the tenant/config naming
> in the code is configuration hygiene, not a multi-university roadmap.

<!--
  Add screenshots to docs/screenshots/ (dashboard / attendance / timetable / grades)
  then uncomment:
  <p align="center">
    <img src="docs/screenshots/dashboard.png" width="22%" alt="Dashboard" />
    <img src="docs/screenshots/attendance.png" width="22%" alt="Attendance" />
    <img src="docs/screenshots/timetable.png" width="22%" alt="Timetable" />
    <img src="docs/screenshots/grades.png" width="22%" alt="Grades" />
  </p>
-->

## What it does

- **Dashboard** — overall attendance %, at-risk subjects, bunkable budget, today's
  schedule with a live "next class" countdown.
- **Attendance** — per-subject breakdown with how many classes you can skip / must
  attend, plus a day-by-day recovery forecast.
- **Timetable** — weekly schedule with live in-progress indicators and week paging.
- **Day-wise** — a month calendar of present/absent/partial days.
- **Planner** — a leave simulator: tap future dates to see the projected impact on
  each subject's attendance.
- **Grades** — performance (component-wise marks insights) and results (semester
  report cards with PDF export).
- **QR attendance** — selfie-first in-app QR scanning (CameraX + ZXing) to mark
  attendance, plus a home-screen widget and app shortcut for one-tap access.
- **Quality of life** — light/dark/system themes, five color profiles + Material You
  dynamic color, biometric lock, offline-resilient caching, and a built-in
  self-updater.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · MVVM with `StateFlow` · Hilt · Retrofit +
OkHttp · Room · DataStore · WorkManager · CameraX + ZXing · kotlinx.serialization ·
Cloudflare Workers + R2 (distribution backend) · JUnit 5 + MockK.

## Project structure

Two-module Gradle project plus a serverless backend:

| Module | Package | Role |
|--------|---------|------|
| **`:app`** | `com.campuscue` | The CampusCue app — screens, ViewModels, repositories, the iCloudEMS API layer, DI wiring, and GU config. |
| **`:core`** | `com.joshi.core` | A reusable Android library (security, networking, storage, theming, Compose components, navigation). All deps are re-exported via `api()` so the app inherits them. Designed to be liftable into other projects. |
| **`worker/`** | — | A Cloudflare Worker (TypeScript) backed by R2 that hosts the install page, serves signed APKs, answers update checks, and gates access. |

A deep, per-file tour and the full build/reverse-engineering/distribution story live in
**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Build & run

```bash
git clone <repo>
cd campuscue-android
cp local.properties.template local.properties   # then fill in the values below
./gradlew assembleDebug
```

`local.properties` (gitignored — never commit it) holds the secrets and signing
config the build injects via `BuildConfig`:

```
API_AUTH_TOKEN=<iCloudEMS auth token>
APP_SECRET=<Cloudflare Worker shared secret>
WORKER_URL=https://YOUR_WORKER.workers.dev
SENTRY_DSN=<optional; blank disables Sentry>
RELEASE_STORE_FILE=<path to your signing keystore>     # release builds only
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

A debug build works without the release/keystore values. The app is access-gated, so
a fresh build still needs an approved account to show real data.

Common tasks (CI runs the first four):

```bash
./gradlew ktlintCheck    # lint
./gradlew detekt         # static analysis
./gradlew test           # unit tests (app + core)
./gradlew assembleDebug  # debug APK
./gradlew assembleRelease # minified, signed release APK
```

## Deployment & distribution

CampusCue is distributed as a **private, signed APK** (not on the Play Store). The
flow, end to end:

1. Bump `versionCode`/`versionName` in `app/build.gradle.kts`.
2. Run `./scripts/release.ps1 -ReleaseNotes "<what changed>"` (needs **Node.js** for the
   Worker publish step) — it verifies, builds a
   signed APK, asserts the signing certificate, publishes the APK + metadata to the
   Worker's R2 bucket, and confirms the live `/update/check` endpoint returns the new
   version with a byte-matching SHA-256.
3. Users install once from `https://YOUR_WORKER.workers.dev/install`; after that the
   in-app updater handles upgrades (daily background check + manual "Check for
   updates"), with SHA-256 verification before install and a forced-update floor for
   builds that drop below `minSupportedVersionCode`.

See **[docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md)** for the full checklist
and the manual fallback.

## Security & known follow-ups

- Secrets are injected at build time from gitignored `local.properties`; nothing
  sensitive is committed. Release builds strip debug logging (ProGuard
  `-assumenosideeffects`) so PII / raw API responses stay out of logcat.
- Token storage uses `EncryptedSharedPreferences` + the Android Keystore, with an
  optional biometric lock.
- API traffic is certificate-pinned. **The current pin set expires 2027-12-31 —
  refresh it before ~2027-06-01** or the app will fail to reach the server.
- **Worker hardening (planned):** add rate-limiting / brute-force protection to the
  admin routes and stricter `apkObjectKey` validation in the update manifest. The
  public `/install` and `/download` routes are intentionally unauthenticated for
  self-serve distribution.

## Roadmap ideas

Features under consideration: a home-screen attendance widget, an exam countdown, a
wallpaper-aware dark widget, and shareable attendance/marks cards. Engineering: split
the large `GradesViewModel` into per-tab ViewModels, and periodic audits that every
`LazyColumn` keeps stable keys + `contentType`.

## License

All rights reserved. This repository is published for review and portfolio purposes;
it is not licensed for reuse or redistribution.
