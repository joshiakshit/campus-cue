# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Product Goal

CampusCue is a single-university SaaS app for Galgotias University. Do not plan or build multi-university expansion unless the user explicitly changes the product direction. Tenant/config naming may remain in code as a lightweight way to isolate GU configuration, but the current product target is GU-only.

If you find or create any other planning document, roadmap, TODO list, implementation plan, or handoff note in this repo, update it to match this single-university goal and the latest completed work in this file.

## Latest Handoff Notes

- Keep the product direction as a GU-only single-university SaaS. Tenant abstractions are allowed only as configuration hygiene, not as a multi-university roadmap.
- Attendance QR is wired app-side: selfie first, optional gallery image attach, then in-app CameraX QR scanning with ZXing. The selfie step must use the front camera; QR scanning uses the rear camera. Do not add location permission back; QR submission uses fixed coordinates.
- Private APK self-updater is wired app-side: `WorkerApi.update/check`, `UpdateRepository`, `UpdatePolicy`, FileProvider cache paths, `REQUEST_INSTALL_PACKAGES`, Settings Updates UI, daily launch checks, optional prompts, forced update blocking, APK download, SHA-256 verification, and package installer launch are implemented.
- Worker distribution is now repo-side too: `worker/` contains a Cloudflare Worker backed by R2 with `/install`, `/download/android/:channel`, `/health`, and private `/update/check`, plus a publish script for release APK metadata.
- Worker is deployed at `https://YOUR_WORKER.workers.dev`; Android `local.properties` must keep `WORKER_URL` pointed there for release builds.
- Release signing is configured through gitignored `local.properties`; the keystore now lives at `C:\Projects\CampusCue\campuscue-release.jks` (moved with the 2026-06-28 repo reorg; `RELEASE_STORE_FILE` in local.properties updated to match). Back this file up carefully because all future APK updates must be signed with it.
- Repo was reorganized under `C:\Projects\CampusCue\` on 2026-06-28. As of that date the Android SDK and platform-tools (adb) were REMOVED from the PC — no `sdk.dir`/`ANDROID_HOME`/`android.jar` present — so no Gradle task can configure and no release APK can be built until the SDK is reinstalled (Android Studio or cmdline-tools `sdkmanager`). The cleanup code compiled+passed ktlint/detekt/test/assembleDebug earlier the same session before the SDK was removed.
- Login was redesigned as a GU-only polished phone OTP screen using the launcher logo. Do not re-add the tenant selector to login unless the product goal changes.
- Settings color profile selection is intentionally compact: horizontal chips instead of the older large grid.
- Planner reliability is the current fragile area to protect: combined PP+PR changes must refresh Planner automatically, combined subjects must show explicit combined badges/groups, and leave projections must use date-keyed timetable data when available.
- Planner now warms a 4-week date-keyed timetable cache on load/refresh. It should fall back to weekly expansion only if the dated endpoint or cached dated data is unavailable.
- If you maintain any other plan/TODO/handoff file, update it after changing this file so future agents do not follow stale goals.
- The repo is published privately at `https://github.com/joshiakshit/campuscue-android` (since 2026-06-10) with a fresh history starting at "Initial commit: CampusCue 1.6.5 (41)". The pre-GitHub development history (which contained committed `worker/node_modules`) is archived only at `C:\Projects\campuscue-history-backup.bundle` — do not delete that file. `expo-screenshots/` stays on disk but is gitignored. CI runs secretless (debug-only); release-secret validation hooks `packageRelease`/`packageReleaseBundle`, not `preReleaseBuild`.
- Latest signed release is `1.6.7 (43)`, published 2026-06-28 to remote R2 on the `release` channel via `scripts/release.ps1` with `minSupportedVersionCode = 39` (optional update). APK SHA-256 is `6ea84bc1df9723215cb41f2e4c280b46057f5c197f9e3a0cb86d04c35d3e71af`. It removes the GPA Planner and carries the pre-public cleanup. `/update/check` returned 43 with a byte-matching hash after publish; installed + verified on device (Grades = Performance + Result only). This was the first real run of `scripts/release.ps1` and it worked end to end.
- Toolchain after the 2026-06-28 machine cleanup: SDK reinstalled at `C:\Users\joshi\AppData\Local\Android\Sdk` (build-tools 35.0.0, platform 35, platform-tools/adb); Gradle runs on Adoptium JDK 21 (`~/.gradle/gradle.properties` sets `org.gradle.java.home` to `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` — do NOT use JDK 25, breaks Gradle 8.11); Node.js v24 reinstalled at `C:\Program Files\nodejs` (may need PATH prepend in fresh shells); wrangler auth cache under `%APPDATA%\xdg.config` intact. Run `./gradlew clean` once after the folder move if you hit stale-intermediate path errors.
- Pre-public-repo cleanup (2026-06-28, committed, unshipped): GPA Planner fully removed (6 files deleted + GPA symbols stripped from GradesState/GradesScreen/GradesViewModel/GradesModels; Grades = Performance + Result only). Deleted unused `core/.../GlassCard.kt` and tracked `app/src/main/ic_launcher-playstore.png`. ProGuard now strips `Log.d`/`Log.v` in release; `admno` dropped from debug logs; grades/student error-body logs demoted to `Log.d`. Live worker URL genericized to `YOUR_WORKER.workers.dev` in all tracked docs (CLAUDE.md, RELEASE_CHECKLIST, worker/README) for the public repo — real URL lives only in gitignored `local.properties` WORKER_URL. Added root `README.md`, `docs/ARCHITECTURE.md` (per-file map + journey), and `scripts/release.ps1` (one-command verify→build→sign-check→publish→endpoint-verify). User will create a NEW public GitHub repo and push; no LICENSE (all rights reserved). Screenshots deferred (no device/SDK) — README has a placeholder + instructions.
- `1.6.6 (42)` (superseded by 1.6.7) was published 2026-06-12; APK SHA-256 `7f01b7fad87e13e6732cfbe6cdfa608602f1c1af2ce458a2e747e8152fd83c3a`. It contained the GPA planner course-type redesign (since removed), the resilience batch, and the light-mode polish below.
- `1.6.5 (41)` (superseded) shipped the bottom clearance pass and sweep fixes; APK SHA-256 was `d4b83fda2aadd81cc1f015fbecf45fc9a14aefbf158741cee0f51689db2649de`.
- `1.6.4 (40)` (superseded) shipped the two UI feel passes below; APK SHA-256 was `c42db02d0f5fd8fe3348ffcf62df117bb751a9b86b81277e0f5560938ca04ce0`.
- UI feel pass (2026-06-10, shipped in 1.6.4): cold start now uses the SplashScreen API (`Theme.CampusCue.Starting` in `themes.xml`, dark `splash_background`, `installSplashScreen()` + `setKeepOnScreenCondition` in `MainActivity` with startup prefs/session loaded async on `Dispatchers.IO` instead of `runBlocking`); the global ripple is re-enabled at low alpha in `AppTheme` (`RippleConfiguration`, pressed 0.10) instead of `LocalRippleConfiguration provides null`; all main-source `collectAsState` calls were replaced with `collectAsStateWithLifecycle`.
- Bottom clearance pass (2026-06-10, shipped in 1.6.5): `AppFooter` is now `navigationBarsPadding()`-aware with `AppDimens.bottomNavClearance` (76dp) instead of a hardcoded 100dp, so clearance under the floating nav bar adapts to gesture vs 3-button navigation; the Settings tail spacer also respects nav-bar insets.
- Light-mode polish (2026-06-12, shipped in 1.6.6): light `primaryContainer` is now an opaque light tint (`base.blend(primary, 0.12f)`) instead of a translucent dark color that rendered as grey; new theme-aware `highlightColor(alpha)` in core AppTheme.kt replaces raw `primaryContainer.copy(alpha)` tints (timetable live slot/Today chip, dashboard LIVE row, day-wise selected date, changelog current card) — light mode tints with `primary`, dark unchanged. GPA "Projection model" box collapsed into a tappable "How projections work" row. `AccessCheckingScreen` uses `AnimatedIconLoader`. The GPA SEE-floor warning and red badge only appear once marks are entered (`projectedTotal() > 0`), so pristine cards don't show alarming red Fs.
- Resilience batch (2026-06-12, shipped in 1.6.6): (1) Hilt↔WorkManager wiring was broken since the workers were written — `@HiltWorker`s (`UpdateCheckWorker`, `ClassReminderWorker`) failed with NoSuchMethodException on every launch because the androidx.hilt KSP compiler was missing, `CampusCueApp` didn't implement `Configuration.Provider`+`HiltWorkerFactory`, and the manifest kept the default `WorkManagerInitializer`. All three fixed (catalog `androidx-hilt-compiler`, app KSP, App class, manifest `tools:node="remove"` on the initializer meta-data); verified on-device — clean launch, no worker errors. (2) Grades tab error fallback: `LoadingStateContainer` is now scoped per pager page in `GradesScreen` so one tab's failure (e.g. the transient GU edge/WAF HTTP 403 of 2026-06-11, a bare nginx block) no longer hides the other tabs or the tab bar. (3) Raw HTTP error bodies are never shown to users: both `requireBody` impls (GradesRepository + StudentApiParser) log the body and throw `IcloudServerException(code)`; `ErrorText` maps 403 to a dedicated "temporarily refused" message. (4) `core/ui/components/AnimatedIconLoader.kt` (staggered 4-icon wave + message, draw-phase-only animation via `graphicsLayer`) replaces the shimmer skeleton as `LoadingStateContainer`'s loading visual per user preference.
- GPA planner course-type redesign (2026-06-10, shipped in 1.6.6): the planner now models GU's three verified course structures — THEORY (IA1 25 + IA2 25 + MTE 50 + ETE 100 = 200), LAB (Lab Work 15 + Record 10 + Test 25 + Lab Exam 50 = 100), and THEORY_LAB (one combined grade; both panes; default all 8 components, max 300). Course kind is auto-detected from attendance lecTypes (PP+PR → THEORY_LAB, PR-only → LAB) and user-overridable via TYPE pills in the card's tune panel (`kindOverridden` shields from re-detection). Cards have a Theory|Lab pane switcher (THEORY_LAB only, UI-state only), per-course credit stepper (1–6; defaults 3/1/4), and the grade math is purely toggle-driven percent = enabled marks / enabled maxes. SGPA is credit-weighted (Σ gp×credits / Σ credits, `ProjectionResult.totalCredits`), the ≥25% SEE/ETE floor forces F (`failsSeeFloor`, strict <), and the summary card is labeled "Projected SGPA". Inputs persist via `PersistedGpaPlan` JSON in user-scoped prefs keyed `gpa_planner_v1_{yearId}_{classId}` (semester-separated; in-memory state only merges when the plan key is unchanged). Math + persistence covered by `app/src/test/.../ui/grades/GpaProjectionMathTest.kt` (22 tests). Grade cutoffs remain estimates because GU grading is relative — the official assessment tables were verified from galgotiasuniversity.edu.in on 2026-06-10.
- Sweep fixes (2026-06-10, shipped in 1.6.5): the multi-account UI was intentionally removed as dead code (deleted `ui/account/AccountSwitcherSheet.kt` and the never-triggered `showAccountSheet`/`onSwitchAccount`/`onAddAccount` wiring through MainApp/SessionGate/MainActivity; core `TokenManager` account APIs were kept because `getActiveAdmno`/`addAccount` are load-bearing for single-account auth and user-scoped prefs — do not rebuild a switcher UI unless the product direction changes). Day-wise now has an `OfflineBanner` (`isOffline` in `DaywiseViewModel`) and its auto-refresh-on-open is deferred until the Academics pager actually settles on the Day-wise page (`DaywiseScreen(isVisible=...)` → `onPageVisible()`, once per ViewModel) instead of firing from `init` when the neighboring page is pre-composed. `MonthCalendar` memoizes its cell grid. Subject names in Attendance rows and Timetable slot cards cap at 2 lines with ellipsis. `SubjectBadge`/`SlotBadge` were unified into core `ui/components/StatusBadge.kt`.
- UI feel pass 2 (2026-06-10, shipped in 1.6.4): dark color schemes now map `outlineVariant` and all `surfaceContainer*` tokens from the profile (cards/nav bar/shimmer harmonize with profiles); `ThemeMode.SYSTEM` added (cycle LIGHT→DARK→SYSTEM, resolved via `isSystemInDarkTheme()`, third Settings chip appears automatically, header icon crossfades incl. `BrightnessAuto`); Material You dynamic color is offered as a "Dynamic" profile chip on Android 12+ (`ColorProfiles.DYNAMIC_NAME`, handled in `AppTheme` via profile name); the Dashboard `TimelineCard` LIVE/past state now ticks every 30s like `NextClassCard`; light haptics added (QR FAB, QR success overlay, Academics/Grades pager settles, Planner day-cell long-press); `ErrorContent` in `LoadingState.kt` got a CloudOff icon and proper spacing.

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run all unit tests
./gradlew :core:test             # Run only core module tests
./gradlew :app:test              # Run only app module tests
./gradlew ktlintCheck            # Lint check (ktlint)
./gradlew ktlintFormat           # Auto-fix lint issues
./gradlew detekt                 # Static analysis (detekt)
./gradlew assembleRelease        # Build minified unsigned release APK
```

CI runs: ktlintCheck -> detekt -> test -> assembleDebug (see `.github/workflows/ci.yml`). Local ship verification currently also runs `assembleRelease`.

## Architecture

Two-module Gradle project with a version catalog (`gradle/libs.versions.toml`).

**`:core` (com.joshi.core)** — Reusable Android library. Designed to be copied wholesale into future projects. Contains security (EncryptedSharedPreferences token storage, Android Keystore, BiometricPrompt with CryptoObject), networking (OkHttp with retry interceptor and auth header injection, Retrofit builder), storage (DataStore preferences wrapper, Room BaseDao, TTL-based CachePolicy), Material 3 theming (color profile system with 5 presets: Iris/Forest/Slate/Amber/Crimson, dark/light/system modes, Inter font family with custom Typography), design tokens (AppDimens for spacing/elevation, AppShapes for pre-allocated corner shapes, SubjectColors for 7-color deterministic palette), reusable Compose components (LoadingState with Crossfade transitions, CacheBadge, ErrorSnackbar, BiometricGate, PullToRefresh, EmptyState, InAppBrowser), navigation scaffold with route-based transitions (fade for tabs, slide for push screens), WorkManager scheduling, crash reporting (Sentry), and network connectivity monitoring. All dependencies are exposed via `api()` so the app module inherits them transitively.

**`:app` (com.campuscue)** — CampusCue-specific code. Depends on `:core`. Contains Retrofit API interfaces for iCloudEMS endpoints (student/auth/worker plus QR attendance), Hilt DI wiring (AppModule provides SecretProvider, Json, Retrofit, API instances), Room database with generic CacheEntity for caching API responses, repository layer (cache-first with TTL), domain use cases (AttendanceUseCase, ForecastUseCase, TimetableUseCase, PlannerUseCase, UpdatePolicy), Compose screens with ViewModels, WorkManager jobs (ClassReminderWorker), private APK updater support, and GU tenant configuration.

**Data flow:** Compose UI → ViewModel (StateFlow) → UseCase → Repository → Retrofit API / Room DB / DataStore.

**DI:** Hilt with two modules — `CoreModule` provides OkHttpClient; `AppModule` provides SecretProvider, Json, 3 named Retrofits, Room database, and API interfaces. Both `@InstallIn(SingletonComponent::class)`.

## Screens & Navigation

Bottom nav has 5 tabs: Dashboard, Attendance, Day-wise, Timetable, Planner. Settings is accessed via a gear icon in the TopAppBar and uses a slide transition.

| Screen | Files | Purpose |
|--------|-------|---------|
| Dashboard | `ui/dashboard/` | Overall %, at-risk count, bunkable total, today's classes, subject cards |
| Attendance | `ui/attendance/` | Subject breakdown with bunk/need badges, recovery forecast cards |
| Planner | `ui/planner/` | Leave budget (day safety grid, spare periods), leave simulator (tap dates, projected impact) |
| Timetable | `ui/timetable/` | HorizontalPager with day tabs, slot cards with live progress, week navigation |
| Day-wise | `ui/daywise/` | Daily attendance calendar and per-day slots (present/absent/partial), month navigation |
| Settings | `ui/settings/` | Theme mode, color profile, threshold (±5 stepper), semester end date (DatePickerDialog), combined PP+PR toggle, homepage picker, biometric/reminders toggles, updater controls, cache clear, export, logout |
| Login | `ui/auth/` | Two-step OTP flow (phone → OTP), biometric gate wrapper |

## API & Auth

- **Auth flow:** Phone → requestOtp → validateOtp → JWT tokens stored in EncryptedSharedPreferences
- **3 Retrofit instances:**
  - `@Named("student")` → `gustudentapp.icloudems.com` (attendance, timetable) — uses raw JWT token (no "Bearer" prefix), sends referer + user-agent headers
  - `@Named("auth")` → `api.icloudems.com` (login, OTP, refresh) — uses API_AUTH_TOKEN from BuildConfig
  - `@Named("worker")` → Cloudflare Worker URL (status, request, update/check) — uses APP_SECRET header
- **AuthInterceptor** skips requests that already have an `authorization` header (so auth Retrofit's own header isn't overwritten)
- **Client code** for iCloudEMS API calls is `"GUSTUDENTAPP"` (not `"gu"`)
- **Sentry auto-init is disabled** in AndroidManifest via `tools:node="remove"` on SentryInitProvider. `CampusCueApp` manually initializes Sentry only when `SENTRY_DSN` is non-blank.
- **Worker updater:** `WorkerApi.checkUpdate()` posts to `update/check` with current version code/name, platform, and channel. The response must include latest/min-supported versions plus APK URL, SHA-256, size, and release notes for installable updates.
- **QR attendance:** `QrAttendanceApi` posts to `student/att/getattendanceqrtemp` and `student/att/qrscan` on `api.icloudems.com`.

## Secrets (local.properties, gitignored)

```
API_AUTH_TOKEN=<iCloudEMS auth JWT>
APP_SECRET=<Cloudflare Worker secret>
WORKER_URL=<Cloudflare Worker base URL>
SENTRY_DSN=<optional Sentry DSN, blank disables manual init>
```

## Domain Use Cases

- **AttendanceUseCase** — `bunkBudget()`, `mustAttend()`, `tone()` (OK/WARN/BAD), `atRiskCount()`, `totalBunkable()`
- **ForecastUseCase** — `buildForecast()` simulates day-by-day recovery with configurable `endDate` (semester end) or default 120-day horizon, returns projected reach dates and `maxReachable` percentage for unrecoverable subjects
- **TimetableUseCase** — week ranges, slot sorting, time parsing, academic year calculation, subject name cleaning
- **PlannerUseCase** — `buildPlannerSubjects()`, `analyzeDaySafety()`, `computeProjected()` (leave impact simulation)
- **UpdatePolicy / UpdateHash** - private APK update availability, forced-update rules, and SHA-256 verification helpers

## UI Architecture

- **Design tokens** live in `core/.../theme/`: `AppDimens` (spacing, elevation, sizing), `AppShapes` (pre-allocated RoundedCornerShape instances), `SubjectColors` (7-color deterministic palette via hashCode % 7). Use these instead of hardcoded dp values or inline shapes.
- **Expo-matching visual style**: All cards use flat `Surface` + `BorderStroke(1.dp, outline)` — zero elevation, zero shadow. No `ElevatedCard`, `OutlinedCard`, or `CardDefaults` in the app module. Screen padding 18dp, card padding 16dp, section gap 22dp, border radius 8dp (medium). Monospace `FontFamily.Monospace` for codes, times, percentages. Page titles 24sp/Bold/-0.5sp letterSpacing. Section labels 11sp/Bold/0.6sp letterSpacing uppercase. Stat numbers 22sp mono bold.
- **Inter font** bundled in `core/src/main/res/font/` (Regular/Medium/SemiBold/Bold). Custom `Typography` in `AppTheme.kt` with tuned weights, sizes, and letter-spacing.
- **UseCase objects must never be instantiated inside composables** — they cause GC pressure during scroll. Pre-compute display data in ViewModels (e.g., `DashboardSubject` with pre-computed tone, `DisplaySlot` with pre-computed displayName/progress).
- **All `items()` calls must have stable keys** — e.g., `key = { "${it.subCode}_${it.lecType}" }`.
- **LoadingStateContainer** wraps content in `Crossfade` for smooth loading→content transitions.
- **NavGraph** accepts `slideRoutes: Set<String>` — routes in this set get slide transitions, others get fade.
- **Status bar insets** handled by `AppScaffold` via `contentWindowInsets = WindowInsets.statusBars`. No TopAppBar — each screen renders its own header inline with content.

## Key Conventions

- Kotlin DSL for all Gradle files; dependency versions only in `libs.versions.toml`
- JUnit 5 + MockK + Turbine for testing; `useJUnitPlatform()` in both modules
- KSP (not kapt) for Hilt and Room annotation processing
- `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`, Java 17
- Kotlinx Serialization (not Gson/Moshi) for JSON — APIs return JsonElement, parsed per-method with explicit KSerializer
- detekt config: max line length 140, MagicNumber disabled, Composable function names allowed via `functionPattern: "[a-zA-Z][a-zA-Z0-9]*"`
- Room schema exports to `app/schemas/`
- No comments in code unless explicitly requested
- Never add Co-Authored-By to commits

## Implementation Status

**Completed:**
- Phase 1: Core shell (all `:core` modules)
- Phase 2: Auth + API (OTP login, repositories, caching, biometric gate, tests)
- Phase 3: All data screens (Dashboard, Attendance, Day-wise, Timetable) + use case tests
- Phase 4: Planner screen, Settings screen, color profile switching wired into MainActivity, ClassReminderWorker, InAppBrowser component, offline mode toggle, Android Print-framework PDF export for attendance and timetable, accessibility labels/roles for custom controls
- UI Polish & Performance Overhaul: Eliminated scroll stutter (UseCase instantiation moved from composables to ViewModels, stable LazyColumn keys, pre-sorted data). Design token system (AppDimens, AppShapes, SubjectColors). Inter font bundled with custom Typography. Animation layer (Crossfade loading states, animateColorAsState for tones, animateFloatAsState for progress bars, graphicsLayer alpha pager fade, nav route transitions). Per-screen polish (subject color stripes, hero progress animation, week navigator).
- Expo UI Replication: All screens rewritten to match the Expo reference app's visual design. Flat cards with 1px borders (no elevation/shadow). Monospace font for codes/times/percentages. Expo-matching spacing (18dp screen pad, 8dp border radius, 22dp section gap). Status bar insets fixed via `WindowInsets.statusBars` in AppScaffold. Timetable day selector uses pill-style LazyRow instead of ScrollableTabRow. All `ElevatedCard`/`OutlinedCard`/`CardDefaults` replaced with `Surface` + `BorderStroke`. Typography uses explicit `fontSize`/`fontWeight` matching Expo's 24sp titles, 44sp hero percentages, 11sp section labels.
- UI Polish Round 2: Dashboard enrichments (bunkable stat, LIVE badge, time greeting, subject list). Collapsible recovery forecast in Attendance. Planner reorder (calendar first, collapsible leave budget). Month navigation in leave simulator. Decimal precision (%.2f) across all screens. Max-reachable display when subjects can't hit threshold. Settings overhaul (sleek +/- threshold control, semester end date picker with Material 3 DatePickerDialog, combined PP+PR attendance toggle, simplified homepage picker replacing NavCustomizer). Settings navigation toggle fix. `surfaceContainerLow` for all card backgrounds in light mode.
- Performance & Offline Resilience: Baseline Profiles via `profileinstaller`. Heavy UseCase computation moved to `Dispatchers.Default` in all ViewModels (Dashboard, Attendance, Planner). `remember(key)` for `String.format()` in scroll-hot composables. Spring-based predictive back animations in `CoreNavHost`. Shimmer skeleton loading placeholders replace `CircularProgressIndicator`. Offline-resilient caching: all repository methods fall back to any-age cached data when network fails (`cachedAnyAge`). `OfflineBanner` composable shows "Offline · showing cached data" in Dashboard and Attendance when `NetworkMonitor.isOnline` is false. `CachedResult` wrapper in CachePolicy for future stale-data indicators.
- 90Hz Scroll Jank Fix & Animations: Compose compiler stability config (`compose-stability.conf`) marks all UI state and domain data classes as stable to enable skipping. Removed per-item `animateColorAsState`/`animateFloatAsState` from `SubjectRow` (direct values instead). `contentType` on all LazyColumn items across all screens for proper view recycling. `SubjectSummaryList` sort wrapped in `remember(subjects)`. `animateItem()` modifier on repeating LazyColumn items (Attendance subject rows, Timetable slot cards, Planner impact cards) for entrance/exit animations.
- Attendance QR: Selfie-first QR attendance flow wired through CameraX and ZXing. Users can take a front-camera selfie or attach an existing image before scanning the QR. Location permission was removed; QR submission uses fixed coordinates because the server does not require live location.
- Private APK Self-Updater: Settings includes an Updates section. `WorkerApi.update/check`, `UpdateRepository`, `UpdatePolicy`, FileProvider cache paths, `REQUEST_INSTALL_PACKAGES`, daily launch checks, optional update prompts, forced update blocking, APK download, SHA-256 verification, and package installer launch are implemented.
- Planner Reliability: Planner observes combined attendance, threshold, and semester-end preference changes and reloads automatically. Combined PP+PR subjects render explicit combined badges/groups. Planner warms a 4-week date-keyed timetable cache on load and falls back to weekly expansion only if the dated endpoint/cache is unavailable.
- Login & Distribution Polish: Login is now a GU-only Material OTP screen using the app launcher logo, Settings color profiles use compact chips, and `worker/` provides the Cloudflare Worker/R2 install and update distribution path.

**Current verification:**
- 2026-05-23 Codex pass: `./gradlew.bat ktlintCheck detekt test assembleDebug` and `./gradlew.bat assembleRelease` both pass after the Planner warm date-keyed cache fix and CLAUDE.md updates.
- `./gradlew ktlintCheck detekt test assembleDebug assembleRelease` passes.
- Latest debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Latest release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- No device/emulator was attached during the latest verification pass, so live install/tap-through still needs a connected device.

**Phase 5 (Ship Single-University SaaS):**
- Play Store listing prep
- Release signing / AAB setup for Play Console
- Final connected-device QA pass
- Production Sentry DSN, privacy policy, Data Safety form, screenshots, and listing copy

## Known Fixes Applied

- `network_security_config.xml`: Removed placeholder cert pin (invalid base64 crashed app startup)
- `AndroidManifest.xml`: Disabled Sentry auto-init; startup uses explicit opt-in manual init
- `AppModule.kt`: Worker Retrofit falls back to `https://localhost/` when WORKER_URL is empty
- `AuthInterceptor.kt`: Sends raw token (no "Bearer" prefix), adds referer/user-agent; skips if request already has authorization header
- `TenantConfig.kt`: clientCode is `"GUSTUDENTAPP"` not `"gu"`
- `org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr` must be set in the **user-level** `~/.gradle/gradle.properties` (JDK 25 doesn't work with Gradle 8.11). It must NOT be in the repo's `gradle.properties` — that breaks CI on Linux runners.
- Student API requests now match the working Expo reference app: JSON request bodies, raw JWT authorization, numeric `br_id`, actionable HTTP error bodies, and tolerant response parsing.
- Attendance, Planner, Day-wise, and Dashboard use latest discovered semester/class data instead of assuming a stale semester.
- Timetable uses the current academic-year guess first, then falls back through attendance academic-year discovery like the Expo app.
- Loading errors now surface the actual repository error text instead of only "Something went wrong".
- Attendance and Timetable have print actions that create report output through Android's Print framework.
- Release builds validate required API_AUTH_TOKEN, APP_SECRET, and HTTPS WORKER_URL before `preReleaseBuild`.
- R8 release minification and resource shrinking pass. ProGuard suppresses Tink/ErrorProne annotation warnings generated by AndroidX Security.
- Sentry auto-init remains disabled in the manifest. `CampusCueApp` manually initializes Sentry only when `SENTRY_DSN` is non-blank.
- `network_security_config.xml` pins `icloudems.com` subdomains using live pins fetched on 2026-05-20 for `gustudentapp.icloudems.com` and `api.icloudems.com`; pin-set expires on 2027-12-31.
- GU tenant configuration exists via `Tenants.GU`, `Tenants.all`, `TenantRepository`, and login UI display. This is for single-university configuration hygiene, not a multi-university roadmap.
- Planner projection now respects the combined PP+PR attendance preference. Previously, PlannerViewModel always treated PP and PR as separate subjects, so combined subjects showed wrong projected percentages. `combineSubjects()` extracted from ViewModels to `AttendanceUseCase` (shared). `PlannerUseCase.computeProjected()` uses `buildSlotMatcher()` to route both PP and PR timetable slots to the combined `PP+PR` subject when applicable.
- Planner projection now uses **date-keyed** timetable data instead of repeating the current week's schedule. `TimetableRepository.getDateKeyedTimetable()` returns `Map<LocalDate, List<TimetableSlot>>` preserving per-date slots, so holidays and schedule changes are respected. `PlannerViewModel` warms the range from today through the next 4 weeks on load, refreshes that cache when Planner is refreshed, lazily extends it for farther selected dates, and falls back to weekly expansion only when the dated endpoint/cache is unavailable. Planner header now shows attendance ratio (`present/total attended`) instead of threshold target.
- QR attendance no longer launches the rear camera for selfie capture. The selfie step uses the front camera or gallery attach, then immediately opens the in-app QR scanner after a successful image choice.
- Private APK updates are app-side complete. The remaining deployment dependency is publishing signed APK metadata from the Worker with matching `latestVersionCode`, `minSupportedVersionCode`, HTTPS APK URL, SHA-256, size, and release notes.
- Worker implementation lives in `worker/`. Use `worker/README.md` and `docs/RELEASE_CHECKLIST.md` for deploy/publish flow. Friends should get the Worker `/install` link, not raw APK files in chat.
- `1.5.8 (34)` (superseded; releases 1.5.9–1.6.4 followed, see handoff notes and `ui/settings/Changelog.kt` for the full history) was Phase 3 of the roadmap plus an error-handling pass. Phase 3: a QR-scan home-screen widget (`widget/QrScanWidgetProvider`, `res/layout/widget_qr_scan.xml`, `res/xml/qr_scan_widget_info.xml`) and a static app shortcut (`res/xml/shortcuts.xml`) that deep-link via the `MainActivity.ACTION_SCAN_QR` intent (handled in `onCreate` + `onNewIntent`, `singleTop`) → `MainApp` navigates to attendance and signals `AttendanceScreen(autoOpenQrSignal)` (read live through a remembered state since the NavHost graph is built once); respects login/biometric gating. Plus a Dashboard "Next class" countdown (`NextClassInfo` in `DashboardViewModel`, 30s ticker in `DashboardScreen`). The widget background is a fixed Iris purple `#7C5CFC` because a home-screen widget can't read the in-app color profile. Error-handling pass: `IcloudServerException` thrown from `requireBody` (HTTP 5xx) and `parseStudentResponse` (non-JSON/HTML) in both repos, 401 → `SessionExpiredException`; `ui/ErrorText.kt` classifier maps exceptions to plain-English messages (server-down / offline / session-expired / bad-input) applied in all 5 data ViewModels and `LoginViewModel` (login distinguishes unregistered-number vs wrong-OTP); `RetryInterceptor` now retries only 5xx with exponential backoff; `getSemesterOptions` tolerates a per-year `getClasses` failure and only rethrows if all years fail. Deferred: dashboard streak/7-day-trend (need historical day-wise data not currently loaded). The roadmap (loading speed, UI polish, QR quick-access) is now complete.
- `1.5.7 (33)` (superseded) was Phase 2 of the improvement roadmap (UI polish): `OfflineBanner` added to Timetable (`TimetableScreen`/`TimetableViewModel`) and Planner (`PlannerScreen`/`PlannerViewModel`) via new `isOffline` state (Day-wise is covered by Attendance's banner since it is embedded there, plus its own last-updated label); `overflow = TextOverflow.Ellipsis` added to single-line subject/label `Text`s in Dashboard and Planner; theme-safe colors — the timetable substitution badge now uses `colorScheme.tertiary` instead of hardcoded `0xFFFF9800`, and the Settings color-profile selection dot picks black/white by `color.luminance()` so it stays visible on light profiles. Accessibility was reviewed and left as-is (icon-only controls already labeled; flagged icons sit beside text TalkBack reads). Phase 3 (QR-scan launcher widget + app shortcut + dashboard enrichments) remains.
- `1.5.6 (32)` (superseded) was Phase 1 of the improvement roadmap (loading speed): Dashboard/Attendance/Planner now fetch attendance + timetable concurrently (`coroutineScope`/`async`); `AttendanceRepository.getSemesterOptions` fans out per-year `getClasses` concurrently; `AuthRepository.refreshTokenIfNeeded` is now `Mutex`-guarded with a double-check to prevent concurrent double token-refresh (which could rotate-invalidate the refresh token and force a logout); the Timetable screen uses stale-while-revalidate via `TimetableRepository.peekTimetable` (renders any-age cached schedule instantly, revalidates in the background when stale/expired); and `MainActivity.onThemeToggle` no longer blocks the UI thread on a DataStore write. Intentionally skipped: OkHttp HTTP cache (student API is POST-only, not HTTP-cacheable), and the `initialSessionState` startup `runBlocking` (left to avoid an access-screen flash). SWR was scoped to Timetable only (highest payoff at 1h freshness); Day-wise intentionally still force-refreshes on open, and Attendance/Dashboard already return fresh/stale cache without a network round-trip.
- `1.5.5 (31)` (superseded) refined the QR success overlay (faster animation, opaque background, static confirmation text instead of the raw server JSON) and removed the post-scan attendance refresh entirely. Rationale: scanning the QR only marks the student present in the QR system; the official attendance record the app reads does not change until the teacher generates and submits the absentee list (hours/days later), so refreshing right after a scan showed stale data and falsely looked like the scan did not count. Attendance now updates only on natural refreshes (pull-to-refresh / app open / daily load).
- `1.5.4 (30)` (superseded) moved the recovery forecast dropdown to Planner, reworked the Planner date interaction (tap to preview attendance by a date, long-press to mark absent), added Day-wise explicit reload + auto-refresh-on-open + "last updated" indicator, fixed the QR success overlay z-order, and added digital zoom (up to 10×) to the QR scanner so distant classroom QRs decode via a center-crop on the analyzer.
