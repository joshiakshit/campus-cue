# CampusCue — Architecture & Journey

This document is the deep tour: how the app is built, what every file does, and the
story of how it came together. For a quick overview see the [README](../README.md).

---

## 1. The big picture

CampusCue is a two-module Android project (`:app` + `:core`) with a serverless
distribution backend (`worker/`).

```
Compose UI  →  ViewModel (StateFlow)  →  UseCase  →  Repository  →  ┬ Retrofit API (iCloudEMS / Worker)
                                                                    ├ Room (response cache)
                                                                    └ DataStore (preferences)
```

- **`:core` (`com.joshi.core`)** is a self-contained, reusable Android library:
  security, networking, storage, theming, Compose components, navigation. Everything
  is exported with `api()` so the app inherits it transitively. It knows nothing
  about CampusCue and could be dropped into another app.
- **`:app` (`com.campuscue`)** is everything GU-specific: the iCloudEMS API surface,
  repositories, domain use cases, and the Compose screens.
- **`worker/`** is a Cloudflare Worker (TypeScript) + R2 bucket that distributes the
  signed APK and answers update checks.

**DI:** Hilt. `CoreModule` provides the `OkHttpClient`; `AppModule` provides the
`SecretProvider`, JSON, three named Retrofit instances (student / auth / worker), the
Room database, and the API interfaces.

---

## 2. The journey (why this exists and how it was built)

**The problem.** GU students get attendance and marks through iCloudEMS, a generic
SaaS portal that's slow on mobile and shows raw numbers with no "can I skip this
class?" insight. CampusCue was built to make that data fast, native, and *useful*.

**Talking to the portal.** There's no public API, so the app speaks the same HTTP
protocol the official web/Expo client uses: phone → OTP → JWT, then authenticated
calls to the student endpoints for attendance, timetable, marks, and results. The
repository layer mirrors the request shapes the portal expects (raw JWT auth, form
bodies, tolerant parsing of inconsistent responses) and adds what the portal lacks —
caching, offline fallback, and derived analytics. Endpoint specifics are intentionally
kept at the integration level here rather than written up as a how-to.

**Distribution without the Play Store.** Publishing a single-university app to the
Play Store isn't worth the friction, so CampusCue ships as a privately distributed,
signed APK. A Cloudflare Worker backed by R2 hosts an `/install` page and serves the
APK; the app has a built-in updater that checks the Worker, verifies the download's
SHA-256, and launches the system installer. A small access-gating layer lets the
owner approve who can use it.

**QR attendance.** GU marks attendance via classroom QR codes. CampusCue scans them
in-app (CameraX + ZXing) behind a selfie step, so a student never leaves the app.

**Where it is now.** The app has been through many polish/perf passes (90 Hz scroll
work, splash + loading states, theming, resilience). This repo is the cleaned-up,
public-presentable cut: the experimental GPA-projection planner was removed, dead code
trimmed, logging hardened, and ops details genericized.

---

## 3. `:app` — file by file

### `data/api/` — Retrofit interfaces (the wire)
| File | What it does |
|------|--------------|
| `AuthApi.kt` | iCloudEMS OTP login / token refresh (the `auth` Retrofit). |
| `ICloudEmsApi.kt` | Student data: attendance, timetable, grades report-card controllers. |
| `QrAttendanceApi.kt` | QR attendance temp-token + scan-submit endpoints. |
| `WorkerApi.kt` | The Cloudflare Worker: access status/request, update check. |

### `data/repository/` — cache-first data access
| File | What it does |
|------|--------------|
| `AttendanceRepository.kt` | Attendance + semester/class discovery; fans out per-year lookups concurrently. |
| `TimetableRepository.kt` | Weekly + date-keyed timetable; stale-while-revalidate; academic-year fallback. |
| `GradesRepository.kt` | Performance marks + semester results + report-card PDF; tolerant HTML/JSON parsing. |
| `AuthRepository.kt` | Login, user info, token refresh (mutex-guarded to avoid double refresh), access gating. |
| `UpdateRepository.kt` | Talks to the Worker for update metadata; feeds `UpdatePolicy`. |
| `AttendanceCacheStore.kt`, `TimetableCacheStore.kt` | Typed wrappers over the generic cache for those domains. |
| `StudentApiParser.kt` | Shared response handler: extracts JSON from messy bodies, maps HTTP failures to typed exceptions. |
| `GradesParser.kt`, `TimetableNormalizer.kt`, `QrScanResultParser.kt` | Feature-specific parsing/normalization (HTML tables, slot dedup, QR result). |
| `IcloudServerException.kt`, `SemesterOptions.kt` | Error wrapper (carries HTTP code) and the semester/class selector model. |

### `data/db/` — Room cache
| File | What it does |
|------|--------------|
| `AppDatabase.kt` | Room database singleton. |
| `CacheEntity.kt` | One generic, TTL-aware cache row (key + JSON blob + timestamp) reused for every API response. |
| `CacheDao.kt` | Get/put with age queries. |

### `domain/model/` — serializable data classes
`Attendance.kt`, `Auth.kt`, `Timetable.kt`, `GradeModels.kt`, `QrScanResult.kt` — the
domain objects passed UI-ward; kotlinx.serialization-annotated where they're cached.

### `domain/usecase/` — pure business logic (unit-tested)
| File | What it does |
|------|--------------|
| `AttendanceUseCase.kt` | Bunk budget, "must attend", OK/WARN/BAD tone, at-risk count, PP+PR combining. |
| `ForecastUseCase.kt` | Day-by-day recovery simulation toward the threshold. |
| `TimetableUseCase.kt` | Week ranges, slot sorting/time parsing, academic-year calc, name cleanup. |
| `PlannerUseCase.kt` | Leave-budget day-safety analysis and projected-impact simulation. |
| `UpdatePolicy.kt` | Up-to-date / optional / forced decision + SHA-256 verification helpers. |

### `ui/` — Compose screens (each folder = one feature: `*Screen` + `*ViewModel` + parts)
- **Shell:** `MainActivity.kt` (SplashScreen API, async startup), `MainApp.kt` (nav graph + floating bottom bar + QR FAB), `AppHeader.kt`/`AppFooter.kt`, `SessionGate.kt` (auth/access gate), `AppUpdateGate.kt` (forced-update blocker), `ErrorText.kt` (exception → friendly message).
- **`dashboard/`** — overview: stats, today's timeline with live tickers, at-risk list.
- **`attendance/`** — subject rows with bunk/need badges + recovery forecast.
- **`daywise/`** — month calendar + per-day slots (its own offline banner).
- **`timetable/`** — day pager, slot cards with live progress, week nav.
- **`planner/`** — leave budget grid + tap-a-date impact simulator.
- **`grades/`** — `GradesScreen` hosts two tabs: **Performance** (marks insights:
  `PerformanceTab`, `PerformanceFilterPanel`, `PerformanceMarksTable`,
  `MarksInsightsScreen`) and **Result** (`ResultTab`, `PdfViewer`). `GradesModels.kt`
  holds shared display helpers; `GradesViewModel` drives both.
- **`qr/`** — `QrScanFlow` orchestrates selfie (`QrSelfieDialog`) → scan
  (`QrScannerDialog`, `QrCamera` = CameraX+ZXing) → submit (`QrAttendanceDialog`),
  with `QrImageUtils` and result overlays.
- **`settings/`** — appearance/theme, attendance threshold + semester end + combined
  PP+PR, updates, export, support/about, and `Changelog`.
- **`auth/`** — two-step OTP login, biometric gate, and the access-state screens
  (waiting / banned / force-reauth).
- **`export/`** — Android Print-framework PDF rendering for attendance & timetable.

### `di/`, `tenant/`, `widget/`, `worker/`
`AppModule.kt` (Hilt graph) · `TenantConfig.kt` (GU config: hosts, client code) ·
`QrScanWidgetProvider.kt` (home-screen QR widget) · `UpdateCheckWorker.kt` &
`ClassReminderWorker.kt` (WorkManager background jobs).

---

## 4. `:core` — file by file

| Area | Files | Role |
|------|-------|------|
| `security/` | `TokenManager` (EncryptedSharedPreferences + mutex refresh), `KeystoreHelper`, `BiometricManager` (CryptoObject), `SecretProvider` | Token storage, crypto, biometrics. |
| `network/` | `SecureHttpClient`, `AuthInterceptor` (raw-JWT + referer/UA injection), `RetryInterceptor` (5xx backoff), `NetworkMonitor` | HTTP stack + connectivity. |
| `storage/` | `PreferencesStore` (DataStore wrapper, user-scoped keys), `BaseDao`, `CachePolicy` (TTL freshness) | Persistence primitives. |
| `ui/components/` | `LoadingState`, `AnimatedIconLoader`, `ShimmerEffect`, `PullToRefresh`, `OfflineBanner`, `EmptyState`, `ErrorSnackbar`, `CacheBadge`, `StatusBadge`, `BiometricGate`, `InAppBrowser` | Reusable Compose widgets. |
| `ui/navigation/` | `AppScaffold` (insets + floating bar), `NavGraph` (route-based fade/slide transitions) | App shell scaffolding. |
| `ui/theme/` | `AppTheme` (M3 light/dark/system + `highlightColor`), `ColorProfiles` (5 presets + dynamic), `AppDimens`, `AppShapes`, `SubjectColors`, `ThemeState` | Design system + tokens. |
| `util/` | `Result`, `DateUtils`, `FlowUtils` | Small shared helpers. |
| `worker/`, `crash/`, `di/` | `BaseScheduler` (WorkManager helper), `CrashReporter` (manual Sentry), `CoreModule` | Infra glue. |

---

## 5. Auth & access-gating model

1. **Login:** phone → `requestOtp` → `validateOtp` → JWTs stored encrypted.
2. **Access gate:** `SessionGate` checks the Worker for the account's status —
   `APPROVED` (enters the app), `PENDING` (waiting screen), `BANNED`, or
   `FORCE_REAUTH`. Approval is cached and re-checked roughly daily / on resume.
3. **Token refresh** is mutex-guarded so concurrent screens can't rotate-invalidate
   the refresh token.
4. **Biometric lock** (optional) wraps the approved app behind `BiometricGate`.

---

## 6. Caching strategy

Every API response can be cached as a generic `CacheEntity` (key + JSON + timestamp)
with a per-domain TTL via `CachePolicy`. Repositories are cache-first: serve fresh
cache without a network hit, fall back to *any-age* cache when the network fails
(so the app stays usable offline), and surface an `OfflineBanner` when appropriate.
Timetable additionally uses stale-while-revalidate (instant render, background
refresh) and warms a 4-week date-keyed window for the planner.

---

## 7. The Worker (`worker/`)

A single `src/index.ts` on Cloudflare Workers + R2 (`worker/README.md` has setup):

- **Public:** `GET /install` (mobile install page), `GET /download/android/:channel`
  (latest APK), `GET /health`.
- **App (X-App-Secret):** `POST /update/check`, plus access register/status/special.
- **Admin (X-Admin-Secret):** list/approve users and access requests.
- `scripts/publish-update.mjs` hashes a signed APK, writes the channel manifest
  (version, min-supported, object key, SHA-256, size, notes), and uploads both APK and
  manifest to R2. `scripts/release.ps1` wraps this with build + signature + endpoint
  verification.

---

## 8. Testing & CI

JUnit 5 + MockK. Use cases carry the unit tests (`AttendanceUseCaseTest`,
`ForecastUseCaseTest`, `PlannerUseCaseTest`, `TimetableUseCaseTest`,
`UpdatePolicyTest`, `AuthTokenTest`) plus core tests (`CachePolicyTest`,
`DateUtilsTest`, `ResultTest`). CI (`.github/workflows/ci.yml`) runs
ktlintCheck → detekt → test → assembleDebug on every push/PR, with **no secrets** —
release signing and publishing happen only locally via `scripts/release.ps1`.

---

## 9. Conventions

Kotlin DSL Gradle with a version catalog (`gradle/libs.versions.toml`); KSP (not kapt);
kotlinx.serialization (not Gson/Moshi); flat `Surface` + 1dp-border cards (no
elevation); design tokens over hardcoded values; stable `LazyColumn` keys +
`contentType`; detekt max line length 140; no code comments unless necessary.
