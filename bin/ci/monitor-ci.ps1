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
    Triggers CI on GitHub via tag push and monitors the workflow until completion.

.DESCRIPTION
    1. Calls trigger-ci.ps1 to create and push a pre-release CI tag.
    2. Captures the tag name and locates the triggered workflow run.
    3. Streams the workflow logs in real time.
    4. Reports the final conclusion (success/failure) and exits with the same code.

    Requires: gh CLI authenticated with the repo, and pwsh (PowerShell Core).

.PARAMETER PreReleaseVersion
    The pre-release label to use (default: "ci").  Passed through to trigger-ci.ps1.

.PARAMETER remote
    The git remote to push the tag to (default: "origin").

.PARAMETER PollIntervalSeconds
    How many seconds to wait between polls while the workflow is queued (default: 5).

.PARAMETER NoWatch
    Skip interactive `gh run watch` and poll with `gh run list` / `gh run view` instead.
    Useful on CI or non-interactive terminals.

.EXAMPLE
    .\bin\ci\monitor-ci.ps1
    .\bin\ci\monitor-ci.ps1 -NoWatch
    .\bin\ci\monitor-ci.ps1 -PreReleaseVersion rc -PollIntervalSeconds 10
#>

param(
    [string]$PreReleaseVersion = "ci",
    [string]$remote = "origin",
    [int]$PollIntervalSeconds = 5,
    [switch]$NoWatch
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

# ── 1. Trigger CI ──────────────────────────────────────────────────

$triggerScript = Join-Path $repoRoot "bin\ci\trigger-ci.ps1"
if (-not (Test-Path $triggerScript)) {
    Write-Error "trigger-ci.ps1 not found at $triggerScript"
    exit 1
}

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  Step 1/3: Triggering CI via tag push" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

$tagOutput = & $triggerScript -PreReleaseVersion $PreReleaseVersion -remote $remote 2>&1
$exitCode = $LASTEXITCODE

# trigger-ci.ps1 uses Write-Output for the tag, so grab the last line that looks like a tag
$tagLines = $tagOutput | Where-Object { $_ -match '^v\d+\.\d+\.\d+-.*$' }
$tag = $tagLines | Select-Object -Last 1

if ($exitCode -ne 0 -or -not $tag) {
    Write-Error "trigger-ci.ps1 failed (exit code: $exitCode). Output:`n$($tagOutput -join "`n")"
    exit 1
}

Write-Host "Tag pushed: $tag" -ForegroundColor Green

# ── 2. Locate the workflow run ─────────────────────────────────────

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  Step 2/3: Waiting for workflow run to appear" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

# CI workflow filename (the one triggered by ci tags)
$workflowFile = "ci.yml"
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
        [string]$WorkflowFile
    )
    # gh run list returns newest first; filter by the workflow file and the tag
    $runs = Invoke-GhJson -GhArgs @(
        "run", "list", "--workflow", "$WorkflowFile",
        "--json", "databaseId,headBranch,status,conclusion,url", "--limit", "5"
    )

    if (-not $runs) { return $null }

    # The tag should appear as the headBranch
    $match = $runs | Where-Object { $_.headBranch -eq $Tag } | Select-Object -First 1
    return $match
}

$run = $null
do {
    $run = Find-RunByTag -Tag $tag -WorkflowFile $workflowFile
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
Write-Host "  Step 3/3: Monitoring workflow" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

if ($NoWatch) {
    # Non-interactive poll loop
    Write-Host "Polling every ${PollIntervalSeconds}s (non-interactive mode) ..."
    $done = $false
    $consecutiveFailures = 0
    $maxConsecutiveFailures = 12
    do {
        Start-Sleep -Seconds $PollIntervalSeconds
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

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor $(if ($finalConclusion -eq "success") { "Green" } else { "Red" })
Write-Host "  CI workflow finished: $finalConclusion" -ForegroundColor $(if ($finalConclusion -eq "success") { "Green" } else { "Red" })
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor $(if ($finalConclusion -eq "success") { "Green" } else { "Red" })

# Show the full run summary (jobs, durations) with a single call
Write-Host "`nJob summary:"
gh run view $run.databaseId --json jobs --jq '.jobs[] | "  \(.name)  →  \(.conclusion)  (\(.startedAt) … \(.completedAt))"' 2>$null
if ($LASTEXITCODE -ne 0) {
    # fallback for older gh without --jq
    gh run view $run.databaseId --json jobs 2>$null
}

# Exit with the matching code
if ($finalConclusion -eq "success") {
    exit 0
} else {
    exit 1
}

