export interface Env {
  APK_BUCKET: R2Bucket;
  APP_SECRET: string;
  ADMIN_SECRET?: string;
  DEFAULT_CHANNEL?: string;
  PUBLIC_BASE_URL?: string;
}

type UserRecord = {
  admno: string;
  name: string;
  status: "pending" | "approved" | "banned";
  requestedAt: string;
  approvedAt?: string;
  specialAccessRequestedAt?: string;
  revokedAt?: string;
  bannedAt?: string;
  referralsLeft?: number;
  referredBy?: string;
};

type ChannelManifest = {
  platform: "android";
  channel: string;
  latestVersionCode: number;
  latestVersionName: string;
  minSupportedVersionCode: number;
  apkObjectKey: string;
  sha256: string;
  sizeBytes: number;
  releaseNotes?: string;
  publishedAt: string;
};

const APK_MIME = "application/vnd.android.package-archive";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(request.url);
      const parts = url.pathname.split("/").filter(Boolean);

      if (request.method === "OPTIONS") return corsResponse();

      if (request.method === "GET" && parts.length === 0) {
        return Response.redirect(new URL("/install", url).toString(), 302);
      }

      if (request.method === "GET" && parts[0] === "health") {
        return json({ ok: true, service: "campuscue-updates" });
      }

      if (request.method === "GET" && parts[0] === "install") {
        return installPage(request, env);
      }

      if ((request.method === "GET" || request.method === "HEAD") && parts[0] === "download" && parts[1] === "android") {
        return downloadApk(parts[2] ?? defaultChannel(env), env, request.method !== "HEAD");
      }

      if (request.method === "POST" && parts[0] === "update" && parts[1] === "check") {
        return updateCheck(request, env);
      }

      if (request.method === "POST" && parts[0] === "users" && parts[1] === "register") {
        return registerUser(request, env);
      }

      if (request.method === "POST" && parts[0] === "access" && parts[1] === "status") {
        return accessStatus(request, env);
      }

      if (request.method === "POST" && parts[0] === "access" && parts[1] === "special") {
        return requestSpecialAccess(request, env);
      }

      if (parts[0] === "admin") {
        return handleAdmin(request, env, parts.slice(1));
      }

      if (request.method === "GET" && parts[0] === "users" && parts[1] === "list") {
        return listUsers(request, env);
      }

      return json({ error: "Not found" }, 404);
    } catch (error) {
      if (error instanceof Response) return error;
      return json({ error: "Internal server error" }, 500);
    }
  },
};

function handleAdmin(request: Request, env: Env, parts: string[]): Promise<Response> {
  const action = parts[0];

  if (request.method === "GET" && action === "dashboard") return adminDashboard(env);
  if (request.method === "GET" && action === "users") return listUsers(request, env);
  if (request.method === "GET" && action === "pending") return listPendingUsers(request, env);
  if (request.method === "GET" && action === "special") return listSpecialAccessUsers(request, env);
  if (request.method === "POST" && action === "approve") return approveUser(request, env);
  if (request.method === "POST" && action === "dummy-user") return createDummyUser(request, env);
  if (request.method === "POST" && action === "revoke") return revokeUser(request, env);
  if (request.method === "POST" && action === "revoke-all") return revokeAllUsers(request, env);
  if (request.method === "POST" && action === "ban") return banUser(request, env);
  if (request.method === "POST" && action === "unban") return unbanUser(request, env);
  if (request.method === "POST" && action === "referrals") return setReferrals(request, env);
  if (request.method === "POST" && action === "force-reauth") return forceReauth(request, env);
  if (request.method === "POST" && action === "clear-reauth") return clearReauth(request, env);

  return Promise.resolve(json({ error: "Not found" }, 404));
}

function corsResponse(): Response {
  return new Response(null, {
    status: 204,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, X-Admin-Secret, X-App-Secret",
      "Access-Control-Max-Age": "86400",
    },
  });
}

function requireAppSecret(request: Request, env: Env): void {
  if (!env.APP_SECRET || request.headers.get("X-App-Secret") !== env.APP_SECRET) {
    throw json({ error: "Unauthorized" }, 401);
  }
}

function requireAdminSecret(request: Request, env: Env): void {
  if (!env.ADMIN_SECRET || request.headers.get("X-Admin-Secret") !== env.ADMIN_SECRET) {
    throw json({ error: "Unauthorized" }, 401);
  }
}

function resolveStatus(raw: unknown): "pending" | "approved" | "banned" {
  if (raw === "approved") return "approved";
  if (raw === "banned") return "banned";
  return "pending";
}

async function getAllUserRecords(env: Env): Promise<UserRecord[]> {
  const users: UserRecord[] = [];
  let cursor: string | undefined;

  do {
    const listed = await env.APK_BUCKET.list({
      prefix: "users/",
      ...(cursor ? { cursor } : {}),
    });

    for (const obj of listed.objects) {
      try {
        const data = await env.APK_BUCKET.get(obj.key);
        if (!data) continue;
        const raw = (await data.json()) as Record<string, unknown>;
        if (!raw.admno || !raw.name) continue;
        users.push({
          admno: raw.admno as string,
          name: raw.name as string,
          status: resolveStatus(raw.status),
          requestedAt: (raw.requestedAt as string) || obj.uploaded.toISOString(),
          ...(raw.approvedAt ? { approvedAt: raw.approvedAt as string } : {}),
          ...(raw.specialAccessRequestedAt ? { specialAccessRequestedAt: raw.specialAccessRequestedAt as string } : {}),
          ...(raw.revokedAt ? { revokedAt: raw.revokedAt as string } : {}),
          ...(raw.bannedAt ? { bannedAt: raw.bannedAt as string } : {}),
          ...(typeof raw.referralsLeft === "number" ? { referralsLeft: raw.referralsLeft as number } : {}),
          ...(raw.referredBy ? { referredBy: raw.referredBy as string } : {}),
        });
      } catch {
        // skip corrupt entries
      }
    }

    cursor = listed.truncated ? listed.cursor : undefined;
  } while (cursor);

  return users;
}

async function getUserRecord(env: Env, admno: string): Promise<UserRecord | null> {
  const object = await env.APK_BUCKET.get(`users/${admno.toLowerCase()}.json`);
  if (!object) return null;
  const raw = (await object.json()) as Record<string, unknown>;
  if (!raw.admno || !raw.name) return null;
  return {
    admno: raw.admno as string,
    name: raw.name as string,
    status: resolveStatus(raw.status),
    requestedAt: (raw.requestedAt as string) || object.uploaded.toISOString(),
    ...(raw.approvedAt ? { approvedAt: raw.approvedAt as string } : {}),
    ...(raw.specialAccessRequestedAt ? { specialAccessRequestedAt: raw.specialAccessRequestedAt as string } : {}),
    ...(raw.revokedAt ? { revokedAt: raw.revokedAt as string } : {}),
    ...(raw.bannedAt ? { bannedAt: raw.bannedAt as string } : {}),
    ...(typeof raw.referralsLeft === "number" ? { referralsLeft: raw.referralsLeft as number } : {}),
    ...(raw.referredBy ? { referredBy: raw.referredBy as string } : {}),
  };
}

async function putUserRecord(env: Env, record: UserRecord): Promise<void> {
  const clean: Record<string, string | number> = {
    admno: record.admno,
    name: record.name,
    status: record.status,
    requestedAt: record.requestedAt,
  };
  if (record.approvedAt) clean.approvedAt = record.approvedAt;
  if (record.specialAccessRequestedAt) clean.specialAccessRequestedAt = record.specialAccessRequestedAt;
  if (record.revokedAt) clean.revokedAt = record.revokedAt;
  if (record.bannedAt) clean.bannedAt = record.bannedAt;
  if (typeof record.referralsLeft === "number") clean.referralsLeft = record.referralsLeft;
  if (record.referredBy) clean.referredBy = record.referredBy;

  await env.APK_BUCKET.put(`users/${record.admno.toLowerCase()}.json`, JSON.stringify(clean), {
    httpMetadata: { contentType: "application/json" },
  });
}

async function updateCheck(request: Request, env: Env): Promise<Response> {
  requireAppSecret(request, env);

  const body = await readJson<{ versionCode?: number; versionName?: string; platform?: string; channel?: string }>(request);
  if (body.platform && body.platform !== "android") {
    return json({ error: "Unsupported platform" }, 400);
  }

  const channel = normalizeChannel(body.channel ?? defaultChannel(env));
  const manifest = await getManifest(env, channel);
  const baseUrl = publicBaseUrl(request, env);

  const reauthObj = await env.APK_BUCKET.get("config/force-reauth.json");
  const forceReauth = reauthObj !== null;

  return json({
    latestVersionCode: manifest.latestVersionCode,
    latestVersionName: manifest.latestVersionName,
    minSupportedVersionCode: manifest.minSupportedVersionCode,
    apkUrl: `${baseUrl}/download/android/${encodeURIComponent(channel)}`,
    sha256: manifest.sha256,
    sizeBytes: manifest.sizeBytes,
    releaseNotes: manifest.releaseNotes ?? "",
    publishedAt: manifest.publishedAt,
    forceReauth,
  });
}

async function registerUser(request: Request, env: Env): Promise<Response> {
  requireAppSecret(request, env);

  const body = await readJson<{ admno?: string; name?: string }>(request);
  const admno = body.admno?.trim();
  const name = body.name?.trim();
  if (!admno || !name) {
    return json({ error: "admno and name are required" }, 400);
  }

  const existing = await getUserRecord(env, admno);
  const now = new Date().toISOString();

  if (existing?.status === "banned") {
    return json({ error: "Account suspended" }, 403);
  }

  const record: UserRecord = existing
    ? { ...existing, name: existing.name || name }
    : { admno, name, status: "pending", requestedAt: now, referralsLeft: 5 };

  await putUserRecord(env, record);
  return json({ ok: true });
}

async function accessStatus(request: Request, env: Env): Promise<Response> {
  requireAppSecret(request, env);

  const body = await readJson<{ admno?: string; name?: string; forceReRegister?: boolean }>(request);
  const admno = body.admno?.trim();
  const name = body.name?.trim();
  if (!admno || !name) {
    return json({ error: "admno and name are required" }, 400);
  }

  const existing = await getUserRecord(env, admno);

  if (existing?.status === "banned") {
    return json({
      status: "banned",
      message: "Your account has been suspended.",
      banned: true,
    });
  }

  if (body.forceReRegister) {
    if (existing?.status === "approved") {
      const updated: UserRecord = {
        ...existing,
        name: existing.name || name,
      };
      await putUserRecord(env, updated);
      return json({ status: "approved", message: "Access approved" });
    }

    const record: UserRecord = {
      admno,
      name,
      status: "pending",
      requestedAt: new Date().toISOString(),
      referralsLeft: 5,
    };
    await putUserRecord(env, record);
    return json({ status: "pending", message: "Authorization in process" });
  }

  if (existing) {
    if (existing.revokedAt) {
      return json({
        status: "pending",
        message: "Access revoked. Please sign in again.",
        requiresReauth: true,
      });
    }

    if (existing.status === "pending" && existing.requestedAt) {
      const elapsed = Date.now() - new Date(existing.requestedAt).getTime();
      const fortyEightHours = 48 * 60 * 60 * 1000;
      if (elapsed >= fortyEightHours) {
        const autoApproved: UserRecord = {
          admno: existing.admno,
          name: existing.name || name,
          status: "approved",
          requestedAt: existing.requestedAt,
          approvedAt: new Date().toISOString(),
          ...(typeof existing.referralsLeft === "number" ? { referralsLeft: existing.referralsLeft } : {}),
          ...(existing.referredBy ? { referredBy: existing.referredBy } : {}),
        };
        await putUserRecord(env, autoApproved);
        return json({ status: "approved", message: "Access approved" });
      }
    }

    const updated: UserRecord = {
      ...existing,
      name: existing.name || name,
    };
    await putUserRecord(env, updated);
    return json({
      status: existing.status,
      message: existing.status === "approved" ? "Access approved" : "Authorization in process",
    });
  }

  const record: UserRecord = {
    admno,
    name,
    status: "pending",
    requestedAt: new Date().toISOString(),
    referralsLeft: 5,
  };
  await putUserRecord(env, record);
  return json({ status: "pending", message: "Authorization in process" });
}

async function requestSpecialAccess(request: Request, env: Env): Promise<Response> {
  requireAppSecret(request, env);

  const body = await readJson<{ admno?: string; name?: string; referralName?: string }>(request);
  const admno = body.admno?.trim();
  const name = body.name?.trim();
  const referralName = body.referralName?.trim();
  if (!admno || !name) {
    return json({ error: "admno and name are required" }, 400);
  }
  if (!referralName) {
    return json({ error: "Referral name is required" }, 400);
  }

  const allUsers = await getAllUserRecords(env);
  const referrer = allUsers.find((u) => u.name.toLowerCase() === referralName.toLowerCase() && u.status === "approved");
  if (!referrer) {
    return json({ ok: false, message: "No approved CampusCue user was found with that referral name." });
  }
  if (referrer.admno.toLowerCase() === admno.toLowerCase()) {
    return json({ ok: false, message: "You cannot refer yourself." });
  }

  const referrerSlots = typeof referrer.referralsLeft === "number" ? referrer.referralsLeft : 5;
  if (referrerSlots <= 0) {
    return json({ ok: false, message: "This person has no referral slots left." });
  }

  const now = new Date().toISOString();
  const existing = await getUserRecord(env, admno);

  if (existing?.referredBy) {
    return json({ ok: false, message: "You have already submitted a referral request." });
  }

  const record: UserRecord = {
    admno: existing?.admno ?? admno,
    name: existing?.name || name,
    status: "pending",
    requestedAt: existing?.requestedAt ?? now,
    specialAccessRequestedAt: now,
    referredBy: referrer.name,
    referralsLeft: typeof existing?.referralsLeft === "number" ? existing.referralsLeft : 5,
  };
  await putUserRecord(env, record);

  const updatedReferrer: UserRecord = {
    ...referrer,
    referralsLeft: referrerSlots - 1,
  };
  await putUserRecord(env, updatedReferrer);

  return json({ ok: true, message: "Special access requested via " + referrer.name + ". It will appear in the admin dashboard." });
}

async function listUsers(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const users = await getAllUserRecords(env);
  users.sort((a, b) => a.admno.localeCompare(b.admno));

  return corsJson({
    count: users.length,
    approved: users.filter((u) => u.status === "approved").length,
    pending: users.filter((u) => u.status === "pending").length,
    banned: users.filter((u) => u.status === "banned").length,
    specialAccess: users.filter((u) => u.specialAccessRequestedAt).length,
    users,
  });
}

async function listPendingUsers(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const all = await getAllUserRecords(env);
  const users = all
    .filter((u) => u.status === "pending" && !u.specialAccessRequestedAt)
    .sort((a, b) => a.admno.localeCompare(b.admno));

  return corsJson({ count: users.length, users });
}

async function listSpecialAccessUsers(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const all = await getAllUserRecords(env);
  const users = all
    .filter((u) => u.status === "pending" && u.specialAccessRequestedAt)
    .sort((a, b) => (b.specialAccessRequestedAt ?? "").localeCompare(a.specialAccessRequestedAt ?? ""));

  return corsJson({ count: users.length, users });
}

async function approveUser(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const body = await readJson<{ admno?: string }>(request);
  const admno = body.admno?.trim();
  if (!admno) return corsJson({ error: "admno is required" }, 400);

  const existing = await getUserRecord(env, admno);
  if (!existing) return corsJson({ error: "User not found" }, 404);

  const record: UserRecord = {
    admno: existing.admno,
    name: existing.name,
    status: "approved",
    requestedAt: existing.requestedAt,
    approvedAt: new Date().toISOString(),
    ...(existing.specialAccessRequestedAt ? { specialAccessRequestedAt: existing.specialAccessRequestedAt } : {}),
    ...(typeof existing.referralsLeft === "number" ? { referralsLeft: existing.referralsLeft } : {}),
    ...(existing.referredBy ? { referredBy: existing.referredBy } : {}),
  };
  await putUserRecord(env, record);
  return corsJson({ ok: true, admno: record.admno, name: record.name, status: record.status });
}

async function createDummyUser(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const body = await readJson<{ admno?: string; name?: string; referralsLeft?: number }>(request);
  const admno = body.admno?.trim() || "DUMMY-REFERRAL";
  const name = body.name?.trim() || "CampusCue Referral";
  const referralsLeft =
    typeof body.referralsLeft === "number" && body.referralsLeft >= 0
      ? Math.floor(body.referralsLeft)
      : 50;
  const now = new Date().toISOString();

  const record: UserRecord = {
    admno,
    name,
    status: "approved",
    requestedAt: now,
    approvedAt: now,
    referralsLeft,
  };
  await putUserRecord(env, record);
  return corsJson({ ok: true, admno: record.admno, name: record.name, status: record.status, referralsLeft });
}

async function revokeUser(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const body = await readJson<{ admno?: string }>(request);
  const admno = body.admno?.trim();
  if (!admno) return corsJson({ error: "admno is required" }, 400);

  const existing = await getUserRecord(env, admno);
  if (!existing) return corsJson({ error: "User not found" }, 404);

  const record: UserRecord = {
    admno: existing.admno,
    name: existing.name,
    status: "pending",
    requestedAt: new Date().toISOString(),
    revokedAt: new Date().toISOString(),
    referralsLeft: 5,
  };
  await putUserRecord(env, record);
  return corsJson({ ok: true, admno: record.admno, name: record.name, status: record.status });
}

async function revokeAllUsers(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const all = await getAllUserRecords(env);

  const now = new Date().toISOString();
  for (const user of all) {
    if (user.status === "banned") continue;
    const record: UserRecord = {
      admno: user.admno,
      name: user.name,
      status: "pending",
      requestedAt: now,
      revokedAt: now,
      referralsLeft: 5,
    };
    await putUserRecord(env, record);
  }

  return corsJson({ ok: true, revoked: all.filter((u) => u.status !== "banned").length, total: all.length });
}

async function banUser(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const body = await readJson<{ admno?: string }>(request);
  const admno = body.admno?.trim();
  if (!admno) return corsJson({ error: "admno is required" }, 400);

  const existing = await getUserRecord(env, admno);
  if (!existing) return corsJson({ error: "User not found" }, 404);

  const record: UserRecord = {
    ...existing,
    status: "banned",
    bannedAt: new Date().toISOString(),
  };
  await putUserRecord(env, record);
  return corsJson({ ok: true, admno: record.admno, name: record.name, status: "banned" });
}

async function unbanUser(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const body = await readJson<{ admno?: string }>(request);
  const admno = body.admno?.trim();
  if (!admno) return corsJson({ error: "admno is required" }, 400);

  const existing = await getUserRecord(env, admno);
  if (!existing) return corsJson({ error: "User not found" }, 404);

  const record: UserRecord = {
    admno: existing.admno,
    name: existing.name,
    status: "pending",
    requestedAt: new Date().toISOString(),
    referralsLeft: typeof existing.referralsLeft === "number" ? existing.referralsLeft : 5,
  };
  await putUserRecord(env, record);
  return corsJson({ ok: true, admno: record.admno, name: record.name, status: "pending" });
}

async function setReferrals(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const body = await readJson<{ admno?: string; referralsLeft?: number }>(request);
  const admno = body.admno?.trim();
  if (!admno) return corsJson({ error: "admno is required" }, 400);
  if (typeof body.referralsLeft !== "number" || body.referralsLeft < 0) {
    return corsJson({ error: "referralsLeft must be a non-negative number" }, 400);
  }

  const existing = await getUserRecord(env, admno);
  if (!existing) return corsJson({ error: "User not found" }, 404);

  const updated: UserRecord = { ...existing, referralsLeft: body.referralsLeft };
  await putUserRecord(env, updated);
  return corsJson({ ok: true, admno: updated.admno, name: updated.name, referralsLeft: updated.referralsLeft });
}

async function forceReauth(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  const payload = JSON.stringify({ forcedAt: new Date().toISOString() });
  await env.APK_BUCKET.put("config/force-reauth.json", payload, {
    httpMetadata: { contentType: "application/json" },
  });
  return corsJson({ ok: true, message: "Force reauth enabled. All users will be asked to sign out." });
}

async function clearReauth(request: Request, env: Env): Promise<Response> {
  requireAdminSecret(request, env);

  await env.APK_BUCKET.delete("config/force-reauth.json");
  return corsJson({ ok: true, message: "Force reauth cleared." });
}

async function adminDashboard(env: Env): Promise<Response> {
  return new Response(ADMIN_HTML, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

async function installPage(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const channel = normalizeChannel(url.searchParams.get("channel") ?? defaultChannel(env));
  const baseUrl = publicBaseUrl(request, env);

  let manifest: ChannelManifest | null = null;
  try {
    manifest = await getManifest(env, channel);
  } catch {
    // The page should still explain what is missing after a fresh Worker deploy.
  }

  const title = "CampusCue";
  const version = manifest ? `v${escapeHtml(manifest.latestVersionName)}` : "No release uploaded yet";
  const notes = manifest?.releaseNotes ? escapeHtml(manifest.releaseNotes) : "Private Galgotias University student build.";
  const downloadUrl = `${baseUrl}/download/android/${encodeURIComponent(channel)}`;

  return new Response(
    `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title} install</title>
  <style>
    :root { color-scheme: light dark; font-family: Inter, Roboto, Arial, sans-serif; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f7f9fc; color: #101828; }
    main { width: min(420px, calc(100vw - 32px)); padding: 28px; border: 1px solid #d9e2ef; border-radius: 22px; background: #fff; box-shadow: 0 18px 50px rgba(16, 24, 40, .08); }
    .logo { width: 64px; height: 64px; display: grid; place-items: center; border-radius: 18px; background: #2563eb; color: white; font-weight: 800; font-size: 20px; }
    h1 { margin: 18px 0 4px; font-size: 30px; letter-spacing: -.02em; }
    p { line-height: 1.5; color: #667085; }
    a.button { display: block; margin: 22px 0 18px; padding: 15px 18px; border-radius: 14px; background: #2563eb; color: white; text-align: center; text-decoration: none; font-weight: 700; }
    ol { padding-left: 20px; color: #475467; }
    li { margin: 8px 0; }
    code { padding: 2px 6px; border-radius: 7px; background: #eef4ff; color: #1d4ed8; }
    @media (prefers-color-scheme: dark) {
      body { background: #0b0f17; color: #e6edf6; }
      main { background: #141a24; border-color: #263244; box-shadow: none; }
      p, ol { color: #9aa6b8; }
      code { background: #1d2a44; color: #8ab4ff; }
    }
  </style>
</head>
<body>
  <main>
    <div class="logo">CC</div>
    <h1>${title}</h1>
    <p>${version} · ${notes}</p>
    ${manifest ? `<a class="button" href="${downloadUrl}">Download APK</a>` : "<p><strong>Upload a release first.</strong></p>"}
    <ol>
      <li>Tap <strong>Download APK</strong>.</li>
      <li>Open the downloaded file.</li>
      <li>If Android asks, allow installs from your browser once.</li>
      <li>Future updates will appear inside CampusCue.</li>
    </ol>
    <p>Channel: <code>${escapeHtml(channel)}</code></p>
  </main>
</body>
</html>`,
    {
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
      },
    },
  );
}

async function downloadApk(channelInput: string, env: Env, includeBody: boolean): Promise<Response> {
  const channel = normalizeChannel(channelInput);
  const manifest = await getManifest(env, channel);
  const object = await env.APK_BUCKET.get(manifest.apkObjectKey);
  if (!object) {
    return json({ error: "APK object missing" }, 404);
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Content-Type", APK_MIME);
  headers.set("Content-Length", manifest.sizeBytes.toString());
  headers.set("Content-Disposition", `attachment; filename="CampusCue-${manifest.latestVersionName}.apk"`);
  headers.set("Cache-Control", "public, max-age=300");
  headers.set("X-CampusCue-Sha256", manifest.sha256);
  return new Response(includeBody ? object.body : null, { headers });
}

async function getManifest(env: Env, channelInput: string): Promise<ChannelManifest> {
  const channel = normalizeChannel(channelInput);
  const object = await env.APK_BUCKET.get(`updates/${channel}.json`);
  if (!object) {
    throw new Response("Manifest not found", { status: 404 });
  }
  const manifest = (await object.json()) as ChannelManifest;
  validateManifest(manifest, channel);
  return manifest;
}

function validateManifest(manifest: ChannelManifest, channel: string): void {
  if (
    manifest.platform !== "android" ||
    manifest.channel !== channel ||
    !Number.isInteger(manifest.latestVersionCode) ||
    manifest.latestVersionCode <= 0 ||
    !manifest.latestVersionName ||
    !Number.isInteger(manifest.minSupportedVersionCode) ||
    !manifest.apkObjectKey ||
    !/^[a-f0-9]{64}$/i.test(manifest.sha256) ||
    !Number.isInteger(manifest.sizeBytes) ||
    manifest.sizeBytes <= 0 ||
    !manifest.publishedAt
  ) {
    throw new Response("Manifest is invalid", { status: 500 });
  }
}

async function readJson<T>(request: Request): Promise<T> {
  try {
    return (await request.json()) as T;
  } catch {
    throw new Response("Invalid JSON", { status: 400 });
  }
}

function defaultChannel(env: Env): string {
  return normalizeChannel(env.DEFAULT_CHANNEL || "release");
}

function normalizeChannel(channel: string): string {
  const normalized = channel.trim().toLowerCase();
  if (!/^[a-z0-9-]{1,32}$/.test(normalized)) {
    throw new Response("Invalid channel", { status: 400 });
  }
  return normalized;
}

function publicBaseUrl(request: Request, env: Env): string {
  const configured = env.PUBLIC_BASE_URL?.trim().replace(/\/+$/, "");
  return configured || new URL(request.url).origin;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

function corsJson(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "Access-Control-Allow-Origin": "*",
    },
  });
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => {
    const entities: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    };
    return entities[char];
  });
}

const ADMIN_HTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CampusCue Admin</title>
<style>
*{box-sizing:border-box;margin:0}
:root{--bg:#0b0f17;--card:#141a24;--border:#1e293b;--text:#e2e8f0;--muted:#94a3b8;--primary:#3b82f6;--green:#22c55e;--amber:#f59e0b;--red:#ef4444;--radius:10px;font-family:Inter,-apple-system,system-ui,sans-serif}
body{background:var(--bg);color:var(--text);min-height:100vh;padding:20px}
.container{max-width:900px;margin:0 auto}
h1{font-size:22px;font-weight:700;margin-bottom:4px}
.sub{color:var(--muted);font-size:13px;margin-bottom:20px}
.login{max-width:360px;margin:120px auto;text-align:center}
.login input{width:100%;padding:12px 16px;border:1px solid var(--border);border-radius:var(--radius);background:var(--card);color:var(--text);font-size:14px;margin:12px 0}
.login button{width:100%;padding:12px;border:none;border-radius:var(--radius);background:var(--primary);color:#fff;font-weight:600;cursor:pointer;font-size:14px}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:10px;margin-bottom:20px}
.stat{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:14px 16px;text-align:center}
.stat .n{font-size:28px;font-weight:700}
.stat .l{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px;margin-top:2px}
.tabs{display:flex;gap:4px;margin-bottom:16px;border-bottom:1px solid var(--border);padding-bottom:0}
.tab{padding:8px 16px;font-size:13px;font-weight:500;color:var(--muted);cursor:pointer;border:none;background:none;border-bottom:2px solid transparent;margin-bottom:-1px}
.tab.active{color:var(--primary);border-bottom-color:var(--primary)}
.toolbar{display:flex;gap:8px;margin-bottom:14px;flex-wrap:wrap;align-items:center}
.toolbar button{padding:6px 14px;border:1px solid var(--border);border-radius:var(--radius);background:var(--card);color:var(--text);font-size:12px;cursor:pointer;font-weight:500}
.toolbar button:hover{border-color:var(--primary)}
.toolbar button.danger{border-color:var(--red);color:var(--red)}
.link-box{padding:8px 14px;background:var(--card);border:1px solid var(--border);border-radius:var(--radius);font-size:12px;color:var(--primary);word-break:break-all;cursor:pointer;flex:1;min-width:200px}
table{width:100%;border-collapse:collapse;font-size:13px}
th{text-align:left;padding:8px 12px;color:var(--muted);font-weight:500;font-size:11px;text-transform:uppercase;letter-spacing:.5px;border-bottom:1px solid var(--border)}
td{padding:10px 12px;border-bottom:1px solid var(--border)}
tr:hover td{background:rgba(59,130,246,.04)}
.badge{display:inline-block;padding:2px 8px;border-radius:20px;font-size:11px;font-weight:600}
.badge.approved{background:rgba(34,197,94,.12);color:var(--green)}
.badge.pending{background:rgba(245,158,11,.12);color:var(--amber)}
.badge.banned{background:rgba(239,68,68,.12);color:var(--red)}
.btn-sm{padding:4px 10px;border:1px solid var(--border);border-radius:6px;background:var(--card);color:var(--text);font-size:11px;cursor:pointer;font-weight:500}
.btn-sm:hover{border-color:var(--primary)}
.btn-sm.red{color:var(--red);border-color:rgba(239,68,68,.3)}
.btn-sm.red:hover{border-color:var(--red)}
.btn-sm.green{color:var(--green);border-color:rgba(34,197,94,.3)}
.btn-sm.green:hover{border-color:var(--green)}
.actions{display:flex;gap:4px;flex-wrap:wrap}
.empty{text-align:center;color:var(--muted);padding:40px;font-size:14px}
.toast{position:fixed;bottom:20px;right:20px;padding:12px 20px;border-radius:var(--radius);background:var(--green);color:#fff;font-size:13px;font-weight:500;opacity:0;transition:opacity .2s;pointer-events:none;z-index:99}
.toast.show{opacity:1}
.toast.error{background:var(--red)}
.modal-bg{position:fixed;inset:0;background:rgba(0,0,0,.6);display:none;place-items:center;z-index:50}
.modal-bg.show{display:grid}
.modal{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:24px;width:min(380px,calc(100vw - 40px))}
.modal h3{font-size:16px;margin-bottom:12px}
.modal input{width:100%;padding:10px 12px;border:1px solid var(--border);border-radius:8px;background:var(--bg);color:var(--text);font-size:13px;margin-bottom:10px}
.modal .btns{display:flex;gap:8px;justify-content:flex-end}
.modal .btns button{padding:8px 18px;border:none;border-radius:8px;font-size:13px;font-weight:600;cursor:pointer}
.modal .btns .cancel{background:var(--bg);color:var(--muted);border:1px solid var(--border)}
.modal .btns .confirm{background:var(--primary);color:#fff}
@media(max-width:600px){.stats{grid-template-columns:repeat(2,1fr)}.toolbar{flex-direction:column}.link-box{width:100%}}
</style>
</head>
<body>
<div id="app"></div>
<div class="toast" id="toast"></div>
<div class="modal-bg" id="modal-bg"><div class="modal" id="modal"></div></div>
<script>
const BASE=location.origin;
let SECRET='';
let users=[];
let tab='pending';

function $(s){return document.querySelector(s)}
function $$(s){return document.querySelectorAll(s)}

function init(){
  SECRET=localStorage.getItem('cc_admin_secret')||'';
  SECRET?renderDashboard():renderLogin();
}

function renderLogin(){
  $('#app').innerHTML=\`<div class="login">
    <h1>CampusCue Admin</h1>
    <p class="sub">Enter your admin secret</p>
    <input type="password" id="secret-input" placeholder="Admin Secret" autofocus>
    <button onclick="doLogin()">Sign In</button>
  </div>\`;
  $('#secret-input').addEventListener('keydown',e=>{if(e.key==='Enter')doLogin()});
}

async function doLogin(){
  SECRET=$('#secret-input').value.trim();
  if(!SECRET)return;
  try{
    const r=await api('GET','/admin/users');
    if(r.error){SECRET='';toast('Invalid secret','error');return}
    localStorage.setItem('cc_admin_secret',SECRET);
    users=r.users||[];
    renderDashboard();
  }catch{SECRET='';toast('Connection failed','error')}
}

function renderDashboard(){
  const approved=users.filter(u=>u.status==='approved');
  const pending=users.filter(u=>u.status==='pending'&&!u.specialAccessRequestedAt);
  const special=users.filter(u=>u.status==='pending'&&u.specialAccessRequestedAt);
  const banned=users.filter(u=>u.status==='banned');

  $('#app').innerHTML=\`
    <h1>CampusCue Admin</h1>
    <p class="sub">User management dashboard</p>
    <div class="stats">
      <div class="stat"><div class="n">\${users.length}</div><div class="l">Total</div></div>
      <div class="stat"><div class="n" style="color:var(--green)">\${approved.length}</div><div class="l">Approved</div></div>
      <div class="stat"><div class="n" style="color:var(--amber)">\${pending.length+special.length}</div><div class="l">Pending</div></div>
      <div class="stat"><div class="n" style="color:var(--red)">\${banned.length}</div><div class="l">Banned</div></div>
    </div>
    <div class="tabs">
      <button class="tab \${tab==='pending'?'active':''}" onclick="switchTab('pending')">Pending (\${pending.length})</button>
      <button class="tab \${tab==='special'?'active':''}" onclick="switchTab('special')">Referrals (\${special.length})</button>
      <button class="tab \${tab==='approved'?'active':''}" onclick="switchTab('approved')">Approved (\${approved.length})</button>
      <button class="tab \${tab==='banned'?'active':''}" onclick="switchTab('banned')">Banned (\${banned.length})</button>
      <button class="tab \${tab==='all'?'active':''}" onclick="switchTab('all')">All (\${users.length})</button>
    </div>
    <div class="toolbar">
      <button onclick="refresh()">Refresh</button>
      <button onclick="copyLink()">Copy Install Link</button>
      <button class="danger" onclick="doLogout()">Sign Out</button>
    </div>
    <div id="table-area"></div>
  \`;
  renderTable();
}

function switchTab(t){tab=t;renderDashboard()}

function renderTable(){
  const approved=users.filter(u=>u.status==='approved');
  const pending=users.filter(u=>u.status==='pending'&&!u.specialAccessRequestedAt);
  const special=users.filter(u=>u.status==='pending'&&u.specialAccessRequestedAt);
  const banned=users.filter(u=>u.status==='banned');

  let list;
  if(tab==='pending')list=pending;
  else if(tab==='special')list=special;
  else if(tab==='approved')list=approved;
  else if(tab==='banned')list=banned;
  else list=[...users];

  if(!list.length){$('#table-area').innerHTML='<div class="empty">No users in this category</div>';return}

  let html='<table><thead><tr><th>Admno</th><th>Name</th><th>Status</th>';
  if(tab==='special')html+='<th>Referred By</th>';
  if(tab==='approved')html+='<th>Referrals</th>';
  html+='<th>Actions</th></tr></thead><tbody>';

  for(const u of list){
    html+=\`<tr><td>\${esc(u.admno)}</td><td>\${esc(u.name)}</td>
      <td><span class="badge \${u.status}">\${u.status}</span></td>\`;
    if(tab==='special')html+=\`<td>\${esc(u.referredBy||'-')}</td>\`;
    if(tab==='approved')html+=\`<td>\${u.referralsLeft??'-'}</td>\`;
    html+='<td><div class="actions">';
    if(u.status==='pending')html+=\`<button class="btn-sm green" onclick="approve('\${esc(u.admno)}')">Approve</button>\`;
    if(u.status==='approved')html+=\`<button class="btn-sm" onclick="showReferralModal('\${esc(u.admno)}',\${u.referralsLeft??5})">Referrals</button>\`;
    if(u.status!=='banned'){
      html+=\`<button class="btn-sm" onclick="revoke('\${esc(u.admno)}')">Revoke</button>\`;
      html+=\`<button class="btn-sm red" onclick="ban('\${esc(u.admno)}')">Ban</button>\`;
    }else{
      html+=\`<button class="btn-sm green" onclick="unban('\${esc(u.admno)}')">Unban</button>\`;
    }
    html+='</div></td></tr>';
  }
  html+='</tbody></table>';
  $('#table-area').innerHTML=html;
}

async function approve(admno){
  const r=await api('POST','/admin/approve',{admno});
  if(r.ok){toast('Approved '+admno);await refresh()}else toast(r.error||'Failed','error');
}
async function revoke(admno){
  if(!confirm('Revoke '+admno+'? They will need to re-verify OTP.'))return;
  const r=await api('POST','/admin/revoke',{admno});
  if(r.ok){toast('Revoked '+admno);await refresh()}else toast(r.error||'Failed','error');
}
async function ban(admno){
  if(!confirm('Ban '+admno+'? They will be locked out.'))return;
  const r=await api('POST','/admin/ban',{admno});
  if(r.ok){toast('Banned '+admno);await refresh()}else toast(r.error||'Failed','error');
}
async function unban(admno){
  const r=await api('POST','/admin/unban',{admno});
  if(r.ok){toast('Unbanned '+admno);await refresh()}else toast(r.error||'Failed','error');
}

function showReferralModal(admno,current){
  $('#modal').innerHTML=\`<h3>Set Referrals for \${esc(admno)}</h3>
    <input type="number" id="ref-count" value="\${current}" min="0" max="999">
    <div class="btns">
      <button class="cancel" onclick="closeModal()">Cancel</button>
      <button class="confirm" onclick="setReferrals('\${esc(admno)}')">Save</button>
    </div>\`;
  $('#modal-bg').classList.add('show');
}

async function setReferrals(admno){
  const v=parseInt($('#ref-count').value);
  if(isNaN(v)||v<0)return;
  closeModal();
  const r=await api('POST','/admin/referrals',{admno,referralsLeft:v});
  if(r.ok){toast('Referrals updated');await refresh()}else toast(r.error||'Failed','error');
}

function closeModal(){$('#modal-bg').classList.remove('show')}
$('#modal-bg').addEventListener('click',e=>{if(e.target===$('#modal-bg'))closeModal()});

function copyLink(){
  navigator.clipboard.writeText(BASE+'/install');
  toast('Install link copied!');
}

async function refresh(){
  try{
    const r=await api('GET','/admin/users');
    users=r.users||[];
    renderDashboard();
  }catch{toast('Refresh failed','error')}
}

function doLogout(){localStorage.removeItem('cc_admin_secret');SECRET='';renderLogin()}

async function api(method,path,body){
  const opts={method,headers:{'X-Admin-Secret':SECRET}};
  if(body){opts.headers['Content-Type']='application/json';opts.body=JSON.stringify(body)}
  const r=await fetch(BASE+path,opts);
  return r.json();
}

function toast(msg,type){
  const t=$('#toast');
  t.textContent=msg;
  t.className='toast show'+(type==='error'?' error':'');
  setTimeout(()=>t.className='toast',2500);
}

function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;')}

init();
</script>
</body>
</html>`;
