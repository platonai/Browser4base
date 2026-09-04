#!/usr/bin/env pwsh

# ═══════════════════════════════════════════════════════════════════
# Shared release-version preflight helpers (dot-source, functions only,
# no top-level side effects). Used by:
#   - bin/release/trigger-release.ps1
#   - bin/release/monitor-release.ps1
#
# Guards "the released version number must be correct": a version that
# is already on Maven Central, already consumed by a GitHub release, or
# older than the latest release (leapfrog) can never publish — Central
# rejects re-publishing the same version, so such a release can only
# fail during deploy. Fail fast before the tag push instead.
# ═══════════════════════════════════════════════════════════════════

# Reads VERSION ("X.Y.Z", "X.Y.Z-SNAPSHOT" or "X.Y.Z-rc.N") and returns
# the clean version without the -SNAPSHOT suffix, or $null when invalid.
function Get-ReleaseVersionFromFile {
    param([string]$VersionFile)
    $raw = (Get-Content $VersionFile -TotalCount 1).Trim()
    $clean = $raw -replace '-SNAPSHOT$', ''
    if ($clean -notmatch '^\d+\.\d+\.\d+(?:-rc\.\d+)?$') { return $null }
    return $clean
}

# $true when pulsar-bom <Version> is already published on Maven Central
# (Central never accepts the same version twice — a definitive blocker).
function Test-VersionOnMavenCentral {
    param([string]$Version)
    $url = "https://repo1.maven.org/maven2/ai/platon/pulsar/pulsar-bom/$Version/pulsar-bom-$Version.pom"
    try {
        $resp = Invoke-WebRequest -Uri $url -Method Head -TimeoutSec 30 -UseBasicParsing
        return ($resp.StatusCode -eq 200)
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) { return $false }
        Write-Warning "Could not reach Maven Central ($url): $($_.Exception.Message)"
        return $false
    }
}

# $true when a GitHub release for <Tag> already exists.
function Test-GitHubReleaseExists {
    param([string]$Tag)
    gh release view $Tag 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}

# Returns the tag of the latest GitHub release (e.g. "v4.11.12"), or $null.
function Get-LatestReleaseTag {
    $ghLatest = gh release list --limit 1 --json tagName 2>$null
    if (-not $ghLatest) { return $null }
    $latestObj = $ghLatest | ConvertFrom-Json
    if ($latestObj -and $latestObj.Count -gt 0) { return $latestObj[0].tagName }
    return $null
}

# Parses "vX.Y.Z" / "vX.Y.Z-rc.N" into comparable parts; $null on bad input.
function Get-TagParts {
    param([string]$Tag)
    if ($Tag -notmatch '^v(?<v>\d+)\.(?<m>\d+)\.(?<p>\d+)(?:-rc\.(?<rc>\d+))?$') { return $null }
    $rc = if ($Matches['rc']) { [int]$Matches['rc'] } else { [int]::MaxValue }
    return [pscustomobject]@{
        V  = [int]$Matches['v']
        M  = [int]$Matches['m']
        P  = [int]$Matches['p']
        Rc = $rc
    }
}

# $true when <Current> is strictly older than <Latest> (numeric compare).
function Test-TagLeapfrogged {
    param([string]$Current, [string]$Latest)
    if (-not $Current -or -not $Latest) { return $false }
    $c = Get-TagParts -Tag $Current
    $l = Get-TagParts -Tag $Latest
    if (-not $c -or -not $l) { return $false }
    if ($l.V -ne $c.V) { return $l.V -gt $c.V }
    if ($l.M -ne $c.M) { return $l.M -gt $c.M }
    if ($l.P -ne $c.P) { return $l.P -gt $c.P }
    return $l.Rc -gt $c.Rc
}

<#
.SYNOPSIS
    Full release-version gate shared by trigger-release.ps1 and
    monitor-release.ps1. Prints diagnostics and returns $false (blocked)
    when the version cannot publish:
      1. already on Maven Central                       -> always blocked
      2. GitHub release exists, artifacts NOT on Central -> blocked unless -Force
                                                          (legit re-run after a failed deploy)
      3. older than the latest release (leapfrog)        -> always blocked
#>
function Assert-ReleaseVersionPublishable {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Version,
        [switch]$Force
    )
    $tag = "v$Version"

    # 1) Never re-release a version that is already on Maven Central.
    if (Test-VersionOnMavenCentral -Version $Version) {
        Write-Host "[XX] v$Version is ALREADY published on Maven Central." -ForegroundColor Red
        Write-Host "     Central never accepts the same version twice, so this release would only" -ForegroundColor Red
        Write-Host "     fail during deploy. Bump the version first, e.g.:" -ForegroundColor Yellow
        Write-Host "       pwsh bin/release/bump-version.ps1 -Part patch" -ForegroundColor Yellow
        return $false
    }

    # 2) A GitHub release for this tag already exists?
    $releaseExists = Test-GitHubReleaseExists -Tag $tag
    if ($releaseExists -and -not $Force) {
        Write-Host ""
        Write-Host "[XX] GitHub release $tag already exists." -ForegroundColor Red
        Write-Host "     Its artifacts are NOT on Maven Central, so this looks like a re-run of a" -ForegroundColor Yellow
        Write-Host "     release that failed during deploy. Pass -Force to overwrite the tag and" -ForegroundColor Yellow
        Write-Host "     re-trigger the workflow — without it the preflight refuses to re-release." -ForegroundColor Yellow
        return $false
    }
    if ($releaseExists) {
        Write-Warning "GitHub release $tag exists but is not on Maven Central — overwriting (re-run) because -Force was given. The re-triggered workflow may still fail."
    }

    # 3) Never release a version older than the latest release (leapfrog).
    $latestTag = Get-LatestReleaseTag
    if (Test-TagLeapfrogged -Current $tag -Latest $latestTag) {
        Write-Host ""
        Write-Host "[XX] v$Version is OLDER than the latest release ($latestTag) — version drift." -ForegroundColor Red
        Write-Host "     Bump the version first, e.g.:" -ForegroundColor Yellow
        Write-Host "       pwsh bin/release/bump-version.ps1 -Part patch" -ForegroundColor Yellow
        return $false
    }

    if ($latestTag) {
        Write-Host "[OK] v$Version is a valid next release (latest release: $latestTag)." -ForegroundColor Green
    } else {
        Write-Host "[OK] v$Version has no prior releases to conflict with." -ForegroundColor Green
    }
    return $true
}
