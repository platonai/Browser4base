#!/usr/bin/env pwsh

param(
    [string]$remote = "origin",
    [string]$message = "",
    [switch]$Yes,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$repoRoot = (git rev-parse --show-toplevel 2>$null)
Set-Location $repoRoot

# Import common utility script
. $repoRoot\bin\common\Util.ps1
. $repoRoot\bin\common\ReleaseVersion.ps1

Fix-Encoding-UTF8

Write-Host "Working in: $repoRoot"

# Check if we're in a git repo
if (!(Test-Path ".git")) {
    Write-Error "Not a git repository"
    exit 1
}

# Check current branch
$branch = git rev-parse --abbrev-ref HEAD

Write-Host "Current branch: $branch"

# Check for uncommitted changes
$status = git status --porcelain
if ($status) {
    Write-Warning "Uncommitted changes detected"
    if (-not $Yes) {
        $continue = Read-Host "Continue anyway? (y/n)"
        if ($continue -ne 'y') {
            Write-Host "Cancelled"
            exit 0
        }
    } else {
        Write-Host "Auto-continuing (-Yes)"
    }
}

# Read and process version
$version = (Get-Content "VERSION").Trim()
Write-Host "Version from file: $version"

if ($version.EndsWith("-SNAPSHOT")) {
    $version = $version.Replace("-SNAPSHOT", "")
    Write-Host "Cleaned version: $version"
}

# Validate version format (support rc tags like x.y.z-rc.1)
if ($version -notmatch "^\d+\.\d+\.\d+(?:-rc\.\d+)?$") {
    Write-Error "Invalid version format: $version"
    exit 1
}

$newTag = "v$version"

# ── Release version preflight ──────────────────────────────────────
# Never create a tag for a version that cannot publish: Maven Central
# rejects re-publishing the same version, so an already published,
# already released or leapfrogged version can only produce a failed
# release. Same gate as monitor-release.ps1 — shared logic lives in
# bin/common/ReleaseVersion.ps1.

gh --version > $null 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "gh CLI is required for the release version preflight."
    exit 1
}

Write-Host ""
Write-Host "Running release version preflight for v$version ..."
if (-not (Assert-ReleaseVersionPublishable -Version $version -Force:$Force)) {
    Write-Host "Cancelled — v$version is not publishable. Bump the version first, e.g.:" -ForegroundColor Yellow
    Write-Host "  pwsh bin/release/bump-version.ps1 -Part patch" -ForegroundColor Yellow
    exit 1
}

# Check if tag already exists
$existingTag = git tag -l $newTag
if ($existingTag) {
    Write-Host "Tag '$newTag' already exists"

    # Overwriting a tag that a GitHub release consumed re-triggers
    # release.yml against a released version and the workflow fails at
    # deploy. -Force is required even with -Yes: automation must opt in
    # deliberately instead of auto-confirming the overwrite.
    $releaseExists = Test-GitHubReleaseExists -Tag $newTag
    if ($releaseExists -and -not $Force) {
        Write-Error "GitHub release '$newTag' already exists. Pass -Force to overwrite it — without it the script refuses to touch a released tag (even with -Yes)."
        exit 1
    }
    if ($releaseExists) {
        Write-Warning "GitHub release '$newTag' exists — overwriting because -Force was given. The re-triggered workflow may still fail if Central already holds the artifacts."
    }

    if (-not $Yes) {
        $confirm = Read-Host "Do you want to overwrite it? (y/n)"
        if ($confirm -ne 'y') {
            Write-Host "Cancelled"
            exit 0
        }
    }
    try {
        # Delete local tag
        git tag -d $newTag
        Write-Host "Deleted local tag: $newTag"

        # Delete remote tag if it exists
        $remoteTag = git ls-remote --tags $remote "refs/tags/$newTag" 2>$null
        if ($remoteTag) {
            git push $remote --delete $newTag
            Write-Host "Deleted remote tag: $newTag"
        }
    } catch {
        Write-Error "Failed to delete existing tag: $_"
        exit 1
    }
}

function Get-TagSortKey {
    param(
        [string]$Tag
    )

    $clean = $Tag -replace '^v',''
    if ($clean -notmatch '^(?<base>\d+\.\d+\.\d+)(?:-rc\.(?<rc>\d+))?$') {
        return $null
    }

    $baseVersion = [version]$matches['base']
    $rcValue = if ($matches['rc']) { [int]$matches['rc'] } else { [int]::MaxValue }

    return [pscustomobject]@{
        Base = $baseVersion
        Rc = $rcValue
    }
}

# Get previous tag for release notes (supports vX.Y.Z and X.Y.Z-rc.N)
$tagCandidates = git tag --list | Where-Object { $_ -match '^(v\d+\.\d+\.\d+|\d+\.\d+\.\d+-rc\.\d+)$' }
$prevTag = $tagCandidates |
        ForEach-Object {
            $key = Get-TagSortKey $_
            if ($key) {
                [pscustomobject]@{ Tag = $_; Base = $key.Base; Rc = $key.Rc }
            }
        } |
        Sort-Object Base, Rc -Descending |
        Select-Object -First 1 |
        ForEach-Object { $_.Tag }

if ($prevTag) {
    Write-Host "`nChanges since $prevTag :"
    $changes = git log --oneline --no-merges "$prevTag..HEAD"
    if ($changes) {
        $changes | ForEach-Object { Write-Host "  - $_" }
    } else {
        Write-Host "  No changes"
    }
} else {
    Write-Host "`nRecent commits:"
    git log --oneline --no-merges -5 | ForEach-Object { Write-Host "  • $_" }
}

# Prompt for tag message if not provided (skip when -Yes)
if ([string]::IsNullOrWhiteSpace($message) -and -not $Yes) {
    Write-Host ""
    $message = Read-Host "Enter release message (optional, press Enter to skip)"
}

# Confirm creation
Write-Host ""
$tagType = if ([string]::IsNullOrWhiteSpace($message)) { "lightweight" } else { "annotated" }
if (-not $Yes) {
    $confirm = Read-Host "Create and push $tagType tag '$newTag'? (y/n)"
    if ($confirm -ne 'y') {
        Write-Host "Cancelled"
        exit 0
    }
}

# Create and push tag
try {
    # Create annotated tag if message provided, otherwise lightweight tag
    if ([string]::IsNullOrWhiteSpace($message)) {
        git tag $newTag
        Write-Host "Created lightweight tag: $newTag"
    } else {
        git tag -a $newTag -m $message
        Write-Host "Created annotated tag: $newTag"
    }

    # Push tag to remote
    git push $remote $newTag
    Write-Host "Successfully pushed tag: $newTag"

    # Try to show GitHub URL
    $remoteUrl = git config --get remote.$remote.url
    if ($remoteUrl -match 'github\.com[:/](.+?)(?:\.git)?$') {
        $repo = $matches[1]
        Write-Host "Release URL: https://github.com/$repo/releases/tag/$newTag"
    }

    Write-Output $newTag
} catch {
    Write-Error "Failed to create/push tag: $_"
    exit 1
}
