#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Checks if the current project version is the next version to publish.

.DESCRIPTION
    A pre-release gate that verifies the given version is the correct next version
    to publish.  Three conditions must be satisfied:
      1. The version is NOT already the latest GitHub release.
      2. The pulsar-bom artifact is NOT already on Maven Central.
      3. The latest published version is EARLIER than this version (no leapfrog).

    Exits with code 0 if the version is ready to publish, non-zero otherwise.

.PARAMETER Version
    The version to check (without the -SNAPSHOT suffix).
    If omitted, reads the version from the VERSION file in the repo root.

.EXAMPLE
    .\check-publish-status.ps1
    Checks the version from the VERSION file.

.EXAMPLE
    .\check-publish-status.ps1 -Version 4.9.5
    Checks whether v4.9.5 is the next version to publish.

.EXAMPLE
    .\check-publish-status.ps1 -Version 4.9.5 -Verbose
    Checks v4.9.5 with detailed diagnostic output.
#>
[CmdletBinding()]
param (
    [Parameter(HelpMessage = "The version to check (without -SNAPSHOT)")]
    [string]$Version
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------
# Utility: retry a network call on transient failures
# ---------------------------------------------------------------
function Invoke-WithRetry {
    param(
        [ScriptBlock]$ScriptBlock,
        [string]$Description = "request",
        [int]$MaxAttempts = 3
    )
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $result = & $ScriptBlock
            return @{ Ok = $true; Result = $result }
        } catch {
            # If the server sent back an HTTP response (even 4xx/5xx) the error is
            # definitive — don't retry.  Retry only when no response was received
            # (DNS failure, connection refused, timeout, etc.).
            $hadResponse = $null -ne $_.Exception.Response
            if (-not $hadResponse -and $attempt -lt $MaxAttempts) {
                Write-Verbose "  $Description (attempt $attempt/$MaxAttempts) — transient failure, retrying..."
                Start-Sleep -Seconds 2
                continue
            }
            return @{ Ok = $false; Error = $_ }
        }
    }
}

# ---------------------------------------------------------------
# Determine repo root
# ---------------------------------------------------------------
$repoRoot = (git rev-parse --show-toplevel 2>$null)
if ($null -eq $repoRoot) {
    Write-Error "Could not determine project root. Are you in a git repository?"
    exit 2
}
Set-Location $repoRoot

# Import common utility (consistent with other release scripts)
$utilScript = Join-Path $repoRoot "bin\common\Util.ps1"
if (Test-Path $utilScript) {
    . $utilScript
    Fix-Encoding-UTF8 | Out-Null
}

# ---------------------------------------------------------------
# Resolve the version
# ---------------------------------------------------------------
if (-not $Version) {
    if (-not (Test-Path "$repoRoot\VERSION")) {
        Write-Error "VERSION file not found at $repoRoot\VERSION and no -Version argument was provided."
        exit 2
    }
    $snapshotVersion = (Get-Content "$repoRoot\VERSION" -TotalCount 1).Trim()
    $Version = $snapshotVersion -replace "-SNAPSHOT", ""
}

# Validate version format
if ($Version -notmatch "^\d+\.\d+\.\d+") {
    Write-Error "Version '$Version' does not match the expected format X.Y.Z"
    exit 2
}

Write-Host "Checking pre-release status for version: v$Version"
Write-Host ""

# ---------------------------------------------------------------
# Derive GitHub repository from the git remote
# ---------------------------------------------------------------
$remoteUrl = git config --get remote.origin.url
if ($remoteUrl -notmatch 'github\.com[:/](.+?)(?:\.git)?$') {
    Write-Error "Could not determine GitHub repository from remote URL: $remoteUrl"
    exit 2
}
$githubRepo = $matches[1]
Write-Host "GitHub repository : $githubRepo"

# Maven Central coordinates for pulsar-bom
$mavenGroupId = "ai.platon.pulsar"
$mavenArtifactId = "pulsar-bom"
$mavenPath = $mavenGroupId -replace '\.', '/'
$mavenUrl = "https://repo1.maven.org/maven2/$mavenPath/$mavenArtifactId/$Version/$mavenArtifactId-$Version.pom"

Write-Host "Maven Central URL : $mavenUrl"
Write-Host ""

# Parse the target version for precedence comparison
# Strip leading 'v' and optional -rc.N suffix for [version] parsing
$cleanVersion = $Version -replace '^v', ''
if ($cleanVersion -match '^(\d+\.\d+\.\d+)(?:-rc\.\d+)?$') {
    $targetVersionObj = [version]$matches[1]
} else {
    $targetVersionObj = $null
}

# ---------------------------------------------------------------
# Check 1: Is this version NOT already the latest GitHub release?
#          (it should still be unreleased)
# ---------------------------------------------------------------
Write-Host "-------------------------------------------------"
Write-Host " Check 1: Not already the latest release"
Write-Host "-------------------------------------------------"

$alreadyReleased = $false
$latestReleaseTag = $null
$latestVersionObj = $null
$leapfrogged = $false

# Prefer gh CLI if available (handles auth and rate limiting)
$ghAvailable = $null -ne (Get-Command gh -ErrorAction SilentlyContinue)
if ($ghAvailable) {
    Write-Verbose "Using gh CLI to fetch latest release..."

    try {
        $ghResult = & gh release list --repo $githubRepo --limit 1 --json tagName 2>&1
        if ($LASTEXITCODE -eq 0) {
            $latestReleaseObj = $ghResult | ConvertFrom-Json -Depth 10
            if ($latestReleaseObj -and $latestReleaseObj.Count -gt 0) {
                $latestReleaseTag = $latestReleaseObj[0].tagName
                Write-Verbose "  Latest release : $latestReleaseTag"
                Write-Verbose "  Current tag    : v$Version"

                if ($latestReleaseTag -eq "v$Version") {
                    $alreadyReleased = $true
                    Write-Host "  [XX] v$Version is already the latest GitHub release."
                } else {
                    Write-Host "  [OK] v$Version is NOT the latest release (latest is $latestReleaseTag)."
                }
            } else {
                Write-Verbose "  No releases exist in the GitHub repository yet."
                Write-Host "  [OK] No existing releases — v$Version will be the first."
            }
        } else {
            Write-Verbose "  gh CLI returned an error: $ghResult"
            Write-Verbose "  Falling back to GitHub API."
        }
    } catch {
        Write-Verbose "  gh CLI error: $_"
        Write-Verbose "  Falling back to GitHub API."
    }
}

# Fallback: use GitHub API (when gh is unavailable or failed)
if (-not $ghAvailable -or $null -eq $latestReleaseTag) {
    Write-Verbose "Using GitHub API to fetch latest release..."

    $apiUrl = "https://api.github.com/repos/$githubRepo/releases/latest"
    $apiResult = Invoke-WithRetry -ScriptBlock {
        Invoke-RestMethod -Uri $apiUrl -Method Get -Headers @{
            Accept    = "application/vnd.github+json"
            UserAgent = "check-publish-status/1.0"
        } -TimeoutSec 30
    } -Description "GitHub API latest release"

    if ($apiResult.Ok) {
        $response = $apiResult.Result
        if ($response) {
            $latestReleaseTag = $response.tag_name
            Write-Verbose "  Latest release : $latestReleaseTag"
            Write-Verbose "  Current tag    : v$Version"

            if ($latestReleaseTag -eq "v$Version") {
                $alreadyReleased = $true
                Write-Host "  [XX] v$Version is already the latest GitHub release."
            } else {
                Write-Host "  [OK] v$Version is NOT the latest release (latest is $latestReleaseTag)."
            }
        } else {
            # No releases exist — this is fine, we'll be the first
            Write-Host "  [OK] No existing releases — v$Version will be the first."
        }
    } else {
        $apiError = $apiResult.Error
        $statusCode = if ($apiError.Exception.Response) { [int]$apiError.Exception.Response.StatusCode } else { $null }

        if ($statusCode -eq 403) {
            $rateLimitRemaining = $apiError.Exception.Response.Headers["X-RateLimit-Remaining"]
            if ($rateLimitRemaining -eq "0") {
                Write-Host "  [!!] GitHub API rate limit exceeded."
                Write-Host "       Install and authenticate the gh CLI to avoid rate limits,"
                Write-Host "       or wait for the rate-limit window to reset."
            } else {
                Write-Host "  [!!] GitHub API returned 403 Forbidden."
                Write-Host "       The repository may be private or access may be restricted."
            }
        } elseif ($null -eq $statusCode) {
            Write-Host "  [!!] Could not reach GitHub API (network error)."
        } else {
            Write-Host "  [!!] GitHub API returned HTTP $statusCode."
        }
    }
}

# Second fallback: compare against latest git tag
if ($null -eq $latestReleaseTag) {
    Write-Host "  Falling back to git tag comparison..."

    $latestTag = git ls-remote --tags --sort=-version:refname origin 2>$null `
        | ForEach-Object {
            $normalised = $_ -replace '\^\{\}$', ''
            if ($normalised -match 'refs/tags/(v\d+\.\d+\.\d+)$') {
                $matches[1]
            }
        } `
        | Select-Object -First 1

    if ($latestTag) {
        $latestReleaseTag = $latestTag
        Write-Host "  Latest remote tag: $latestTag"
        Write-Host "  Current tag      : v$Version"

        if ($latestTag -eq "v$Version") {
            $alreadyReleased = $true
            Write-Host "  [XX] v$Version is already the latest release tag."
        } else {
            Write-Host "  [OK] v$Version is NOT the latest release tag (latest is $latestTag)."
        }
    } else {
        Write-Host "  [OK] No version tags found — v$Version will be the first."
    }
}

# ---------------------------------------------------------------
# Check 2: Is pulsar-bom NOT already on Maven Central?
#          (it should not be deployed yet)
# ---------------------------------------------------------------
Write-Host ""
Write-Host "-------------------------------------------------"
Write-Host " Check 2: pulsar-bom NOT on Maven Central"
Write-Host "-------------------------------------------------"

$alreadyDeployed = $false

$mavenResult = Invoke-WithRetry -ScriptBlock {
    Invoke-WebRequest -Uri $mavenUrl -Method Head -TimeoutSec 30 -UseBasicParsing
} -Description "Maven Central HEAD request"

if ($mavenResult.Ok) {
    $response = $mavenResult.Result
    if ($response.StatusCode -eq 200) {
        $alreadyDeployed = $true
        Write-Host "  [XX] pulsar-bom $Version is ALREADY on Maven Central."
    } else {
        Write-Host "  [OK] pulsar-bom $Version is NOT on Maven Central (HTTP $($response.StatusCode))."
    }
} else {
    $statusCode = if ($mavenResult.Error.Exception.Response) {
        [int]$mavenResult.Error.Exception.Response.StatusCode
    } else {
        $null
    }

    if ($statusCode -eq 404) {
        # 404 is the expected result — the artifact hasn't been published yet
        Write-Host "  [OK] pulsar-bom $Version is NOT on Maven Central (HTTP 404)."
    } elseif ($null -eq $statusCode) {
        Write-Host "  [!!] Could not reach Maven Central (network error)."
        Write-Host "       Verify your internet connection and try again."
    } else {
        Write-Host "  [!!] pulsar-bom $Version returned HTTP $statusCode."
    }
}

# ---------------------------------------------------------------
# Check 3: Is the latest release EARLIER than this version?
#          (no one has leapfrogged us with a newer release)
# ---------------------------------------------------------------
Write-Host ""
Write-Host "-------------------------------------------------"
Write-Host " Check 3: Version precedence (not leapfrogged)"
Write-Host "-------------------------------------------------"

if ($latestReleaseTag) {
    # Normalise the tag: strip leading 'v' and optional -rc.N suffix
    $normalisedTag = $latestReleaseTag -replace '^v', ''
    if ($normalisedTag -match '^(\d+\.\d+\.\d+)(?:-rc\.\d+)?$') {
        $latestVersionObj = [version]$matches[1]
    } else {
        $latestVersionObj = $null
    }

    if ($targetVersionObj -and $latestVersionObj) {
        if ($latestVersionObj -gt $targetVersionObj) {
            $leapfrogged = $true
            Write-Host "  [XX] Latest release ($latestReleaseTag) is NEWER than v$Version — leapfrogged!"
        } elseif ($latestVersionObj -eq $targetVersionObj) {
            # Same base version but latest might be a -rc.N while target is the final release
            if ($latestReleaseTag -match '-rc\.\d+$' -and $Version -notmatch '-rc\.\d+$') {
                Write-Host "  [OK] Latest ($latestReleaseTag) is an RC of the same version — final release is next."
            } else {
                Write-Host "  [OK] Latest release ($latestReleaseTag) is the same base version as v$Version."
            }
        } else {
            Write-Host "  [OK] Latest release ($latestReleaseTag) is earlier than v$Version — correct order."
        }
    } else {
        Write-Verbose "  Could not parse versions for numeric comparison."
        if ($latestReleaseTag -eq "v$Version") {
            # Already flagged in check 1
            $leapfrogged = $false
        } else {
            Write-Host "  [OK] Latest release ($latestReleaseTag) differs from v$Version."
        }
    }
} else {
    Write-Host "  [OK] No prior releases — v$Version is the first."
}

# ---------------------------------------------------------------
# Summary
# ---------------------------------------------------------------
Write-Host ""
Write-Host "-------------------------------------------------"
Write-Host " Summary"
Write-Host "-------------------------------------------------"

Write-Host ""
Write-Host "  Version            : v$Version"
Write-Host "  Already released   : $(if ($alreadyReleased) { '[XX] YES' } else { '[OK] NO' })"
Write-Host "  Already on Maven   : $(if ($alreadyDeployed)  { '[XX] YES' } else { '[OK] NO' })"
Write-Host "  Leapfrogged        : $(if ($leapfrogged)      { '[XX] YES' } else { '[OK] NO' })"
Write-Host "  Ready to publish   : $(if (-not $alreadyReleased -and -not $alreadyDeployed -and -not $leapfrogged) { '[OK] YES' } else { '[XX] NO' })"
Write-Host ""

# Determine exit code
$blockers = @()
if ($alreadyReleased) { $blockers += "already the latest GitHub release" }
if ($alreadyDeployed)  { $blockers += "already on Maven Central" }
if ($leapfrogged)      { $blockers += "leapfrogged by a newer release ($latestReleaseTag)" }

if ($blockers.Count -eq 0) {
    Write-Host "Ready to publish v$Version."
    exit 0
} else {
    Write-Host "NOT ready to publish — blockers:"
    $blockers | ForEach-Object { Write-Host "  - $_" }
    exit 1
}
