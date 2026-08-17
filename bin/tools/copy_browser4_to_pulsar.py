#!/usr/bin/env python3
"""
Copy Kotlin files from Browser4 (Team2 fork) to browser4base,
transforming package namespace: ai.platon.browser4 -> ai.platon.pulsar

Usage:
    python copy_browser4_to_pulsar.py                  # execute all jobs
    python copy_browser4_to_pulsar.py --dry-run         # preview only
    python copy_browser4_to_pulsar.py --only "browser"  # run a specific job
    python copy_browser4_to_pulsar.py --validate        # verify no leftover references
    python copy_browser4_to_pulsar.py --verbose         # detailed per-file output
    python copy_browser4_to_pulsar.py --backup          # backup originals before overwriting

Sync modes (configured per-job in COPY_JOBS):
    overwrite     - Source always overwrites destination (legacy behavior).
    sync_existing - Only sync files whose mapped destination already exists.
                    Source-only files/dirs are silently skipped.
    keep_existing - Never overwrite existing destination files.
                    Only copies new files from source that don't exist in dest.

Jobs:
    browser     browser4-browser/src -> pulsar-browser/src      [overwrite]
    protocol    browser4-protocol/src -> pulsar-protocol/src    [overwrite]
    skeleton    browser4-skeleton/src -> pulsar-skeleton/src    [overwrite]
"""

import argparse
import os
import re
import shutil
import sys
import time
from pathlib import Path


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

SOURCE_BASE = Path(r"D:\workspace\Browser4\Browser4")
DEST_BASE = Path(r"D:\workspace\Browser4\browser4base")

OLD_NS = "ai.platon.browser4"
NEW_NS = "ai.platon.pulsar"

OLD_PORT = "8182"
NEW_PORT = "8082"

OLD_MOCK_PORT = "18080"
NEW_MOCK_PORT = "17080"

TEXT_EXTENSIONS = {
    ".kt", ".kts", ".java", ".xml", ".properties", ".yaml", ".yml",
    ".json", ".txt", ".gradle", ".cfg", ".conf",
}

SKIP_NAMES = {".git", ".svn", "__pycache__", ".idea", "node_modules", "target"}

# ---------------------------------------------------------------------------
# Class name transformations applied AFTER package namespace replacement.
# These handle classes that were renamed during the Browser4 -> Pulsar
# migration (e.g. B4ResourceLoader -> ResourceLoader).
# ---------------------------------------------------------------------------

CLASS_RENAMES = {
    # Browser4AutoConfiguration was renamed to PulsarAutoConfiguration
    "Browser4AutoConfiguration": "PulsarAutoConfiguration",
    # B4ResourceLoader was renamed to ResourceLoader
    "B4ResourceLoader": "ResourceLoader",
    # AgenticSession was replaced by PulsarSession (also see FQ_CLASS_RENAMES
    # for the full package migration: ai.platon.pulsar.agentic.* → core.api)
    "AgenticSession": "PulsarSession",
    # AgenticContexts was replaced by PulsarContexts (package also changed:
    # ai.platon.pulsar.agentic.context → ai.platon.pulsar.skeleton.context)
    "AgenticContexts": "PulsarContexts",
}

# Fully-qualified class references that were renamed during migration.
# These are applied AFTER namespace replacement (ai.platon.browser4 → ai.platon.pulsar),
# so the keys use the NEW_NS (ai.platon.pulsar).
FQ_CLASS_RENAMES = {
    # AgenticSession was moved from agentic package to core.api package
    "ai.platon.pulsar.agentic.AgenticSession": "ai.platon.pulsar.core.api.PulsarSession",
    # AgenticContexts was moved from agentic.context to skeleton.context
    "ai.platon.pulsar.agentic.context.AgenticContexts": "ai.platon.pulsar.skeleton.context.PulsarContexts",
}

# ---------------------------------------------------------------------------
# Post-copy fixups: regex patterns applied to .kt files after namespace
# transformation. Each is a (pattern, replacement) pair.
# ---------------------------------------------------------------------------

POST_FIXUPS = [
    # Fix circular import alias: when the same-named interface from the same
    # package is imported under an alias, remove the redundant import and
    # replace the alias with the direct class name.
    (
        re.compile(
            r'import ' + re.escape(NEW_NS) + r'\.(\w+) as Pulsar\1Shim\n'
        ),
        '',
    ),
    (
        re.compile(r'Pulsar(\w+)Shim'),
        r'\1',
    ),
]

# ---------------------------------------------------------------------------
# Copy jobs
# ---------------------------------------------------------------------------

# Files that are Browser4-specific and must NEVER be synced into pulsar upstream.
# These types are the extension point of the Team2 fork (Browser4); publishing them
# inside pulsar-browser creates same-FQCN duplicate classes with the fork's own
# browser4-browser sources (see 4f17ae50b which wrongly added them upstream).
# Paths are relative to the browser job's source_root (browser4-browser/src).
BROWSER4_SPECIFIC_FILES = [
    "main/kotlin/ai/platon/pulsar/chrome/Browser4WebDriver.kt",
    "main/kotlin/ai/platon/pulsar/api/snapshot/ViewportRanges.kt",
    "test/kotlin/ai/platon/pulsar/chrome/Browser4WebDriverTest.kt",
    "test/kotlin/ai/platon/pulsar/api/snapshot/ViewportRangesTest.kt",
]

COPY_JOBS = {
    "browser": {
        "name": "browser4-browser -> pulsar-browser",
        "source_root": SOURCE_BASE / "browser4-core" / "browser4-browser" / "src",
        "dest_root": DEST_BASE / "pulsar-core" / "pulsar-browser" / "src",
        "sync_mode": "overwrite",
        # Browser4WebDriver / ViewportRanges (and their tests) are Browser4-specific
        # extension points — they must not be copied into pulsar upstream, otherwise
        # the published pulsar-browser jar contains same-FQCN duplicates of the fork's
        # own classes.
        "skip_paths": BROWSER4_SPECIFIC_FILES,
    },
    "protocol": {
        "name": "browser4-protocol -> pulsar-protocol",
        "source_root": SOURCE_BASE / "browser4-core" / "browser4-protocol" / "src",
        "dest_root": DEST_BASE / "pulsar-core" / "pulsar-protocol" / "src",
        "sync_mode": "overwrite",
        "skip_paths": [],
    },
    "skeleton": {
        "name": "browser4-skeleton -> pulsar-skeleton",
        "source_root": SOURCE_BASE / "browser4-core" / "browser4-skeleton" / "src",
        "dest_root": DEST_BASE / "pulsar-core" / "pulsar-skeleton" / "src",
        "sync_mode": "overwrite",
        "skip_paths": [],
    },
}

# ---------------------------------------------------------------------------
# Cleanup paths: directories to remove from destination
# ---------------------------------------------------------------------------

CLEANUP_PATHS = [
    DEST_BASE / "pulsar-core" / "pulsar-browser" / "src" / "main" / "kotlin" / "ai" / "platon" / "browser4",
    DEST_BASE / "pulsar-core" / "pulsar-browser" / "src" / "test" / "kotlin" / "ai" / "platon" / "browser4",
    DEST_BASE / "pulsar-core" / "pulsar-protocol" / "src" / "main" / "kotlin" / "ai" / "platon" / "browser4",
    DEST_BASE / "pulsar-core" / "pulsar-protocol" / "src" / "test" / "kotlin" / "ai" / "platon" / "browser4",
    DEST_BASE / "pulsar-core" / "pulsar-skeleton" / "src" / "main" / "kotlin" / "ai" / "platon" / "browser4",
    DEST_BASE / "pulsar-core" / "pulsar-skeleton" / "src" / "test" / "kotlin" / "ai" / "platon" / "browser4",
]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def is_text_file(filepath):
    return filepath.suffix.lower() in TEXT_EXTENSIONS


def is_skipped(rel_path, skip_paths):
    rel_str = rel_path.as_posix()
    for skip in skip_paths:
        skip_norm = skip.replace("\\", "/").rstrip("/") + "/"
        if rel_str.startswith(skip_norm) or rel_str == skip.replace("\\", "/"):
            return True
    return False


def is_wrapper_shim(filepath):
    """Detect if a .kt file is a compatibility wrapper shim.

    A wrapper shim lives in the pulsar namespace but extends/implements
    a type from the browser4 namespace. Files like:
        package ai.platon.pulsar.api
        interface Browser : ai.platon.browser4.api.Browser
    """
    if filepath.suffix.lower() != ".kt":
        return False
    # Quick heuristic: only check files in pulsar namespace paths
    rel = filepath.as_posix()
    if "/ai/platon/pulsar/" not in rel:
        return False
    try:
        content = filepath.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return False
    # Wrapper: declares pulsar namespace, extends/implements browser4 type
    has_pulsar_pkg = NEW_NS in content[:1000]  # package is near the top
    has_browser4_ref = OLD_NS in content
    if not (has_pulsar_pkg and has_browser4_ref):
        return False
    # Confirm the interface/class line extends a browser4 type
    return bool(re.search(
        r'(?:interface|class|object)\s+\w+\s*:\s*' + re.escape(OLD_NS),
        content
    ))


def map_dest_path(rel_path):
    """Map source-relative path to destination-relative path, replacing
    the 'browser4' package directory component with 'pulsar'."""
    parts = list(rel_path.parts)
    new_parts = []
    for i, part in enumerate(parts):
        if part == "browser4":
            if i >= 2 and parts[i - 2] == "ai" and parts[i - 1] == "platon":
                new_parts.append("pulsar")
                continue
        new_parts.append(part)
    return Path(*new_parts)


def build_dest_file_set(dest_root):
    """Walk the destination root and return a set of all relative file paths.

    Used by sync_existing and keep_existing modes to check whether a
    mapped destination file already exists.
    """
    file_set = set()
    if not dest_root.exists():
        return file_set
    for root, dirs, files in os.walk(str(dest_root)):
        dirs[:] = [d for d in dirs if d not in SKIP_NAMES]
        for filename in files:
            if filename in SKIP_NAMES:
                continue
            rel = (Path(root) / filename).relative_to(dest_root)
            file_set.add(rel.as_posix())
    return file_set


def transform_content(content, filepath):
    """Apply all transformations: namespace, class renames, post-fixups."""
    changed = 0

    if OLD_NS in content:
        count = content.count(OLD_NS)
        content = content.replace(OLD_NS, NEW_NS)
        changed += count

    if OLD_PORT in content:
        count = content.count(OLD_PORT)
        content = content.replace(OLD_PORT, NEW_PORT)
        changed += count

    if OLD_MOCK_PORT in content:
        count = content.count(OLD_MOCK_PORT)
        content = content.replace(OLD_MOCK_PORT, NEW_MOCK_PORT)
        changed += count

    for old_class, new_class in CLASS_RENAMES.items():
        if old_class in content:
            count = content.count(old_class)
            content = content.replace(old_class, new_class)
            changed += count

    for old_fq, new_fq in FQ_CLASS_RENAMES.items():
        if old_fq in content:
            count = content.count(old_fq)
            content = content.replace(old_fq, new_fq)
            changed += count

    for pattern, replacement in POST_FIXUPS:
        if pattern.search(content):
            count = len(pattern.findall(content))
            content = pattern.sub(replacement, content)
            changed += count

    return content, changed


# ---------------------------------------------------------------------------
# File operations
# ---------------------------------------------------------------------------

def copy_file(src, dest, dry_run, backup, stats, verbose):
    """Copy a single file with transformation and optional backup."""
    ext = src.suffix.lower()

    # Handle .kt files with full transformation pipeline
    if ext == ".kt":
        try:
            content = src.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            return _binary_copy(src, dest, dry_run, backup, stats, verbose)

        new_content, changes = transform_content(content, src)
        _write_text_file(src, dest, new_content, changes, dry_run, backup, stats, verbose)
        return

    # Handle other text files
    if is_text_file(src):
        try:
            content = src.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            return _binary_copy(src, dest, dry_run, backup, stats, verbose)

        new_content, changes = transform_content(content, src)
        _write_text_file(src, dest, new_content, changes, dry_run, backup, stats, verbose)
        return

    # Binary files
    _binary_copy(src, dest, dry_run, backup, stats, verbose)


def _binary_copy(src, dest, dry_run, backup, stats, verbose=False):
    if verbose:
        print(f"  [binary] {src.name}")
    if not dry_run:
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
    stats["binary_copied"] += 1


def _write_text_file(src, dest, content, changes, dry_run, backup, stats, verbose):
    if changes > 0:
        label = f"transformed ({changes})"
    else:
        label = "unchanged"

    if verbose:
        print(f"  [{label}] {src.name}")
    elif changes > 0:
        print(f"  [{label}] {src.name}")

    if not dry_run:
        if backup and dest.exists():
            backup_path = dest.with_suffix(dest.suffix + ".b4bak")
            shutil.copy2(dest, backup_path)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")

    if changes > 0:
        stats["transformed"] += 1
    else:
        stats["copied_unchanged"] += 1


# ---------------------------------------------------------------------------
# Cleanup
# ---------------------------------------------------------------------------

def cleanup_destination(dry_run, verbose=False):
    """Remove unrelevant test directories from the destination."""
    print(f"\n{'=' * 70}")
    print("Cleanup: removing unrelevant paths from destination")
    print(f"{'=' * 70}")

    removed = 0
    for path in CLEANUP_PATHS:
        if path.exists():
            print(f"  [remove] {path.relative_to(DEST_BASE)}")
            if not dry_run:
                shutil.rmtree(path)
            removed += 1
        elif verbose:
            print(f"  [skip]   {path.relative_to(DEST_BASE)} (not found)")

    print(f"\n  Removed {removed} directories")
    return removed


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

def validate_destination():
    """Check destination for any remaining browser4 references or directories."""
    print(f"\n{'=' * 70}")
    print("Validation: checking for remaining ai.platon.browser4 references")
    print(f"{'=' * 70}")

    issues = []

    # Check for browser4 in .kt files
    for module in ["pulsar-core/pulsar-browser"]:
        src_dir = DEST_BASE / module / "src"
        if not src_dir.exists():
            continue
        for kt_file in src_dir.rglob("*.kt"):
            if "target" in str(kt_file):
                continue
            try:
                content = kt_file.read_text(encoding="utf-8")
                if OLD_NS in content:
                    lines = [f"  line {i + 1}: {l.strip()[:120]}"
                             for i, l in enumerate(content.splitlines())
                             if OLD_NS in l]
                    issues.append(f"[REF] {kt_file.relative_to(DEST_BASE)}:\n" +
                                  "\n".join(lines[:5]))
            except Exception:
                pass

    # Check for browser4 directories
    for root_str in ["pulsar-core/pulsar-browser/src"]:
        for dirpath in (DEST_BASE / root_str).rglob("**/ai/platon/browser4*"):
            if dirpath.is_dir():
                issues.append(f"[DIR] {dirpath.relative_to(DEST_BASE)}")

    if issues:
        print(f"  Found {len(issues)} issue(s):")
        for issue in issues:
            print(f"  {issue}")
        return 1
    else:
        print("  No browser4 references or directories found.")
        return 0


# ---------------------------------------------------------------------------
# Job processing
# ---------------------------------------------------------------------------

def process_job(job, dry_run, backup, verbose):
    """Process a single copy job.

    Supports three sync_mode values:
      - "overwrite":      Source always overwrites dest (legacy behavior).
      - "sync_existing":  Only sync files whose mapped dest already exists.
      - "keep_existing":  Skip source files whose mapped dest already exists.
    """
    name = job["name"]
    source_root = job["source_root"]
    dest_root = job["dest_root"]
    sync_mode = job.get("sync_mode", "overwrite")
    skip_paths = job.get("skip_paths", [])

    stats = {
        "transformed": 0,
        "copied_unchanged": 0,
        "binary_copied": 0,
        "skipped": 0,
        "wrapper_skipped": 0,
        "sync_skipped": 0,       # skipped due to sync_mode (source-only or existing)
        "errors": 0,
    }

    # For sync_existing / keep_existing modes, pre-compute the set of all
    # existing destination files so we can check membership quickly.
    dest_file_set = None
    if sync_mode in ("sync_existing", "keep_existing"):
        dest_file_set = build_dest_file_set(dest_root)

    print(f"\n{'=' * 70}")
    print(f"Job: {name}")
    print(f"  Source:     {source_root}")
    print(f"  Dest:       {dest_root}")
    print(f"  Sync mode:  {sync_mode}")
    if skip_paths:
        print(f"  Exclude paths: {skip_paths}")
    print(f"{'=' * 70}")

    if not source_root.exists():
        print(f"  [ERROR] Source root does not exist: {source_root}")
        stats["errors"] += 1
        return stats

    if not dry_run:
        dest_root.mkdir(parents=True, exist_ok=True)

    for root, dirs, files in os.walk(source_root):
        dirs[:] = [d for d in dirs if d not in SKIP_NAMES]

        root_path = Path(root)
        rel_dir = root_path.relative_to(source_root)

        if is_skipped(rel_dir, skip_paths):
            dirs[:] = []
            continue

        for filename in files:
            if filename in SKIP_NAMES:
                continue

            src_file = root_path / filename
            rel_file = src_file.relative_to(source_root)

            # Auto-detect and skip wrapper shims
            if src_file.suffix.lower() == ".kt" and is_wrapper_shim(src_file):
                if verbose:
                    print(f"  [skip wrapper] {src_file.name}")
                stats["wrapper_skipped"] += 1
                continue

            dest_rel = map_dest_path(rel_file)
            dest_file = dest_root / dest_rel

            # --- Sync mode filtering ---
            dest_rel_posix = dest_rel.as_posix()

            if sync_mode == "sync_existing":
                # Only copy if the mapped destination already exists
                if dest_rel_posix not in dest_file_set:
                    stats["sync_skipped"] += 1
                    continue

            elif sync_mode == "keep_existing":
                # Skip if the destination already exists (keep dest version)
                if dest_rel_posix in dest_file_set:
                    stats["sync_skipped"] += 1
                    continue

            # --- Default: copy (for overwrite, or filtered-in files) ---
            try:
                copy_file(src_file, dest_file, dry_run, backup, stats, verbose)
            except Exception as e:
                print(f"  [ERROR] {rel_file}: {e}")
                stats["errors"] += 1

    # Print summary
    parts = [f"{stats['transformed']} transformed",
             f"{stats['copied_unchanged']} unchanged",
             f"{stats['binary_copied']} binary"]
    if stats["wrapper_skipped"] > 0:
        parts.append(f"{stats['wrapper_skipped']} wrappers skipped")
    if stats["sync_skipped"] > 0:
        if sync_mode == "sync_existing":
            parts.append(f"{stats['sync_skipped']} source-only skipped")
        elif sync_mode == "keep_existing":
            parts.append(f"{stats['sync_skipped']} existing skipped")
    if stats["errors"] > 0:
        parts.append(f"{stats['errors']} errors")
    print(f"\n  Summary: {', '.join(parts)}")

    return stats


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Copy and transform Kotlin files from Browser4 to browser4base"
    )
    parser.add_argument("--dry-run", "-n", action="store_true",
                        help="Preview actions without writing files")
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Show every file processed (not just changed ones)")
    parser.add_argument("--backup", action="store_true",
                        help="Backup original files before overwriting (.b4bak)")
    parser.add_argument("--validate", action="store_true",
                        help="Only validate: check for remaining browser4 references")
    parser.add_argument("--only", metavar="JOB",
                        help="Run only the specified job (browser, protocol, skeleton)")
    args = parser.parse_args()

    # Validate-only mode
    if args.validate:
        sys.exit(validate_destination())

    dry_run = args.dry_run
    backup = args.backup
    verbose = args.verbose

    if dry_run:
        print("=" * 70)
        print("  DRY RUN MODE — no files will be written or deleted")
        print("=" * 70)

    if backup and not dry_run:
        print("Backup mode: originals will be saved with .b4bak extension")

    # Select jobs
    if args.only:
        if args.only not in COPY_JOBS:
            print(f"Error: unknown job '{args.only}'. "
                  f"Available: {', '.join(COPY_JOBS.keys())}")
            sys.exit(1)
        jobs_to_run = {args.only: COPY_JOBS[args.only]}
    else:
        jobs_to_run = COPY_JOBS

    start_time = time.time()
    grand_total = {
        "transformed": 0,
        "copied_unchanged": 0,
        "binary_copied": 0,
        "wrapper_skipped": 0,
        "sync_skipped": 0,
        "errors": 0,
    }

    for job in jobs_to_run.values():
        stats = process_job(job, dry_run, backup, verbose)
        for key in grand_total:
            grand_total[key] += stats.get(key, 0)

    # Cleanup
    cleaned = cleanup_destination(dry_run, verbose)

    elapsed = time.time() - start_time

    print(f"\n{'=' * 70}")
    print(f"  GRAND TOTAL")
    print(f"  Transformed:         {grand_total['transformed']}")
    print(f"  Copied unchanged:    {grand_total['copied_unchanged']}")
    print(f"  Binary copies:       {grand_total['binary_copied']}")
    print(f"  Wrappers skipped:    {grand_total['wrapper_skipped']}")
    print(f"  Sync skipped:        {grand_total['sync_skipped']}")
    print(f"  Directories cleaned: {cleaned}")
    print(f"  Errors:              {grand_total['errors']}")
    print(f"  Time:                {elapsed:.2f}s")
    print(f"{'=' * 70}")

    if dry_run:
        print("\n  This was a dry run. Run without --dry-run to execute.")

    if grand_total["errors"] > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
