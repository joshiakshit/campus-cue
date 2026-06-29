param(
    [ValidateSet("Dashboard", "ForceReauth", "ClearReauth", "CreateDummyUser")]
    [string]$Action = "Dashboard",
    [string]$ConfigPath = (Join-Path (Split-Path $PSScriptRoot -Parent) "local.properties")
)

$ErrorActionPreference = "Stop"

function Read-CampusCueConfig {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Config file not found: $Path"
    }

    $vars = @{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        if ($_ -match '^([^#][^=]+)=(.*)$') {
            $vars[$matches[1].Trim()] = $matches[2].Trim()
        }
    }

    if ([string]::IsNullOrWhiteSpace($vars["WORKER_URL"])) {
        throw "WORKER_URL is missing from $Path"
    }

    if ([string]::IsNullOrWhiteSpace($vars["ADMIN_SECRET"]) -and [string]::IsNullOrWhiteSpace($vars["APP_SECRET"])) {
        throw "ADMIN_SECRET or APP_SECRET is missing from $Path"
    }

    $adminSecret = $vars["ADMIN_SECRET"]
    if ([string]::IsNullOrWhiteSpace($adminSecret)) {
        $adminSecret = $vars["APP_SECRET"]
    }

    $appSecret = $vars["APP_SECRET"]
    if ([string]::IsNullOrWhiteSpace($appSecret)) {
        $appSecret = $adminSecret
    }

    return @{
        WorkerUrl = $vars["WORKER_URL"].TrimEnd("/")
        AdminHeaders = @{ "X-Admin-Secret" = $adminSecret }
        AppHeaders = @{ "X-App-Secret" = $appSecret }
    }
}

function Show-ApiErrorBody {
    param([Parameter(Mandatory = $true)]$ErrorRecord)

    $response = $ErrorRecord.Exception.Response
    if ($null -eq $response) {
        return
    }

    try {
        $stream = $response.GetResponseStream()
        if ($null -eq $stream) {
            return
        }

        $body = [System.IO.StreamReader]::new($stream).ReadToEnd()
        if (-not [string]::IsNullOrWhiteSpace($body)) {
            Write-Host "Server response: $body" -ForegroundColor DarkYellow
        }
    } catch {
        # Keep the original web exception visible.
    }
}

function Invoke-CampusCueApi {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [ValidateSet("GET", "POST")][string]$Method = "GET",
        [hashtable]$Body = @{},
        [switch]$UseAppSecret
    )

    $headers = if ($UseAppSecret) { $script:Config.AppHeaders } else { $script:Config.AdminHeaders }
    $uri = "$($script:Config.WorkerUrl)$Path"

    try {
        if ($Method -eq "POST") {
            $postHeaders = @{}
            foreach ($key in $headers.Keys) {
                $postHeaders[$key] = $headers[$key]
            }
            $postHeaders["Content-Type"] = "application/json"

            return Invoke-RestMethod `
                -Uri $uri `
                -Method Post `
                -Headers $postHeaders `
                -Body ($Body | ConvertTo-Json -Depth 6)
        }

        return Invoke-RestMethod -Uri $uri -Method Get -Headers $headers
    } catch {
        Show-ApiErrorBody -ErrorRecord $_
        throw
    }
}

function Read-RequiredInput {
    param([string]$Prompt)

    $value = Read-Host $Prompt
    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-Host "No value entered." -ForegroundColor Yellow
        return $null
    }
    return $value.Trim()
}

function Pause-Dashboard {
    Write-Host ""
    Read-Host "Press Enter to continue" | Out-Null
}

function Show-UsersTable {
    param(
        [Parameter(Mandatory = $true)]$Users,
        [string]$EmptyMessage = "No users found."
    )

    if ($null -eq $Users -or $Users.Count -eq 0) {
        Write-Host $EmptyMessage -ForegroundColor DarkGray
        return
    }

    @($Users) |
        Select-Object `
            @{ Name = "AdmNo"; Expression = { $_.admno } },
            @{ Name = "Name"; Expression = { $_.name } },
            @{ Name = "Status"; Expression = { $_.status } },
            @{ Name = "Refs"; Expression = { $_.referralsLeft } },
            @{ Name = "ReferredBy"; Expression = { $_.referredBy } },
            @{ Name = "Revoked"; Expression = { if ($_.revokedAt) { "yes" } else { "" } } },
            @{ Name = "Special"; Expression = { if ($_.specialAccessRequestedAt) { "yes" } else { "" } } } |
        Format-Table -AutoSize |
        Out-Host
}

function Get-CampusCueSnapshot {
    return @{
        All = Invoke-CampusCueApi -Path "/admin/users"
        Special = Invoke-CampusCueApi -Path "/admin/special"
    }
}

function Show-UserList {
    param(
        [ValidateSet("All", "Pending", "Special")]
        [string]$List
    )

    Clear-Host
    switch ($List) {
        "All" {
            Write-Host "All registered users" -ForegroundColor Cyan
            $result = Invoke-CampusCueApi -Path "/admin/users"
            Write-Host ("Count: {0}   Approved: {1}   Pending: {2}" -f $result.count, $result.approved, $result.pending) -ForegroundColor DarkGray
            Show-UsersTable -Users $result.users -EmptyMessage "No registered users."
        }
        "Pending" {
            Write-Host "Pending users" -ForegroundColor Cyan
            $result = Invoke-CampusCueApi -Path "/admin/pending"
            Write-Host ("Count: {0}" -f $result.count) -ForegroundColor DarkGray
            Show-UsersTable -Users $result.users -EmptyMessage "No pending users."
        }
        "Special" {
            Write-Host "Special access requests" -ForegroundColor Cyan
            $result = Invoke-CampusCueApi -Path "/admin/special"
            Write-Host ("Count: {0}" -f $result.count) -ForegroundColor DarkGray
            Show-UsersTable -Users $result.users -EmptyMessage "No special access requests."
        }
    }
}

function Approve-AdmissionNumber {
    $pending = Invoke-CampusCueApi -Path "/admin/pending"
    if ($null -ne $pending.users -and $pending.users.Count -gt 0) {
        Write-Host ""
        Write-Host "Pending users" -ForegroundColor Cyan
        Show-UsersTable -Users $pending.users -EmptyMessage "No pending users."
        Write-Host ""
    } else {
        Write-Host "No pending users are waiting right now." -ForegroundColor DarkGray
    }

    $admno = Read-RequiredInput "Admission number to approve"
    if ($null -eq $admno) {
        return
    }

    $result = Invoke-CampusCueApi -Path "/admin/approve" -Method "POST" -Body @{ admno = $admno }
    Write-Host ("Approved {0} - {1}" -f $result.admno, $result.name) -ForegroundColor Green
}

function Revoke-AdmissionNumber {
    $admno = Read-RequiredInput "Admission number to revoke"
    if ($null -eq $admno) {
        return
    }

    $result = Invoke-CampusCueApi -Path "/admin/revoke" -Method "POST" -Body @{ admno = $admno }
    Write-Host ("Revoked {0} - {1}. Re-auth required on next session check." -f $result.admno, $result.name) -ForegroundColor Red
}

function Revoke-AllUsers {
    $confirm = Read-Host "Type YES to revoke ALL users and require re-auth"
    if ($confirm -ne "YES") {
        Write-Host "Cancelled." -ForegroundColor Yellow
        return
    }

    $result = Invoke-CampusCueApi -Path "/admin/revoke-all" -Method "POST"
    Write-Host ("Revoked {0} of {1} user(s). Re-auth required on next session check." -f $result.revoked, $result.total) -ForegroundColor Red
}

function Set-Referrals {
    $admno = Read-RequiredInput "Admission number"
    if ($null -eq $admno) {
        return
    }

    $countText = Read-RequiredInput "New referral count"
    if ($null -eq $countText) {
        return
    }

    $count = 0
    if (-not [int]::TryParse($countText, [ref]$count) -or $count -lt 0) {
        Write-Host "Invalid count. Must be a non-negative integer." -ForegroundColor Yellow
        return
    }

    $result = Invoke-CampusCueApi `
        -Path "/admin/referrals" `
        -Method "POST" `
        -Body @{ admno = $admno; referralsLeft = $count }

    Write-Host ("Set referrals for {0} ({1}) to {2}" -f $result.admno, $result.name, $result.referralsLeft) -ForegroundColor Green
}

function Create-DummyUser {
    $name = Read-Host "Dummy referral name [CampusCue Referral]"
    if ([string]::IsNullOrWhiteSpace($name)) {
        $name = "CampusCue Referral"
    }

    $admno = Read-Host "Dummy admission number [DUMMY-REFERRAL]"
    if ([string]::IsNullOrWhiteSpace($admno)) {
        $admno = "DUMMY-REFERRAL"
    }

    $referralsText = Read-Host "Referral slots [50]"
    $referrals = 50
    if (-not [string]::IsNullOrWhiteSpace($referralsText) -and
        (-not [int]::TryParse($referralsText, [ref]$referrals) -or $referrals -lt 0)) {
        Write-Host "Invalid referral count. Must be a non-negative integer." -ForegroundColor Yellow
        return
    }

    $result = Invoke-CampusCueApi `
        -Path "/admin/dummy-user" `
        -Method "POST" `
        -Body @{ admno = $admno.Trim(); name = $name.Trim(); referralsLeft = $referrals }

    Write-Host ("Created approved dummy user: {0} ({1}), referrals: {2}" -f $result.name, $result.admno, $result.referralsLeft) -ForegroundColor Green
    Write-Host ("Use referral name exactly: {0}" -f $result.name) -ForegroundColor Cyan
}

function Invoke-ForceReauth {
    $confirm = Read-Host "Type FORCE to log out every user and require re-authentication"
    if ($confirm -ne "FORCE") {
        Write-Host "Cancelled." -ForegroundColor Yellow
        return
    }

    $result = Invoke-CampusCueApi -Path "/admin/force-reauth" -Method "POST"
    Write-Host "Force re-auth is now active. Users will need to log in again." -ForegroundColor Red
    if ($null -ne $result) {
        $result | Format-List
    }
}

function Clear-ForceReauth {
    $result = Invoke-CampusCueApi -Path "/admin/clear-reauth" -Method "POST"
    Write-Host "Force re-auth flag cleared. Users can enter normally after signing in." -ForegroundColor Green
    if ($null -ne $result) {
        $result | Format-List
    }
}

function Print-InstallLink {
    Write-Host "$($script:Config.WorkerUrl)/install" -ForegroundColor Cyan
}

function Show-Dashboard {
    Clear-Host
    Write-Host "CampusCue Admin" -ForegroundColor Cyan
    Write-Host "Worker: $($script:Config.WorkerUrl)" -ForegroundColor DarkGray
    Write-Host ""

    try {
        $snapshot = Get-CampusCueSnapshot
        Write-Host ("Users: {0}   Approved: {1}   Pending: {2}   Special access: {3}" -f `
            $snapshot.All.count, `
            $snapshot.All.approved, `
            $snapshot.All.pending, `
            $snapshot.All.specialAccess) -ForegroundColor Green
        Write-Host ""

        Write-Host "Special access requests" -ForegroundColor Yellow
        Show-UsersTable -Users $snapshot.Special.users -EmptyMessage "No special access requests."
    } catch {
        Write-Host "Could not load dashboard: $($_.Exception.Message)" -ForegroundColor Red
    }

    Write-Host ""
    Write-Host "Users"
    Write-Host "  1. Refresh"
    Write-Host "  2. Approve request"
    Write-Host "  3. Revoke user + require re-auth"
    Write-Host "  4. Revoke all users + require re-auth"
    Write-Host "  5. Change referrals"
    Write-Host "  6. Create approved dummy referral user"
    Write-Host ""
    Write-Host "Lists"
    Write-Host "  7. Special requests"
    Write-Host "  8. Pending users"
    Write-Host "  9. All users"
    Write-Host ""
    Write-Host "System"
    Write-Host "  10. Print install link"
    Write-Host "  11. Force re-auth"
    Write-Host "  12. Clear force re-auth"
    Write-Host "  Q. Quit"
    Write-Host ""
}

function Invoke-MenuAction {
    param([string]$Choice)

    switch ($Choice.Trim().ToUpperInvariant()) {
        "1" { return "Refresh" }
        "2" { Approve-AdmissionNumber }
        "3" { Revoke-AdmissionNumber }
        "4" { Revoke-AllUsers }
        "5" { Set-Referrals }
        "6" { Create-DummyUser }
        "7" { Show-UserList -List "Special" }
        "8" { Show-UserList -List "Pending" }
        "9" { Show-UserList -List "All" }
        "10" { Print-InstallLink }
        "11" { Invoke-ForceReauth }
        "12" { Clear-ForceReauth }
        "Q" { return "Quit" }
        default { Write-Host "Unknown option." -ForegroundColor Yellow }
    }

    return "Pause"
}

$script:Config = Read-CampusCueConfig -Path $ConfigPath

switch ($Action) {
    "ForceReauth" {
        Invoke-ForceReauth
        return
    }
    "ClearReauth" {
        Clear-ForceReauth
        return
    }
    "CreateDummyUser" {
        Create-DummyUser
        return
    }
}

while ($true) {
    Show-Dashboard
    $result = "Pause"

    try {
        $result = Invoke-MenuAction -Choice (Read-Host "Select an option")
    } catch {
        Write-Host "Action failed: $($_.Exception.Message)" -ForegroundColor Red
    }

    if ($result -eq "Quit") {
        break
    }
    if ($result -ne "Refresh") {
        Pause-Dashboard
    }
}
