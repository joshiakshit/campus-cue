#!/usr/bin/env node
import { createHash } from "node:crypto";
import { readFileSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

const args = parseArgs(process.argv.slice(2));

const apkPath = required(args.apk, "--apk");
const versionCode = parsePositiveInt(required(args["version-code"], "--version-code"));
const versionName = required(args["version-name"], "--version-name");
const channel = normalizeChannel(args.channel || "release");
const bucket = args.bucket || "campuscue-apks";
const minSupportedVersionCode = parseNonNegativeInt(args["min-supported-version-code"] || "0");
const notes = args["notes-file"] ? readFileSync(args["notes-file"], "utf8").trim() : args["release-notes"] || "";
const remote = args.remote !== "false";

const apkStats = statSync(apkPath);
const sha256 = sha256File(apkPath);
const apkObjectKey = `apks/android/${channel}/campuscue-${versionName}-${versionCode}.apk`;
const manifestObjectKey = `updates/${channel}.json`;

const manifest = {
  platform: "android",
  channel,
  latestVersionCode: versionCode,
  latestVersionName: versionName,
  minSupportedVersionCode,
  apkObjectKey,
  sha256,
  sizeBytes: apkStats.size,
  releaseNotes: notes,
  publishedAt: new Date().toISOString(),
};

const manifestPath = path.join(tmpdir(), `campuscue-update-${channel}-${versionCode}.json`);
writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);

wrangler(["r2", "object", "put", `${bucket}/${apkObjectKey}`, "--file", apkPath]);
wrangler(["r2", "object", "put", `${bucket}/${manifestObjectKey}`, "--file", manifestPath]);

console.log("");
console.log("CampusCue update published");
console.log(`Channel: ${channel}`);
console.log(`Version: ${versionName} (${versionCode})`);
console.log(`SHA-256: ${sha256}`);
console.log("Install page: https://<your-worker-url>/install");
console.log("Set WORKER_URL in local.properties to your deployed Worker URL.");

function wrangler(commandArgs) {
  const args = ["wrangler", ...commandArgs, ...(remote ? ["--remote"] : [])];
  const bin = process.platform === "win32" ? process.env.ComSpec || "cmd.exe" : "npx";
  const finalArgs = process.platform === "win32" ? ["/d", "/s", "/c", "npx", ...args] : args;
  const result = spawnSync(bin, finalArgs, { stdio: "inherit" });
  if (result.error) {
    console.error(result.error.message);
    process.exit(1);
  }
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function sha256File(filePath) {
  return createHash("sha256").update(readFileSync(filePath)).digest("hex");
}

function parseArgs(argv) {
  const parsed = {};
  for (let i = 0; i < argv.length; i += 1) {
    const raw = argv[i];
    if (!raw.startsWith("--")) continue;
    const eq = raw.indexOf("=");
    if (eq > -1) {
      parsed[raw.slice(2, eq)] = raw.slice(eq + 1);
    } else {
      parsed[raw.slice(2)] = argv[i + 1] && !argv[i + 1].startsWith("--") ? argv[++i] : "true";
    }
  }
  return parsed;
}

function required(value, name) {
  if (!value) {
    console.error(`Missing ${name}`);
    process.exit(1);
  }
  return value;
}

function parsePositiveInt(value) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    console.error("Expected a positive integer");
    process.exit(1);
  }
  return parsed;
}

function parseNonNegativeInt(value) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed < 0) {
    console.error("Expected a non-negative integer");
    process.exit(1);
  }
  return parsed;
}

function normalizeChannel(value) {
  const normalized = value.trim().toLowerCase();
  if (!/^[a-z0-9-]{1,32}$/.test(normalized)) {
    console.error("Channel must contain only lowercase letters, numbers, or hyphens.");
    process.exit(1);
  }
  return normalized;
}
