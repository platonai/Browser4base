#!/usr/bin/env node

/**
 * Unified version maintenance script for Browser4base.
 *
 * The project uses a SINGLE version number across all Maven modules.  The
 * repo-root VERSION file is the sole source of truth — every pom.xml in the
 * reactor (excluding the independently-versioned pulsar-parent) derives its
 * version from it.
 *
 * Usage:
 *   # Version queries
 *   node bin/version.mjs show              Print project version
 *   node bin/version.mjs show -v           Print version + git metadata
 *
 *   # Version changes
 *   node bin/version.mjs release           Strip -SNAPSHOT / -rc.N for release
 *   node bin/version.mjs bump <part>       Bump major/minor/patch, update pom.xml, commit
 *   node bin/version.mjs bump rc           Create/increment -rc.N candidate (rc.1, rc.2, ...)
 *   node bin/version.mjs bump <part> --dry-run     Show what would change
 *   node bin/version.mjs bump <part> --skip-precheck  Skip publish-status check
 *   node bin/version.mjs auto              Show bump plan (dry-run by default)
 *   node bin/version.mjs auto --dry-run    Same as default (explicit dry-run)
 *   node bin/version.mjs auto --commit     Apply bump, commit, and push
 *
 *   # Full sync (VERSION → all pom.xml versions)
 *   node bin/version.mjs sync              Sync VERSION to all pom.xml files
 *   node bin/version.mjs sync --check      Check-only mode (CI, exit 1 if mismatch)
 *   node bin/version.mjs sync --dry-run    Show what would change without applying
 *
 *   # Cross-cutting
 *   node bin/version.mjs check             Full version consistency check
 *   node bin/version.mjs prerelease-check  Consistency + next-patch guard (JSON output)
 */

import { execSync } from "child_process";
import { existsSync, readFileSync, readdirSync, writeFileSync } from "fs";
import { join, relative, resolve } from "path";
import { createInterface } from "readline";
import { fileURLToPath } from "url";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const __dirname = resolve(fileURLToPath(import.meta.url), "..");

/**
 * Modules that are versioned independently of the unified project version and
 * must never be touched by bump/sync/check.  pulsar-parent is published
 * separately (see AGENTS.md) and keeps its own 4.5.x line.
 */
const INDEPENDENT_POM_DIRS = ["pulsar-parent"];

/** Find the repository root via git, falling back to walking up from this script. */
function getRepoRoot() {
  try {
    return execSync("git rev-parse --show-toplevel", {
      stdio: ["ignore", "pipe", "ignore"],
    })
      .toString()
      .trim();
  } catch {
    let dir = __dirname;
    while (dir !== resolve(dir, "..")) {
      if (existsSync(join(dir, "VERSION"))) return dir;
      dir = resolve(dir, "..");
    }
    console.error("ERROR: Cannot find repository root.");
    process.exit(1);
  }
}

const REPO_ROOT = getRepoRoot();

/** True if the pom path belongs to an independently-versioned module. */
function isIndependentPom(pomPath) {
  const rel = relative(REPO_ROOT, pomPath).replace(/\\/g, "/");
  return INDEPENDENT_POM_DIRS.some((dir) => rel === `${dir}/pom.xml` || rel.startsWith(`${dir}/`));
}

function stripSnapshot(version) {
  return version.endsWith("-SNAPSHOT")
    ? version.slice(0, -"-SNAPSHOT".length)
    : version;
}

/** Strip a trailing `-rc.N` prerelease qualifier (case-insensitive). */
function stripRc(version) {
  return version.replace(/-rc\.\d+$/i, "");
}

function parseSemver(version) {
  const m = version.match(/^(\d+)\.(\d+)\.(\d+)(?:-(.+))?$/);
  if (!m) return null;
  const prerelease = m[4] || null;
  const result = {
    major: Number(m[1]),
    minor: Number(m[2]),
    patch: Number(m[3]),
    prerelease,
  };
  if (prerelease) {
    const rcMatch = prerelease.match(/^rc\.(\d+)$/i);
    result.rc = rcMatch ? Number(rcMatch[1]) : null;
  } else {
    result.rc = null;
  }
  return result;
}

/**
 * Compare two prerelease identifiers (the part after "-"), e.g. "rc.1" vs "rc.2".
 * Follows semver precedence: a missing prerelease sorts after any present one.
 */
function comparePrerelease(a, b) {
  if (a === b) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  const aIds = a.split(".");
  const bIds = b.split(".");
  const len = Math.max(aIds.length, bIds.length);
  for (let i = 0; i < len; i++) {
    const ai = aIds[i];
    const bi = bIds[i];
    if (ai === undefined) return -1;
    if (bi === undefined) return 1;
    if (ai === bi) continue;
    const aNum = /^\d+$/.test(ai);
    const bNum = /^\d+$/.test(bi);
    if (aNum && bNum) return Number(ai) < Number(bi) ? -1 : 1;
    if (aNum) return -1;
    if (bNum) return 1;
    return ai < bi ? -1 : 1;
  }
  return 0;
}

/**
 * Compare two version strings per semver precedence.
 * Returns -1 (a < b), 0 (a == b), or 1 (a > b). Unparseable input compares equal.
 */
function compareSemver(a, b) {
  const pa = parseSemver(a);
  const pb = parseSemver(b);
  if (!pa || !pb) return 0;
  if (pa.major !== pb.major) return pa.major < pb.major ? -1 : 1;
  if (pa.minor !== pb.minor) return pa.minor < pb.minor ? -1 : 1;
  if (pa.patch !== pb.patch) return pa.patch < pb.patch ? -1 : 1;
  return comparePrerelease(pa.prerelease, pb.prerelease);
}

/**
 * Validate that a version string is a safe semver (X.Y.Z or X.Y.Z-prerelease).
 * Rejects anything with shell metacharacters that could lead to injection.
 */
function validateVersion(version) {
  if (!/^[a-zA-Z0-9][a-zA-Z0-9._+-]*$/.test(version)) {
    console.error(
      `ERROR: Version "${version}" contains unexpected characters. ` +
      "Expected format: X.Y.Z or X.Y.Z-prerelease."
    );
    process.exit(1);
  }
  if (!/^\d+\.\d+\.\d+(-[a-zA-Z0-9._+-]+)?$/.test(version)) {
    console.error(
      `ERROR: Version "${version}" does not match expected semver format (X.Y.Z or X.Y.Z-prerelease).`
    );
    process.exit(1);
  }
  return version;
}

function readBackendVersion() {
  const path = join(REPO_ROOT, "VERSION");
  if (!existsSync(path)) {
    console.error("ERROR: VERSION file not found at", path);
    process.exit(1);
  }
  return validateVersion(readFileSync(path, "utf-8").trim());
}

function checkVersionBump(published, local) {
  const pub = parseSemver(published);
  const loc = parseSemver(local);
  if (!pub || !loc) return { ok: true };

  const cmp = compareSemver(local, published);
  if (cmp < 0) {
    return {
      ok: false,
      reason: `local version ${local} is behind the published version ${published}`,
    };
  }
  if (cmp === 0) {
    return { ok: true, note: `version ${local} is already published` };
  }

  const sameBase =
    loc.major === pub.major && loc.minor === pub.minor && loc.patch === pub.patch;

  // rc candidates: only a +1 increment is valid, or finalizing into stable.
  if (sameBase && pub.rc != null) {
    if (loc.rc != null) {
      if (loc.rc === pub.rc + 1) return { ok: true };
      if (loc.rc > pub.rc) {
        return {
          ok: false,
          reason: `version bump from ${published} to ${local} skips rc versions (expected -rc.${pub.rc + 1})`,
        };
      }
      return {
        ok: false,
        reason: `version bump from ${published} to ${local} is not a forward rc increment`,
      };
    }
    return { ok: true };
  }

  if (loc.major === pub.major && loc.minor === pub.minor && loc.patch === pub.patch + 1) {
    return { ok: true };
  }
  if (loc.major === pub.major && loc.minor === pub.minor + 1 && loc.patch === 0) {
    return { ok: true };
  }
  return {
    ok: false,
    reason: `version bump from ${published} to ${local} is neither a patch nor a minor increment`,
  };
}

/** Bump a semver version string by the given part. */
function bumpSemverPart(version, part) {
  const parsed = parseSemver(version);
  if (!parsed) return null;
  let { major, minor, patch } = parsed;
  switch (part) {
    case "major": major++; minor = 0; patch = 0; break;
    case "minor": minor++; patch = 0; break;
    case "patch": patch++; break;
    default: return null;
  }
  return `${major}.${minor}.${patch}`;
}

/** Bump to (or increment) an `-rc.N` prerelease of the given version. */
function bumpRc(version) {
  const parsed = parseSemver(version);
  if (!parsed) return null;
  const base = `${parsed.major}.${parsed.minor}.${parsed.patch}`;
  return `${base}-rc.${parsed.rc != null ? parsed.rc + 1 : 1}`;
}

/** Walk dir up to maxDepth finding files named `targetName`. */
function findFiles(dir, targetName, maxDepth) {
  const results = [];
  const walk = (current, depth) => {
    if (depth > maxDepth) return;
    let entries;
    try {
      entries = readdirSync(current, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const full = join(current, entry.name);
      if (entry.isDirectory()) {
        if (entry.name === "node_modules" || entry.name === "target" || entry.name === ".git") continue;
        walk(full, depth + 1);
      } else if (entry.isFile() && entry.name === targetName) {
        results.push(full);
      }
    }
  };
  walk(dir, 0);
  return results;
}

/** Print a message using the maintenance check result format. */
function checkItem(label, status, message) {
  const icon = status === "passed" ? "✓" : status === "failed" ? "✗" : status === "error" ? "‼" : "○";
  console.log(`  ${icon} ${label}: ${message}`);
  return status;
}

// ---------------------------------------------------------------------------
// Maven helpers
// ---------------------------------------------------------------------------

/** Escape special regex characters in a string. */
function escapeRegex(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * Read the project's own <version> from a pom.xml — the one that is a direct
 * child of <project>, NOT versions inside <parent>, <dependencies>,
 * <dependencyManagement>, <build> or <profiles>.  Returns null when the pom
 * has no explicit own version (it inherits from its parent, which Maven
 * versions:set -DprocessAllModules handles automatically).
 *
 * Implemented with a lightweight depth-tracking tag scanner so stray version
 * strings in comments or nested blocks cannot fool it.
 */
function readPomProjectVersion(pomPath) {
  const content = readFileSync(pomPath, "utf-8").replace(/<!--[\s\S]*?-->/g, "");
  const tagRe = /<(\/?)([\w.:-]+)(?:\s[^>]*)?\/?>/g;
  let inProject = false;
  let depth = 0; // nesting depth below <project>
  let m;
  while ((m = tagRe.exec(content))) {
    const closing = m[1] === "/";
    const name = m[2];
    const selfClosing = m[0].endsWith("/>");
    if (!inProject) {
      if (name === "project" && !closing) {
        inProject = true;
        if (selfClosing) break;
      }
      continue;
    }
    if (name === "project" && closing) break;
    if (closing) {
      depth = Math.max(0, depth - 1);
      continue;
    }
    if (selfClosing) continue;
    if (depth === 0 && name === "version") {
      const start = tagRe.lastIndex;
      const end = content.indexOf("</version>", start);
      if (end === -1) return null;
      return content.slice(start, end).trim();
    }
    depth++;
  }
  return null;
}

/**
 * Collect all pom.xml files that belong to the Maven reactor by recursively
 * following <module> entries from the root pom (including profile-activated
 * modules).  Poms outside the reactor (examples/, pulsar-coverage-report/,
 * pulsar-parent/) are NOT managed by versions:set and are never checked or
 * modified.
 */
function getReactorPoms() {
  const results = new Set();
  const visit = (dir, depth) => {
    if (depth > 8) return;
    const pomPath = join(dir, "pom.xml");
    if (!existsSync(pomPath)) return;
    results.add(pomPath);
    const content = readFileSync(pomPath, "utf-8");
    for (const m of content.matchAll(/<module>\s*([^<]+?)\s*<\/module>/g)) {
      visit(join(dir, m[1].replace(/\\/g, "/")), depth + 1);
    }
  };
  visit(REPO_ROOT, 0);
  return [...results];
}

/** Run Maven versions:set on all reactor modules. */
function mavenSetVersion(version) {
  const isWindows = process.platform === "win32";
  const mvnCmd = isWindows ? join(REPO_ROOT, "mvnw.cmd") : join(REPO_ROOT, "mvnw");
  const mvnArgs = [
    "versions:set",
    `-DnewVersion=${version}`,
    "-DprocessAllModules",
    "-DgenerateBackupPoms=false",
  ];
  if (isWindows) {
    execSync(`cmd /c "${mvnCmd}" ${mvnArgs.join(" ")}`, {
      cwd: REPO_ROOT,
      stdio: "inherit",
    });
  } else {
    execSync(`"${mvnCmd}" ${mvnArgs.join(" ")}`, {
      cwd: REPO_ROOT,
      stdio: "inherit",
    });
  }
}

/** Revert VERSION + any pom.xml files Maven may have partially modified. */
function revertVersionChanges(previousSnapshotVersion) {
  writeFileSync(join(REPO_ROOT, "VERSION"), previousSnapshotVersion + "\n");
  try {
    const changed = execSync('git diff --name-only -- "pom.xml" "*/pom.xml"', {
      cwd: REPO_ROOT, stdio: ["ignore", "pipe", "ignore"],
    }).toString().trim();
    if (changed) {
      for (const f of changed.split("\n").filter(Boolean)) {
        execSync(`git checkout -- "${f}"`, { cwd: REPO_ROOT, stdio: "pipe" });
      }
    }
  } catch { /* best-effort revert */ }
}

/**
 * Update the root pom.xml SCM <tag> to `expectedTag`.
 *
 * This repo's default tag is HEAD (set by the release flow), so the tag is
 * only rewritten when the current value already looks like a `vX.Y.Z` tag —
 * a HEAD (or otherwise drifted) tag is left untouched with a note, mirroring
 * the conservative behaviour of bin/release/bump-version.ps1.
 */
function setPomTag(expectedTag) {
  const pomXmlPath = join(REPO_ROOT, "pom.xml");
  if (!existsSync(pomXmlPath)) {
    console.warn("  Warning: root pom.xml not found; <tag> not updated.");
    return false;
  }
  const pomContent = readFileSync(pomXmlPath, "utf-8");
  const tagMatch = pomContent.match(/<tag>([^<]*)<\/tag>/);
  if (!tagMatch) {
    console.warn("  Warning: could not find <tag> in root pom.xml; leaving it unchanged.");
    return false;
  }
  const currentTag = tagMatch[1];
  if (currentTag === expectedTag) {
    console.log(`  pom.xml <tag> already up to date (${currentTag})`);
    return true;
  }
  if (!/^v\d+\.\d+\.\d+(-[a-zA-Z0-9._+-]+)?$/.test(currentTag)) {
    console.log(`  pom.xml <tag> is "${currentTag}" (not a vX.Y.Z tag) — left unchanged`);
    return true;
  }
  writeFileSync(
    pomXmlPath,
    pomContent.replace(new RegExp(`<tag>${escapeRegex(currentTag)}</tag>`), `<tag>${expectedTag}</tag>`)
  );
  console.log(`  Updated pom.xml <tag>: ${currentTag} -> ${expectedTag}`);
  return true;
}

/** Check that all reactor pom.xml files match the expected version. */
function checkAllPomVersions(expectedVersion) {
  const pomFiles = getReactorPoms();
  if (pomFiles.length === 0) {
    checkItem("pom.xml", "error", "No reactor pom.xml files found");
    process.exitCode = 1;
    return;
  }
  let outOfSync = 0;
  let inherited = 0;
  const rootPom = join(REPO_ROOT, "pom.xml");
  for (const pomPath of pomFiles) {
    if (pomPath === rootPom) continue; // reported separately in cmdCheck
    const relPath = relative(REPO_ROOT, pomPath);
    const pomVersion = readPomProjectVersion(pomPath);
    if (pomVersion === null) {
      // No explicit own version — inherits from its parent, always consistent.
      inherited++;
    } else if (pomVersion === expectedVersion) {
      checkItem(relPath, "passed", pomVersion);
    } else {
      checkItem(relPath, "failed", `${pomVersion} (expected ${expectedVersion})`);
      outOfSync++;
      process.exitCode = 1;
    }
  }
  if (inherited > 0) {
    checkItem("(inherited)", "passed", `${inherited} pom.xml files inherit their version from the parent`);
  }
  const nonReactor = findFiles(REPO_ROOT, "pom.xml", 8)
    .filter((p) => !isIndependentPom(p) && !pomFiles.includes(p));
  if (nonReactor.length > 0) {
    checkItem(
      "(non-reactor)",
      "skipped",
      `${nonReactor.length} pom.xml files outside the reactor (examples/, coverage-report/, ...) — not managed`
    );
  }
}

// ---------------------------------------------------------------------------
// Subcommand: show
// ---------------------------------------------------------------------------

function cmdShow(args) {
  const verbose = args.includes("-v") || args.includes("--verbose");

  if (verbose) {
    const version = readBackendVersion();
    let hash = "", branch = "", date = "";
    try {
      hash = execSync("git rev-parse --short=7 HEAD", {
        stdio: ["ignore", "pipe", "ignore"],
      }).toString().trim();
    } catch { /* ignore */ }
    try {
      branch = execSync("git rev-parse --abbrev-ref HEAD", {
        stdio: ["ignore", "pipe", "ignore"],
      }).toString().trim();
    } catch { /* ignore */ }
    try {
      date = execSync("git log -1 --pretty=%ad --date=short", {
        stdio: ["ignore", "pipe", "ignore"],
      }).toString().trim();
    } catch { /* ignore */ }
    console.log(`v${version} ${hash} ${branch} ${date}`);
  } else {
    console.log(`v${readBackendVersion()}`);
  }
}

// ---------------------------------------------------------------------------
// Subcommand: sync (sync VERSION → all pom.xml files)
// ---------------------------------------------------------------------------

function cmdSync(args) {
  const checkOnly = args.includes("--check");
  const dryRun = args.includes("--dry-run");

  if (checkOnly && dryRun) {
    console.error("ERROR: --check and --dry-run are mutually exclusive");
    process.exit(1);
  }

  const version = readBackendVersion();

  if (checkOnly) {
    console.log(`VERSION: ${version}`);
  } else if (dryRun) {
    console.log(`VERSION: ${version}`);
    console.log("========== DRY-RUN MODE ==========");
    console.log("No changes will be made.\n");
  }

  // ── 1. Maven pom.xml files ──────────────────────────────────────────
  if (checkOnly) {
    checkAllPomVersions(version);
  } else if (dryRun) {
    console.log(`  [DRY-RUN] mvnw versions:set -DnewVersion=${version} -DprocessAllModules -DgenerateBackupPoms=false`);
    console.log(`  [DRY-RUN] pom.xml <tag> would be set to v${stripSnapshot(version)} only if it is already a vX.Y.Z tag`);
  } else {
    try {
      mavenSetVersion(version);
      console.log("  Synced all pom.xml files to " + version);
    } catch (e) {
      console.error("ERROR: Maven versions:set failed:", e.message);
      process.exit(1);
    }
  }

  // ── 2. Root pom.xml <tag> ───────────────────────────────────────────
  if (checkOnly) {
    const pomXmlPath = join(REPO_ROOT, "pom.xml");
    if (existsSync(pomXmlPath)) {
      const tagMatch = readFileSync(pomXmlPath, "utf-8").match(/<tag>([^<]*)<\/tag>/);
      if (tagMatch) {
        const currentTag = tagMatch[1];
        if (/^v\d+\.\d+\.\d+/.test(currentTag)) {
          const expectedTag = `v${stripSnapshot(version)}`;
          if (currentTag === expectedTag) {
            checkItem("pom.xml <tag>", "passed", currentTag);
          } else {
            checkItem("pom.xml <tag>", "failed", `${currentTag} (expected ${expectedTag})`);
            process.exitCode = 1;
          }
        } else {
          checkItem("pom.xml <tag>", "skipped", currentTag);
        }
      }
    }
  } else if (!dryRun) {
    setPomTag(`v${stripSnapshot(version)}`);
  }

  // ── Report ──────────────────────────────────────────────────────────
  if (checkOnly) {
    if (process.exitCode === 1) {
      console.error("\nVersion mismatch detected! Run 'node bin/version.mjs sync' to fix.");
    } else {
      console.log(`\nAll versions in sync with VERSION: ${version}`);
    }
  } else if (dryRun) {
    console.log("\n========== END DRY-RUN ==========");
    console.log("No changes were made.");
  } else {
    console.log(`\nFull sync complete. All pom.xml files match VERSION: ${version}`);
  }
}

// ---------------------------------------------------------------------------
// Subcommand: release
// ---------------------------------------------------------------------------

function cmdRelease() {
  const versionFileContent = readBackendVersion();
  // A release version has neither a -SNAPSHOT nor an -rc.N suffix.
  const version = stripRc(stripSnapshot(versionFileContent));

  if (versionFileContent !== version) {
    console.log(`Converting to release: ${versionFileContent} -> ${version}`);

    writeFileSync(join(REPO_ROOT, "VERSION"), version + "\n");
    console.log(`  Updated VERSION: ${versionFileContent} -> ${version}`);
  } else {
    console.log(`VERSION file already contains release version: ${version}`);
    console.log("Proceeding with file updates to ensure consistency (idempotent)...");
  }

  // Replace in pom.xml and READMEs (skipping independently-versioned modules)
  const filePatterns = ["pom.xml", "README.md", "README.zh.md"];
  for (const pattern of filePatterns) {
    const files = findFiles(REPO_ROOT, pattern, 8).filter((f) => !isIndependentPom(f));
    for (const file of files) {
      let content = readFileSync(file, "utf-8");
      if (versionFileContent !== version && content.includes(versionFileContent)) {
        content = content.replaceAll(versionFileContent, version);
        writeFileSync(file, content);
        console.log(`  Updated ${relative(REPO_ROOT, file)}: ${versionFileContent} -> ${version}`);
      }
    }
  }

  console.log(`\nRelease version conversion complete: ${version}`);
  console.log("NOTE: pom.xml versions were textually updated; run 'node bin/version.mjs sync' for a full Maven sync.");
}

// ---------------------------------------------------------------------------
// Subcommand: bump
// ---------------------------------------------------------------------------

/**
 * Prompt for confirmation on the terminal.
 * In non-TTY environments (CI pipelines, redirected stdin) we auto-confirm.
 */
async function confirm(prompt) {
  if (!process.stdin.isTTY) return true;
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => {
    rl.question(prompt, (answer) => {
      rl.close();
      const a = answer.trim();
      resolve(a === "" || a === "Y" || a === "y");
    });
  });
}

async function cmdBump(args) {
  const partIdx = args.findIndex(
    (a) =>
      a === "major" ||
      a === "minor" ||
      a === "patch" ||
      a === "rc" ||
      a === "prerelease"
  );
  if (partIdx === -1) {
    console.error("ERROR: Must specify part to bump: major, minor, patch, or rc");
    console.error("Usage: node bin/version.mjs bump <major|minor|patch|rc> [--dry-run] [--skip-precheck]");
    process.exit(1);
  }
  const part = args[partIdx] === "prerelease" ? "rc" : args[partIdx];
  const isRcBump = part === "rc";
  const dryRun = args.includes("--dry-run");
  const skipPrecheck = args.includes("--skip-precheck");

  // Ensure we're not on main/master
  let currentBranch;
  try {
    currentBranch = execSync("git rev-parse --abbrev-ref HEAD", {
      stdio: ["ignore", "pipe", "ignore"],
    }).toString().trim();
  } catch {
    console.error("ERROR: Cannot determine current git branch.");
    process.exit(1);
  }
  if (currentBranch === "master" || currentBranch === "main") {
    console.error(`You are on the '${currentBranch}' branch. Please switch to a feature branch first.`);
    process.exit(1);
  }

  // Read current version
  const snapshotVersion = readBackendVersion();
  const version = stripSnapshot(snapshotVersion);
  const parsed = parseSemver(version);
  if (!parsed) {
    console.error(`ERROR: Version '${version}' does not match X.Y.Z (or X.Y.Z-rc.N) format.`);
    process.exit(1);
  }

  // An rc bump produces (or increments) the -rc.N prerelease itself, which
  // replaces -SNAPSHOT as the version's prerelease marker.
  const nextVersion = isRcBump ? bumpRc(version) : bumpSemverPart(version, part);
  const nextSnapshot = isRcBump ? nextVersion : `${nextVersion}-SNAPSHOT`;

  // Precheck: verify current version is published (unless skipped). rc bumps
  // skip it by design — an rc candidate precedes release.
  if (!skipPrecheck && !dryRun && !isRcBump) {
    console.log("");
    console.log(`Running publish-status precheck for version v${version}...`);
    console.log("---------------------------------------------------------");
    const checkScript = join(REPO_ROOT, "bin", "release", "check-publish-status.ps1");
    if (existsSync(checkScript)) {
      const pwsh = process.platform === "win32" ? "pwsh.exe" : "pwsh";
      try {
        execSync(`${pwsh} -NoProfile -File "${checkScript}" -Version ${version}`, {
          cwd: REPO_ROOT,
          stdio: "inherit",
        });
      } catch {
        console.error("");
        console.error(
          `Precheck failed: version v${version} has not been fully published. ` +
          "Ensure the version is the latest GitHub release and pulsar-bom is on Maven Central. " +
          "Use --skip-precheck to bypass this check."
        );
        process.exit(1);
      }
    } else {
      console.warn(`check-publish-status.ps1 not found at '${checkScript}'. Skipping precheck.`);
    }
    console.log("---------------------------------------------------------");
    console.log("");
  } else if (isRcBump && !dryRun) {
    console.log("");
    console.log("Precheck skipped (rc bump): rc candidates precede release, so the current version is not expected to be published yet.");
    console.log("");
  } else if (skipPrecheck && !dryRun) {
    console.log("");
    console.log("Precheck skipped (--skip-precheck). Proceeding without publish-status verification.");
    console.log("");
  }

  console.log(`Current version: ${snapshotVersion}`);
  console.log(`New version:     ${nextSnapshot}`);

  if (dryRun) {
    console.log("");
    console.log("========== DRY-RUN MODE ==========");
    console.log("No changes will be made.");
    console.log("");
    console.log("The following actions would be performed:");
    console.log(`  1. Update VERSION file: '${snapshotVersion}' -> '${nextSnapshot}'`);
    console.log(`  2. Run Maven versions:set -DnewVersion=${nextSnapshot} on all reactor modules`);
    console.log(`  3. Update root pom.xml <tag> if it is currently a vX.Y.Z tag`);
    console.log("  4. git add .");
    console.log(`  5. git commit -m 'Bump version to v${nextVersion}'`);
    console.log("  6. git push");
    console.log("");
    console.log("Files that would be modified:");
    console.log("  - VERSION");
    console.log("  - pom.xml (root + all reactor modules)");
    console.log("==================================");
    process.exit(0);
  }

  // Update VERSION file
  writeFileSync(join(REPO_ROOT, "VERSION"), nextSnapshot + "\n");

  // Run Maven versions:set (revert VERSION + poms on failure)
  try {
    mavenSetVersion(nextSnapshot);
  } catch {
    console.error("Maven versions:set command failed. Reverting VERSION file and any modified pom.xml files.");
    revertVersionChanges(snapshotVersion);
    process.exit(1);
  }

  // Update root pom.xml <tag> (only if it is already a vX.Y.Z tag)
  setPomTag(`v${nextVersion}`);

  // Confirm and commit
  const comment = `Bump version to v${nextVersion}`;
  console.log(`Ready to commit with comment: <${comment}>`);
  const ok = await confirm("Are you sure to continue? [Y/n] ");
  if (!ok) {
    console.log("Operation cancelled. Run 'git checkout .' to revert changes.");
    process.exit(0);
  }

  try {
    execSync("git add .", { cwd: REPO_ROOT, stdio: "inherit" });
    execSync(`git commit -m "${comment}"`, { cwd: REPO_ROOT, stdio: "inherit" });
    execSync("git push", { cwd: REPO_ROOT, stdio: "inherit" });
    console.log(`Version bumped to ${nextVersion} and changes pushed to remote.`);
  } catch (e) {
    console.error("Git operation failed:", e.message);
    process.exit(1);
  }
}

// ---------------------------------------------------------------------------
// Helpers: release info & change summary (used by auto)
// ---------------------------------------------------------------------------

/** Get the latest GitHub release tag, falling back to git tags. */
function getLatestReleaseTag() {
  try {
    const raw = execSync("gh release list --limit 1 --exclude-pre-releases --json tagName", {
      cwd: REPO_ROOT, stdio: ["ignore", "pipe", "ignore"],
      timeout: 10_000,
    }).toString().trim();
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed.length > 0 && parsed[0].tagName) {
        return parsed[0].tagName;
      }
    }
  } catch { /* fall through */ }

  try {
    const raw = execSync("git tag --sort=-v:refname", {
      cwd: REPO_ROOT, stdio: ["ignore", "pipe", "ignore"],
    }).toString().trim();
    const stableTag = raw
      .split("\n")
      .map((t) => t.trim())
      .filter(Boolean)
      .find((t) => /^v?\d+\.\d+\.\d+$/.test(t));
    if (stableTag) return stableTag;
  } catch { /* fall through */ }

  try {
    const desc = execSync("git describe --tags --abbrev=0", {
      cwd: REPO_ROOT, stdio: ["ignore", "pipe", "ignore"],
    }).toString().trim();
    if (desc) return desc;
  } catch { /* ignore */ }
  return null;
}

/**
 * Print a section of git log / diff stat lines, indented and capped.
 */
function printGitSummary(title, lines, max) {
  const entries = lines.trim().split("\n").filter(Boolean);
  if (!entries.length) {
    console.log(`  ${title}: (none)`);
    return;
  }
  console.log(`  ${title} (${entries.length}):`);
  for (const line of entries.slice(0, max)) {
    console.log(`    ${line.trim()}`);
  }
  if (entries.length > max) {
    console.log(`    ... and ${entries.length - max} more`);
  }
}

/** Get the upstream base branch (origin/main or origin/master). */
function getBaseBranch() {
  for (const name of ["origin/main", "origin/master"]) {
    try {
      execSync(`git rev-parse --verify ${name}`, { cwd: REPO_ROOT, stdio: "ignore" });
      return name;
    } catch { /* try next */ }
  }
  for (const name of ["main", "master"]) {
    try {
      execSync(`git rev-parse --verify ${name}`, { cwd: REPO_ROOT, stdio: "ignore" });
      return name;
    } catch { /* try next */ }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Subcommand: auto (compute next patch after last release)
// ---------------------------------------------------------------------------

async function cmdAuto(args) {
  const commit = args.includes("--commit");
  const dryRun = args.includes("--dry-run");

  // ---- Verify we're not on main/master ----
  let currentBranch;
  try {
    currentBranch = execSync("git rev-parse --abbrev-ref HEAD", {
      stdio: ["ignore", "pipe", "ignore"],
    }).toString().trim();
  } catch {
    console.error("ERROR: Cannot determine current git branch.");
    process.exit(1);
  }
  if (currentBranch === "master" || currentBranch === "main") {
    console.error(
      `You are on the '${currentBranch}' branch. Auto-bump is disabled on protected branches.`
    );
    process.exit(1);
  }

  // ================================================================
  // PHASE 1 — Gather information
  // ================================================================

  const snapshotVersion = readBackendVersion();
  const localVersion = stripSnapshot(snapshotVersion);
  if (!parseSemver(localVersion)) {
    console.error(`ERROR: Version '${localVersion}' does not match X.Y.Z format.`);
    process.exit(1);
  }

  const lastReleaseTag = getLatestReleaseTag();

  // Parse last-release version (strip leading "v" and any prerelease suffix)
  const lastReleaseBackend = lastReleaseTag
    ? lastReleaseTag.replace(/^v/, "").replace(/-.*$/, "")
    : localVersion;

  // ---- Compute next version from last RELEASE (not local) ----
  const nextBackend = bumpSemverPart(lastReleaseBackend, "patch");
  const nextSnapshot = `${nextBackend}-SNAPSHOT`;
  const backendChanged = nextBackend !== localVersion;

  // Guard against accidental downgrades
  if (backendChanged) {
    const localParsed = parseSemver(localVersion);
    const nextParsed = parseSemver(nextBackend);
    if (localParsed && nextParsed) {
      const localIsAhead =
        localParsed.major > nextParsed.major ||
        (localParsed.major === nextParsed.major && localParsed.minor > nextParsed.minor) ||
        (localParsed.major === nextParsed.major && localParsed.minor === nextParsed.minor && localParsed.patch > nextParsed.patch);
      if (localIsAhead) {
        console.error(
          `ERROR: Auto-bump would downgrade from ${snapshotVersion} to ${nextSnapshot}. ` +
          `The local version is already ahead of the next patch after ${lastReleaseTag || "last release"}. ` +
          "Use 'node bin/version.mjs bump <major|minor|patch>' to bump manually."
        );
        process.exit(1);
      }
    }
  }

  // Changes summary
  const sinceRef = lastReleaseTag || getBaseBranch() || "HEAD~10";
  let commitLog = "", fileStat = "";
  try {
    commitLog = execSync(`git log --oneline ${sinceRef}..HEAD`, {
      cwd: REPO_ROOT, stdio: ["ignore", "pipe", "ignore"],
    }).toString().trim();
  } catch { /* ignore */ }
  try {
    fileStat = execSync(`git diff --stat ${sinceRef}..HEAD`, {
      cwd: REPO_ROOT, stdio: ["ignore", "pipe", "ignore"],
    }).toString().trim();
  } catch { /* ignore */ }

  // ================================================================
  // PHASE 2 — Display
  // ================================================================

  console.log("");
  console.log("══════════════════════════════════════════════");
  console.log("           AUTO-BUMP — Plan");
  console.log("══════════════════════════════════════════════");
  console.log("");

  console.log("┌─ Last Release");
  if (lastReleaseTag) {
    console.log(`│  GitHub:  ${lastReleaseTag}  (version=${lastReleaseBackend})`);
  } else {
    console.log(`│  GitHub:  (no release found)`);
  }
  console.log(`│  Local:   ${snapshotVersion}`);
  console.log("");

  console.log("┌─ Proposed Version Bump (next patch after last release)");
  if (backendChanged) {
    console.log(`│  Unified: ${snapshotVersion}  →  ${nextSnapshot}`);
  } else {
    console.log(`│  Unified: ${snapshotVersion}  (already at next after ${lastReleaseTag || "last release"})`);
  }
  console.log("");

  console.log("┌─ Changes Since Last Release");
  if (sinceRef && (commitLog || fileStat)) {
    printGitSummary("Commits", commitLog, 15);
    if (fileStat) {
      console.log("");
      printGitSummary("Files", fileStat, 12);
    }
  } else {
    console.log("  (could not determine change set)");
  }
  console.log("");

  // ================================================================
  // PHASE 3 — Act
  // ================================================================

  if (!commit) {
    console.log("══════════════════════════════════════════════");
    console.log("  DRY-RUN — No changes have been made.");
    if (!dryRun) {
      console.log("  Add --commit to apply the bump, commit, and push.");
    }
    console.log("══════════════════════════════════════════════");
    console.log("");
    return;
  }

  if (!backendChanged) {
    console.log("Nothing to bump — version is already up to date.");
    return;
  }

  const ok = await confirm("Proceed with auto-bump? [Y/n] ");
  if (!ok) {
    console.log("Auto-bump cancelled.");
    process.exit(0);
  }
  console.log("");

  // ---- Apply bump ----
  writeFileSync(join(REPO_ROOT, "VERSION"), nextSnapshot + "\n");

  try {
    mavenSetVersion(nextSnapshot);
  } catch {
    console.error("Maven versions:set failed. Reverting VERSION file and any modified pom.xml files.");
    revertVersionChanges(snapshotVersion);
    process.exit(1);
  }

  setPomTag(`v${nextBackend}`);

  // ---- Summary ----
  console.log(`\nAuto-bump complete: ${snapshotVersion} -> ${nextSnapshot}`);

  // ---- Commit & push ----
  const msg = `Auto-bump version to ${nextSnapshot}`;
  try {
    execSync("git add .", { cwd: REPO_ROOT, stdio: "inherit" });
    execSync(`git commit -m "${msg}"`, { cwd: REPO_ROOT, stdio: "inherit" });
    execSync("git push", { cwd: REPO_ROOT, stdio: "inherit" });
    console.log("Changes committed and pushed to remote.");
  } catch (e) {
    console.error("Git operation failed:", e.message);
    process.exit(1);
  }
}

// ---------------------------------------------------------------------------
// Subcommand: check
// ---------------------------------------------------------------------------

function cmdCheck() {
  console.log("Version Consistency Check");
  console.log("==========================");
  console.log("");

  let allPassed = true;

  // 1. VERSION file
  const versionPath = join(REPO_ROOT, "VERSION");
  let versionFileVersion = "";
  if (existsSync(versionPath)) {
    versionFileVersion = readFileSync(versionPath, "utf-8").trim();
    checkItem("VERSION", "passed", versionFileVersion);
  } else {
    checkItem("VERSION", "error", "VERSION file not found");
    allPassed = false;
  }

  // 2. Root pom.xml <version> (project version, not parent)
  const pomPath = join(REPO_ROOT, "pom.xml");
  let pomVersion = "";
  if (existsSync(pomPath)) {
    pomVersion = readPomProjectVersion(pomPath);
    if (pomVersion === null) {
      checkItem("pom.xml", "error", "Cannot parse <version>");
      allPassed = false;
    } else if (pomVersion === versionFileVersion) {
      checkItem("pom.xml", "passed", pomVersion);
    } else {
      checkItem("pom.xml", "failed", `${pomVersion} (expected ${versionFileVersion})`);
      allPassed = false;
    }
  } else {
    checkItem("pom.xml", "error", "File not found");
    allPassed = false;
  }

  // 3. SNAPSHOT consistency
  const versionIsSnapshot = versionFileVersion.endsWith("-SNAPSHOT");
  const pomIsSnapshot = pomVersion.endsWith("-SNAPSHOT");
  if (pomVersion && versionIsSnapshot !== pomIsSnapshot) {
    checkItem("SNAPSHOT consistency", "failed", "VERSION and pom.xml disagree on SNAPSHOT status");
    allPassed = false;
  } else {
    checkItem("SNAPSHOT consistency", "passed", versionIsSnapshot ? "SNAPSHOT" : "RELEASE");
  }

  // 4. All reactor pom.xml files match VERSION
  checkAllPomVersions(versionFileVersion);
  if (process.exitCode === 1) allPassed = false;

  // 5. pulsar-parent stays independent (informational)
  const parentPom = join(REPO_ROOT, "pulsar-parent", "pom.xml");
  if (existsSync(parentPom)) {
    checkItem("pulsar-parent/pom.xml", "skipped", `${readPomProjectVersion(parentPom)} (versioned independently)`);
  }

  console.log("");
  if (allPassed) {
    console.log("✓ All version checks passed.");
  } else {
    console.log("✗ Some version checks failed.");
    process.exitCode = 1;
  }
}

// ---------------------------------------------------------------------------
// Subcommand: prerelease-check (used by trigger-release.ps1)
// ---------------------------------------------------------------------------

function cmdPrereleaseCheck() {
  const result = {
    consistent: true,
    isNextPatch: false,
    lastRelease: null,
    currentVersion: null,
    expectedNextPatch: null,
    issues: [],
  };

  // ── Phase 1: File-level version consistency ─────────────────────────
  const versionFileVersion = readBackendVersion();
  const cliVersion = stripSnapshot(versionFileVersion);
  result.currentVersion = cliVersion;

  // Check root pom.xml version
  const pomPath = join(REPO_ROOT, "pom.xml");
  if (existsSync(pomPath)) {
    const pomVersion = readPomProjectVersion(pomPath);
    if (pomVersion === null) {
      result.consistent = false;
      result.issues.push("pom.xml: cannot parse <version>");
    } else if (pomVersion !== versionFileVersion) {
      result.consistent = false;
      result.issues.push(`pom.xml: "${pomVersion}" (expected "${versionFileVersion}")`);
    }
  } else {
    result.consistent = false;
    result.issues.push("pom.xml: file not found");
  }

  // Check all reactor pom.xml files (skip those inheriting their version)
  const pomFiles = getReactorPoms();
  for (const pomFile of pomFiles) {
    const v = readPomProjectVersion(pomFile);
    if (v === null) continue; // inherits from parent
    if (v !== versionFileVersion) {
      result.consistent = false;
      result.issues.push(`${relative(REPO_ROOT, pomFile)}: "${v}" (expected "${versionFileVersion}")`);
    }
  }

  // ── Phase 2: Next-patch check against last release ──────────────────
  const lastReleaseTag = getLatestReleaseTag();
  result.lastRelease = lastReleaseTag;

  if (lastReleaseTag) {
    const lastBase = lastReleaseTag.replace(/^v/, "").replace(/-.*$/, "");
    const bumpResult = checkVersionBump(lastBase, cliVersion);

    result.isNextPatch = bumpResult.ok && !bumpResult.note;

    if (!result.isNextPatch) {
      if (bumpResult.reason) {
        result.issues.push(bumpResult.reason);
      } else if (bumpResult.note) {
        result.issues.push(`Version ${cliVersion} is already published as ${lastReleaseTag}`);
      }

      const lastParsed = parseSemver(lastBase);
      if (lastParsed) {
        result.expectedNextPatch = `${lastParsed.major}.${lastParsed.minor}.${lastParsed.patch + 1}`;
      }
    }
  } else {
    // No previous release found — first release is always OK
    result.isNextPatch = true;
  }

  // ── Output ───────────────────────────────────────────────────────────
  console.log(JSON.stringify(result, null, 2));

  if (!result.consistent || !result.isNextPatch) {
    process.exit(1);
  }
}

// ---------------------------------------------------------------------------
// Main: parse subcommand
// ---------------------------------------------------------------------------

function printUsage() {
  console.log("Usage: node bin/version.mjs <command> [options]");
  console.log("");
  console.log("Browser4base uses a single unified version (VERSION file) across all Maven modules.");
  console.log("The independently-versioned pulsar-parent module is never touched.");
  console.log("");
  console.log("  Version queries");
  console.log("    show              Print project version");
  console.log("    show -v           Print version + git hash, branch, date");
  console.log("");
  console.log("  Version changes");
  console.log("    release           Strip -SNAPSHOT / -rc.N to finalize a release");
  console.log("    bump <part>       Bump major/minor/patch, update pom.xml, commit");
  console.log("    bump rc           Create or increment an -rc.N candidate (rc.1, rc.2, ...)");
  console.log("    bump <part> --dry-run    Show what would change without applying");
  console.log("    bump <part> --skip-precheck  Skip publish-status verification");
  console.log("    auto              Show bump plan (dry-run by default)");
  console.log("    auto --dry-run    Same as default (explicit dry-run)");
  console.log("    auto --commit     Apply bump, commit, and push");
  console.log("");
  console.log("  Full sync (VERSION → all pom.xml files)");
  console.log("    sync              Sync VERSION to all pom.xml files via Maven versions:set");
  console.log("    sync --check      Check-only mode (exit 1 if out of sync)");
  console.log("    sync --dry-run    Show what would change without applying");
  console.log("");
  console.log("  Cross-cutting");
  console.log("    check             Check version consistency across all files");
  console.log("    prerelease-check  Check consistency + next-patch guard (JSON output)");
}

const args = process.argv.slice(2);

if (args.length === 0 || args[0] === "-h" || args[0] === "--help") {
  printUsage();
  process.exit(0);
}

const command = args[0];
const rest = args.slice(1);

switch (command) {
  case "show":
    cmdShow(rest);
    break;
  case "release":
    cmdRelease();
    break;
  case "sync":
    cmdSync(rest);
    break;
  case "bump":
    await cmdBump(rest);
    break;
  case "auto":
    await cmdAuto(rest);
    break;
  case "check":
    cmdCheck();
    break;
  case "prerelease-check":
    cmdPrereleaseCheck();
    break;
  default:
    console.error(`Unknown command: ${command}`);
    console.error("");
    printUsage();
    process.exit(1);
}
