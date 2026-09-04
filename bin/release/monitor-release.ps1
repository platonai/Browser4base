#!/usr/bin/env pwsh

# ═══════════════════════════════════════════════════════════════════
# CROSS-PLATFORM: This script must run on Linux, macOS, and Windows.
# - Use $IsWindows / $IsLinux / $IsMacOS for platform detection.
# - Use "($IsWindows -or $env:OS -eq 'Windows_NT')" for PS 5.1 compat.
# - Windows-only env vars ($env:TEMP) need $env:TMPDIR fallback.
# - Guard "chcp" and other Windows-only commands behind platform checks.
# ═══════════════════════════════════════════════════════════════════

<#
.SYNOPSIS
    Triggers a release on GitHub via tag push and monitors the workflow until completion.

.DESCRIPTION
    0. Preflights the release version (Step 0) before anything is pushed: the version in
       VERSION is checked against the latest GitHub release and Maven Central. Releasing a
       version that is already on Maven Central, already consumed by a GitHub release, or
       older than the latest release (leapfrog) aborts immediately — Central never accepts
       the same version twice, so such a release can only fail during deploy. Overwriting a
       tag whose GitHub release exists (but whose artifacts never reached Central, i.e. an
       earlier run failed during deploy) requires an explicit -Force.
    1. Calls trigger-release.ps1 to create and push a release tag (interactive — you will
       be prompted for confirmations, just as with trigger-release.ps1 directly).
    2. Captures the tag name and locates the triggered Release workflow run (matched by tag
       AND commit SHA, so a stale run from an earlier attempt at the same tag is never
       reported as this release's run).
    3. Streams the workflow logs in real time.
    4. Reports the final conclusion (success/failure) and exits with the same code.

    Requires: gh CLI authenticated with the repo, and pwsh (PowerShell Core).

.PARAMETER remote
    The git remote to push the tag to (default: "origin").
    Passed through to trigger-release.ps1.

.PARAMETER message
    Release message for an annotated tag.  Passed through to trigger-release.ps1.
    If omitted, trigger-release.ps1 will prompt for one.

.PARAMETER PollIntervalSeconds
    How many seconds to wait between polls while the workflow is queued (default: 5).

.PARAMETER NoWatch
    Skip interactive `gh run watch` and poll with `gh run list` / `gh run view` instead.
    Useful on CI or non-interactive terminals.

.PARAMETER Yes
    Skip all interactive confirmation prompts in trigger-release.ps1 (non-interactive and
    agent use). Does NOT bypass the version preflight.

.PARAMETER Force
    Allow re-releasing a version whose tag already has a GitHub release. Only meaningful
    when that release's artifacts never reached Maven Central (the earlier run failed
    during deploy): the tag is overwritten and release.yml re-triggers. Without -Force the
    preflight refuses to touch any version already on Central or already released.

.PARAMETER MaxMonitorMinutes
    Upper bound (in minutes) for the -NoWatch monitor loop. Default 0 = no limit. Use this
    to guarantee the script eventually exits if a workflow run gets stuck (e.g. a job
    queued forever). Ignored in interactive (-NoWatch off) mode.

.EXAMPLE
    .\bin\release\monitor-release.ps1

    .\bin\release\monitor-release.ps1 -message "Hotfix for login crash"

    .\bin\release\monitor-release.ps1 -NoWatch -PollIntervalSeconds 10

    .\bin\release\monitor-release.ps1 -NoWatch -Yes -MaxMonitorMinutes 90

    .\bin\release\monitor-release.ps1 -NoWatch -Yes -Force   # re-run a failed release
#>

param(
    [string]$remote = "origin",
    [string]$message = "",
    [int]$PollIntervalSeconds = 5,
    [switch]$NoWatch,
    [switch]$Yes,
    [switch]$Force,
    [int]$MaxMonitorMinutes = 0
)

$ErrorActionPreference = "Stop"

# gh emits UTF-8. On Windows with a legacy console codepage (e.g. cp936),
# PowerShell decodes native output with [Console]::OutputEncoding and any
# multi-byte character (e.g. the "…" GitHub appends to truncated titles)
# can swallow an adjacent ASCII quote, structurally corrupting the JSON
# before ConvertFrom-Json ever sees it — deterministically, so retries
# cannot help. Force UTF-8 decoding instead.
try {
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
} catch {
    # Non-interactive or redirected hosts may refuse; proceed regardless.
}

$repoRoot = (git rev-parse --show-toplevel 2>$null)
if (-not $repoRoot) {
    Write-Error "Not inside a git repository."
    exit 1
}
Set-Location $repoRoot

# ── 0. Preflight: validate the release version ─────────────────────
# Guard rails for "the release must use the correct version" — the
# shared logic lives in bin/common/ReleaseVersion.ps1 (also used by
# trigger-release.ps1). Maven Central rejects a re-publish of the same
# version, so an already published, already released or leapfrogged
# version can only produce a failed release run — fail fast here
# instead of after a tag push.

. (Join-Path $repoRoot "bin\common\ReleaseVersion.ps1")

gh --version > $null 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "gh CLI is required for the release version preflight and workflow monitoring."
    exit 1
}

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  Step 0/4: Validating release version" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

$versionFile = Join-Path $repoRoot "VERSION"
$version = Get-ReleaseVersionFromFile -VersionFile $versionFile
if (-not $version) {
    Write-Error "VERSION does not contain a valid version (expected X.Y.Z or X.Y.Z-SNAPSHOT): '$(Get-Content $versionFile -TotalCount 1)'"
    exit 1
}
$newTag = "v$version"
$headSha = (git rev-parse HEAD).Trim()
Write-Host "  Version:     $version"
Write-Host "  Release tag: $newTag"
Write-Host "  Tag commit:  $headSha"
Write-Host ""

if (-not (Assert-ReleaseVersionPublishable -Version $version -Force:$Force)) {
    exit 1
}
Write-Host ""

# ── 1. Trigger release ─────────────────────────────────────────────

$triggerScript = Join-Path $repoRoot "bin\release\trigger-release.ps1"
if (-not (Test-Path $triggerScript)) {
    Write-Error "trigger-release.ps1 not found at $triggerScript"
    exit 1
}

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  Step 1/4: Triggering release via tag push" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

# Build args for trigger-release.ps1 — passthrough of remote and message
$triggerArgs = @{}
if ($remote)      { $triggerArgs['remote'] = $remote }
if ($message)     { $triggerArgs['message'] = $message }
if ($Yes)         { $triggerArgs['Yes'] = $true }

# Capture all output streams so we can extract the tag
$tagOutput = & $triggerScript @triggerArgs 2>&1
$exitCode = $LASTEXITCODE

# trigger-release.ps1 uses Write-Output for the tag on its last line.
# Tags look like: v4.12.0, v4.12.0-rc.1, v4.12.0-alpha.1, etc.
$tagLines = $tagOutput | Where-Object { $_ -match '^v\d+\.\d+\.\d+(-(rc|alpha|beta|dry_run)\.\d+)?$' }
$tag = $tagLines | Select-Object -Last 1

if ($exitCode -ne 0 -or -not $tag) {
    Write-Error "trigger-release.ps1 failed (exit code: $exitCode). Output:`n$($tagOutput -join "`n")"
    exit 1
}

Write-Host "Tag pushed: $tag" -ForegroundColor Green

# ── 2. Locate the workflow run ─────────────────────────────────────

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  Step 2/4: Waiting for workflow run to appear" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

$workflowFile = "release.yml"
$maxWaitSeconds = 120
$elapsed = 0

# --- Helper: run gh and parse JSON defensively -----------------------------
# ConvertFrom-Json throws a terminating error under $ErrorActionPreference
# = "Stop"; transient failures (truncated/garbled output, network hiccups)
# would kill the whole monitor. Retry a few times, then give up gracefully.
function Invoke-GhJson {
    param(
        [string[]]$GhArgs,
        [int]$Attempts = 3,
        [int]$RetryDelaySeconds = 3
    )
    for ($i = 1; $i -le $Attempts; $i++) {
        try {
            $raw = & gh @GhArgs 2>$null
            if ($LASTEXITCODE -ne 0) { throw "gh exited with code $LASTEXITCODE" }
            return (($raw -join "`n") | ConvertFrom-Json)
        } catch {
            if ($i -ge $Attempts) {
                Write-Warning "gh $($GhArgs -join ' ') failed after ${Attempts} attempts: $($_.Exception.Message)"
                return $null
            }
            Start-Sleep -Seconds $RetryDelaySeconds
        }
    }
}

# --- Helper: resolve a run ID from a tag by polling gh run list ----------
function Find-RunByTag {
    param(
        [string]$Tag,
        [string]$WorkflowFile,
        [string]$HeadSha
    )
    # gh run list returns newest first; filter by the workflow file, the tag
    # and the commit the tag points at. Matching the SHA guarantees a stale
    # run from an earlier attempt at the same tag (e.g. a re-released or
    # re-run version) is never reported as this release's run.
    $runs = Invoke-GhJson -GhArgs @(
        "run", "list", "--workflow", "$WorkflowFile",
        "--json", "databaseId,headBranch,headSha,status,conclusion,url", "--limit", "20"
    )

    if (-not $runs) { return $null }

    $candidates = @($runs | Where-Object { $_.headBranch -eq $Tag })
    if ($HeadSha) {
        $candidates = @($candidates | Where-Object { $_.headSha -eq $HeadSha })
    }
    if ($candidates.Count -eq 0) { return $null }
    return $candidates | Select-Object -First 1
}

$run = $null
do {
    $run = Find-RunByTag -Tag $tag -WorkflowFile $workflowFile -HeadSha $headSha
    if ($run) { break }

    Write-Host "  Run not yet visible (${elapsed}s elapsed) — retrying in ${PollIntervalSeconds}s ..."
    Start-Sleep -Seconds $PollIntervalSeconds
    $elapsed += $PollIntervalSeconds
} while ($elapsed -lt $maxWaitSeconds)

if (-not $run) {
    Write-Error "Workflow run for tag '$tag' did not appear within ${maxWaitSeconds}s.`n" +
                "Check manually: gh run list --workflow '$workflowFile'"
    exit 1
}

Write-Host "Workflow run found:" -ForegroundColor Green
Write-Host "  ID:     $($run.databaseId)"
Write-Host "  URL:    $($run.url)"
Write-Host "  Status: $($run.status)"

# ── 3. Monitor until completion ────────────────────────────────────

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  Step 3/4: Monitoring workflow" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

if ($NoWatch) {
    # Non-interactive poll loop
    Write-Host "Polling every ${PollIntervalSeconds}s (non-interactive mode) ..."
    $done = $false
    $consecutiveFailures = 0
    $maxConsecutiveFailures = 12
    $monitorStarted = Get-Date
    do {
        Start-Sleep -Seconds $PollIntervalSeconds

        if ($MaxMonitorMinutes -gt 0) {
            $elapsedMin = ((Get-Date) - $monitorStarted).TotalMinutes
            if ($elapsedMin -ge $MaxMonitorMinutes) {
                Write-Host "  ERROR: monitor exceeded -MaxMonitorMinutes ($MaxMonitorMinutes min) — aborting." -ForegroundColor Red
                exit 1
            }
        }
        $info = Invoke-GhJson -GhArgs @(
            "run", "view", "$($run.databaseId)", "--json", "status,conclusion"
        )
        if (-not $info) {
            # Transient query failure — keep polling, but bail out eventually.
            $consecutiveFailures++
            if ($consecutiveFailures -ge $maxConsecutiveFailures) {
                Write-Error "Failed to query run status ${consecutiveFailures} times in a row; giving up."
                exit 1
            }
            Write-Host "  [$((Get-Date).ToString('HH:mm:ss'))] Failed to query run status — retrying ..."
            continue
        }
        $consecutiveFailures = 0
        $statusLine = "  [$((Get-Date).ToString('HH:mm:ss'))] Status: $($info.status)"
        if ($info.conclusion) { $statusLine += " | Conclusion: $($info.conclusion)" }
        Write-Host $statusLine
        if ($info.status -eq "completed") {
            $done = $true
            $finalConclusion = $info.conclusion
        }
    } while (-not $done)
} else {
    # Interactive: stream logs in real time
    gh run watch $run.databaseId

    # After watch returns, get the final conclusion
    $info = Invoke-GhJson -GhArgs @(
        "run", "view", "$($run.databaseId)", "--json", "status,conclusion"
    )
    if (-not $info) {
        Write-Warning "Could not read the final run conclusion; treating as failure."
        $finalConclusion = "unknown"
    } else {
        $finalConclusion = $info.conclusion
    }
}

# ── 4. Report ─────────────────────────────────────────────────────

$color = if ($finalConclusion -eq "success") { "Green" } else { "Red" }
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor $color
Write-Host "  Release workflow finished: $finalConclusion" -ForegroundColor $color
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor $color

# Show the full run summary (jobs, durations)
Write-Host "`nJob summary:"
gh run view $run.databaseId --json jobs --jq '.jobs[] | "  \(.name)  →  \(.conclusion)  (\(.startedAt) … \(.completedAt))"' 2>$null
if ($LASTEXITCODE -ne 0) {
    # fallback for older gh without --jq
    gh run view $run.databaseId --json jobs 2>$null
}

if ($finalConclusion -eq "success") {
    exit 0
} else {
    exit 1
}
