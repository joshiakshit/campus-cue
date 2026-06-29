<#
.SYNOPSIS
  One-command CampusCue release: verify -> build signed APK -> check signature
  -> publish to the Cloudflare Worker -> verify the live update endpoint.

.DESCRIPTION
  Reads versionCode / versionName straight from app/build.gradle.kts so the
  published metadata can never drift from the APK. Bump the version there first,
  then run this script. Fails fast on any step.

.PARAMETER ReleaseNotes
  Short human-readable notes shown in the in-app updater. Required.

.PARAMETER MinSupported
  minSupportedVersionCode. Builds older than this are force-updated. Defaults to
  39 (the current floor — keep it unless you intend a forced update).

.PARAMETER Channel
  Release channel. Defaults to "release".

.EXAMPLE
  ./scripts/release.ps1 -ReleaseNotes "Removed GPA planner; cleanup pass."
#>
param(
    [Parameter(Mandatory = $true)] [string]$ReleaseNotes,
    [int]$MinSupported = 39,
    [string]$Channel = "release"
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$ExpectedCertSha256 = "40c1fbc301830f20cbbf782aaed926101304c2d5708d48d4ff16998070e3fffb"

function Fail($msg) { Write-Error "RELEASE ABORTED: $msg"; exit 1 }

# --- 1. Version from build.gradle.kts (single source of truth) ---
$gradle = Get-Content "app/build.gradle.kts" -Raw
$versionCode = ([regex]::Match($gradle, 'versionCode\s*=\s*(\d+)')).Groups[1].Value
$versionName = ([regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"')).Groups[1].Value
if (-not $versionCode -or -not $versionName) { Fail "Could not parse versionCode/versionName from app/build.gradle.kts" }
Write-Host "==> Releasing CampusCue $versionName ($versionCode), channel=$Channel, minSupported=$MinSupported" -ForegroundColor Cyan

# --- 2. Secrets from local.properties (for post-publish verification) ---
if (-not (Test-Path "local.properties")) { Fail "local.properties not found" }
$props = @{}
Get-Content "local.properties" | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $props[$matches[1].Trim()] = $matches[2].Trim() }
}
$appSecret = $props["APP_SECRET"]
$workerUrl = ($props["WORKER_URL"]).TrimEnd("/")
if (-not $appSecret) { Fail "APP_SECRET missing from local.properties" }
if (-not $workerUrl) { Fail "WORKER_URL missing from local.properties" }

# --- 3. Verify + build signed release APK ---
Write-Host "==> Gradle: ktlintCheck detekt test assembleRelease" -ForegroundColor Cyan
& ".\gradlew.bat" ktlintCheck detekt test assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { Fail "Gradle verify/build failed" }

$apk = "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $apk)) { Fail "Signed APK not found at $apk" }

# --- 4. Signature check (assert the long-term release cert) ---
$buildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { Fail "Android build-tools not found" }
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$certOut = & $apksigner verify --print-certs $apk 2>$null
$digest = ($certOut | Select-String "certificate SHA-256 digest:" | Select-Object -First 1) -replace '.*:\s*', ''
$digest = $digest.Trim().ToLower()
if ($digest -ne $ExpectedCertSha256) {
    Fail "APK signed with UNEXPECTED cert ($digest). Expected the CampusCue release key ($ExpectedCertSha256). Check RELEASE_STORE_FILE in local.properties."
}
Write-Host "==> Signature OK (CampusCue release cert)" -ForegroundColor Green

# --- 5. Publish to the Worker (uploads APK + manifest to R2) ---
Write-Host "==> Publishing to Worker via worker/scripts/publish-update.mjs" -ForegroundColor Cyan
Push-Location worker
& npm run publish:update -- --apk "..\$($apk -replace '/','\')" --version-code $versionCode --version-name $versionName --min-supported-version-code $MinSupported --channel $Channel --release-notes $ReleaseNotes
$pubExit = $LASTEXITCODE
Pop-Location
if ($pubExit -ne 0) { Fail "Worker publish failed" }

# --- 6. Verify the live update endpoint matches what we built ---
Write-Host "==> Verifying $workerUrl/update/check" -ForegroundColor Cyan
$body = @{ platform = "android"; channel = $Channel; versionCode = 1; versionName = "0.0.0" } | ConvertTo-Json
$resp = Invoke-RestMethod -Uri "$workerUrl/update/check" -Method Post -Headers @{ "X-App-Secret" = $appSecret } -ContentType "application/json" -Body $body
if ([string]$resp.latestVersionCode -ne $versionCode) { Fail "Endpoint latestVersionCode=$($resp.latestVersionCode), expected $versionCode" }

$localHash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$tmp = Join-Path $env:TEMP "campuscue-release-verify.apk"
Invoke-WebRequest -Uri $resp.apkUrl -OutFile $tmp
$remoteHash = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
if ($localHash -ne $remoteHash) { Fail "Published APK hash ($remoteHash) != local build ($localHash)" }
if ($resp.sha256.ToLower() -ne $localHash) { Fail "Manifest sha256 ($($resp.sha256)) != local build ($localHash)" }

Write-Host ""
Write-Host "RELEASE COMPLETE: CampusCue $versionName ($versionCode)" -ForegroundColor Green
Write-Host "  SHA-256:  $localHash"
Write-Host "  Endpoint: $workerUrl/update/check -> $($resp.latestVersionName) ($($resp.latestVersionCode))"
Write-Host "  Install:  $workerUrl/install"
