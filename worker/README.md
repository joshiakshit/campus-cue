# CampusCue Worker

Cloudflare Worker used for private APK distribution and app update metadata.

## Endpoints

- `GET /install` - mobile-friendly install page for friends.
- `GET /download/android/:channel` - downloads the latest APK for a channel.
- `GET /health` - basic health check.
- `POST /update/check` - app updater endpoint. Requires `X-App-Secret`.
- `POST /users/register` - stores the app user's name and admission number only. Requires `X-App-Secret`.
- `POST /access/status` - creates/checks an app access request after OTP verification. Requires `X-App-Secret`.
- `POST /access/special` - marks a pending user as requesting special access. Requires `X-App-Secret`.
- `GET /users/list` - lists registered user names and admission numbers only. Requires `X-Admin-Secret`.
- `GET /admin/pending` - lists pending access requests. Requires `X-Admin-Secret`.
- `GET /admin/special` - lists pending special-access requests. Requires `X-Admin-Secret`.
- `POST /admin/approve` - approves one admission number. Requires `X-Admin-Secret`.

## Setup

1. Install dependencies:

   ```bash
   npm install
   ```

2. Create the R2 bucket:

   ```bash
   npx wrangler r2 bucket create campuscue-apks
   ```

3. Set the app secret. It must match `APP_SECRET` in Android `local.properties`.

   ```bash
   npx wrangler secret put APP_SECRET
   ```

4. Set a separate admin secret. Do not ship this value in the Android app.

   ```bash
   npx wrangler secret put ADMIN_SECRET
   ```

5. Deploy:

   ```bash
   npm run deploy
   ```

6. Put the deployed Worker URL in Android `local.properties`:

   ```properties
   WORKER_URL=https://YOUR_WORKER.workers.dev
   ```

## Publish a Release APK

Use a release-signed APK. Every future APK must use the same signing key.

```bash
npm run publish:update -- \
  --apk ../app/build/outputs/apk/release/app-release.apk \
  --version-code 2 \
  --version-name 1.0.1 \
  --min-supported-version-code 1 \
  --channel release \
  --release-notes "Login polish, compact settings themes, and updater fixes."
```

List registered users:

```bash
curl -H "X-Admin-Secret: <admin-secret>" \
  https://YOUR_WORKER.workers.dev/users/list
```

List pending users:

```bash
curl -H "X-Admin-Secret: <admin-secret>" \
  https://YOUR_WORKER.workers.dev/admin/pending
```

List special-access requests:

```bash
curl -H "X-Admin-Secret: <admin-secret>" \
  https://YOUR_WORKER.workers.dev/admin/special
```

Approve a user:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: <admin-secret>" \
  -d '{"admno":"ADMISSION_NUMBER"}' \
  https://YOUR_WORKER.workers.dev/admin/approve
```

PowerShell admin dashboard:

```powershell
.\admin-tools\campuscue-admin.ps1
```

Send friends:

```text
Install CampusCue here:
https://YOUR_WORKER.workers.dev/install

Android may ask you to allow installs from your browser once. After this first install, CampusCue checks for updates itself.
```
