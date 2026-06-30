# CampusCue Release Checklist

## Before Sharing

- Build a release-signed APK with the long-term CampusCue signing key at `C:\Projects\campuscue-release.jks`.
- Confirm `local.properties` has production `API_AUTH_TOKEN`, `APP_SECRET`, and `WORKER_URL`.
- Run `./gradlew.bat ktlintCheck detekt test assembleRelease`.
- Install the release APK on a clean Android device.
- Log in, open Dashboard, Attendance, Day-wise, Timetable, Planner, Settings, and QR attendance.

## One-Command Release (recommended)

After bumping `versionCode`/`versionName` in `app/build.gradle.kts`, run:

```powershell
./scripts/release.ps1 -ReleaseNotes "<what changed>"
```

`scripts/release.ps1` reads the version from `build.gradle.kts` (so it can't drift),
then runs ktlintCheck → detekt → test → assembleRelease, asserts the APK is signed
with the long-term CampusCue cert, publishes to the Worker, verifies the live
`/update/check` returns the new version with a byte-matching SHA-256, and finally cuts
a matching **GitHub release** (`vX.Y.Z`, notes-only, with the install link) using the
cached git credential token — no `gh` CLI needed. It fails fast on any build/publish
mismatch; the GitHub-release step is non-fatal (warns if the tag already exists or the
commit isn't pushed yet, since the real distribution has already succeeded by then).
Pass `-MinSupported <code>` only when you intend a forced update (default keeps the
current floor). **Push your release commit to `origin/main` before running** so the
GitHub release tags the right commit.

Prerequisite (once): `cd worker && npm install`, and `npm run deploy` whenever the
Worker code itself changes.

## Manual Publish (fallback)

- From `worker/`, run `npm install` once, then `npm run deploy` if Worker code changed.
- Publish the APK using `npm run publish:update -- --apk <signed-apk> --version-code <code> --version-name <name> --min-supported-version-code <code> --channel release --release-notes "<notes>"`.
- Open `/install` on a phone and verify the APK downloads.
- Call `/update/check` with `X-App-Secret` and confirm the metadata matches the uploaded APK.

## Update QA

- Install an older signed APK from `/install`.
- Publish a higher `versionCode`.
- Use Settings -> Check for updates.
- Confirm download completes, SHA-256 verification passes, Android installer opens, and the update succeeds.
- For a forced update, set `minSupportedVersionCode` higher than the installed build and confirm the app blocks normal use.

## Message To Friends

```text
Install CampusCue here:
https://YOUR_WORKER.workers.dev/install

Android may ask you to allow installs from your browser once. That is normal for this private build. After the first install, CampusCue will check for updates inside the app.
```
