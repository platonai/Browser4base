#!/usr/bin/env python3
"""
Copy Kotlin files from Browser4 (Team2 fork) to browser4base,
transforming package namespace: ai.platon.browser4 -> ai.platon.pulsar

Usage:
    python copy_browser4_to_pulsar.py          # execute the copy
    python copy_browser4_to_pulsar.py --dry-run # preview only
"""

import os
import shutil
import sys
import time
from pathlib import Path


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

SOURCE_BASE = Path(r"D:\workspace\Browser4Team2\submodules\Browser4")
DEST_BASE = Path(r"D:\workspace\Browser4\browser4base")

# String to replace in file contents
OLD_NS = "ai.platon.browser4"
NEW_NS = "ai.platon.pulsar"

# Extensions considered "text" (eligible for content transformation).
TEXT_EXTENSIONS = {
    ".kt", ".kts", ".java", ".xml", ".properties", ".yaml", ".yml",
    ".json", ".txt", ".gradle", ".cfg", ".conf",
}

# File/directory names to skip during walk
SKIP_NAMES = {".git", ".svn", "__pycache__", ".idea", "node_modules"}

# ---------------------------------------------------------------------------
# Unrelevant test paths (relative to destination test src roots).
# These are NOT testing browser4-browser and should be excluded from copy
# AND deleted from the destination.
# ---------------------------------------------------------------------------

UNRELEVANT_IT_TEST_PATHS = [
    "test/kotlin/ai/platon/pulsar/basic",           # framework tests
    "test/kotlin/ai/platon/pulsar/basic/session",    # session tests
    "test/kotlin/ai/platon/pulsar/basic/component",  # component loading tests
    "test/kotlin/ai/platon/pulsar/basic/crawl",      # event handler tests
    "test/kotlin/ai/platon/pulsar/heavy/rest",       # REST API integration tests
    "test/kotlin/ai/platon/pulsar/ql",               # SQL engine tests (dest-only)
]

UNRELEVANT_E2E_TEST_PATHS = [
    "test/kotlin/ai/platon/pulsar/agentic",          # agentic AI tests
]

# ---------------------------------------------------------------------------
# Copy jobs: (name, source_root, dest_root, skip_patterns)
# skip_patterns are subdirectory paths (relative to source_root) to exclude
# ---------------------------------------------------------------------------

COPY_JOBS = [
    {
        "name": "browser4-browser -> pulsar-browser",
        "source_root": SOURCE_BASE / "browser4-core" / "browser4-browser" / "src",
        "dest_root": DEST_BASE / "pulsar-core" / "pulsar-browser" / "src",
        "skip_paths": [
            # Skip pulsar-namespace wrapper shims that extend browser4 classes.
            # These become circular after transformation and would overwrite the
            # correctly-transformed full files from the browser4/ directory.
            "main/kotlin/ai/platon/pulsar",
            "test/kotlin/ai/platon/pulsar",
        ],
    },
    {
        "name": "browser4-rest -> pulsar-it-tests (src/main)",
        "source_root": SOURCE_BASE / "browser4-rest" / "src" / "main",
        "dest_root": DEST_BASE / "pulsar-tests" / "pulsar-it-tests" / "src" / "main",
        "skip_paths": [],
    },
    {
        "name": "pulsar-it-tests (src/test)",
        "source_root": SOURCE_BASE / "browser4-tests" / "pulsar-it-tests" / "src" / "test",
        "dest_root": DEST_BASE / "pulsar-tests" / "pulsar-it-tests" / "src" / "test",
        "skip_paths": [
            "kotlin/ai/platon/pulsar/basic",           # framework tests
            "kotlin/ai/platon/pulsar/basic/session",    # session tests
            "kotlin/ai/platon/pulsar/basic/component",  # component loading tests
            "kotlin/ai/platon/pulsar/basic/crawl",      # event handler tests
            "kotlin/ai/platon/pulsar/heavy/rest",       # REST API integration tests
        ],
    },
    {
        "name": "pulsar-e2e-tests (src/test)",
        "source_root": SOURCE_BASE / "browser4-tests" / "pulsar-e2e-tests" / "src" / "test",
        "dest_root": DEST_BASE / "pulsar-tests" / "pulsar-e2e-tests" / "src" / "test",
        "skip_paths": [
            "kotlin/ai/platon/pulsar/agentic",          # agentic AI tests
        ],
    },
]

# ---------------------------------------------------------------------------
# Cleanup: directories to remove from destination (relative to dest roots).
# These contain tests not relevant to browser4-browser.
# ---------------------------------------------------------------------------

CLEANUP_PATHS = [
    # IT tests — unrelevant paths
    DEST_BASE / "pulsar-tests" / "pulsar-it-tests" / "src" / "test" / "kotlin" / "ai" / "platon" / "pulsar" / "basic",
    DEST_BASE / "pulsar-tests" / "pulsar-it-tests" / "src" / "test" / "kotlin" / "ai" / "platon" / "pulsar" / "heavy" / "rest",
    DEST_BASE / "pulsar-tests" / "pulsar-it-tests" / "src" / "test" / "kotlin" / "ai" / "platon" / "pulsar" / "ql",
    DEST_BASE / "pulsar-tests" / "pulsar-it-tests" / "src" / "test" / "kotlin" / "ai" / "platon" / "browser4",
    # E2E tests — unrelevant paths
    DEST_BASE / "pulsar-tests" / "pulsar-e2e-tests" / "src" / "test" / "kotlin" / "ai" / "platon" / "pulsar" / "agentic",
    DEST_BASE / "pulsar-tests" / "pulsar-e2e-tests" / "src" / "test" / "kotlin" / "ai" / "platon" / "browser4",
    # Old browser4 package directories in pulsar-browser (stale after package rename)
    DEST_BASE / "pulsar-core" / "pulsar-browser" / "src" / "main" / "kotlin" / "ai" / "platon" / "browser4",
    DEST_BASE / "pulsar-core" / "pulsar-browser" / "src" / "test" / "kotlin" / "ai" / "platon" / "browser4",
]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def is_text_file(filepath):
    """Check if a file extension is text-based (eligible for transformation)."""
    return filepath.suffix.lower() in TEXT_EXTENSIONS


def is_skipped(rel_path, skip_paths):
    """Check if a relative path should be skipped based on prefix patterns."""
    rel_str = rel_path.as_posix()
    for skip in skip_paths:
        skip_norm = skip.replace("\\", "/").rstrip("/") + "/"
        if rel_str.startswith(skip_norm) or rel_str == skip.replace("\\", "/"):
            return True
    return False


def map_dest_path(rel_path):
    """
    Map a source-relative path to a destination-relative path.
    Replaces 'browser4' directory component with 'pulsar' in the
    ai/platon/browser4 package directory path.
    """
    parts = list(rel_path.parts)
    new_parts = []
    for i, part in enumerate(parts):
        if part == "browser4":
            # Check if this is the package namespace directory
            # (preceded by 'ai/platon')
            if i >= 2 and parts[i-2] == "ai" and parts[i-1] == "platon":
                new_parts.append("pulsar")
                continue
        new_parts.append(part)
    return Path(*new_parts)


def transform_content(content, filepath):
    """
    Replace OLD_NS with NEW_NS in file content.
    Returns (transformed_content, replacement_count).
    """
    if OLD_NS in content:
        count = content.count(OLD_NS)
        return content.replace(OLD_NS, NEW_NS), count
    return content, 0


def copy_file(src, dest, dry_run, stats):
    """
    Copy a single file from src to dest.
    - .kt files: always read as text, transform content if needed, write
    - Other text files: read as text, transform if OLD_NS found, write
    - Binary files: copy directly
    """
    ext = src.suffix.lower()

    if ext == ".kt":
        try:
            content = src.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            print(f"  [WARN] Cannot read as UTF-8, binary copy: {src}")
            if not dry_run:
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dest)
            stats["binary_copied"] += 1
            return

        new_content, changes = transform_content(content, src)
        action = f"transformed ({changes} replacements)" if changes > 0 else "copied (no changes)"
        print(f"  [{action}] {src.name}")

        if not dry_run:
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(new_content, encoding="utf-8")

        if changes > 0:
            stats["transformed"] += 1
        else:
            stats["copied_unchanged"] += 1
        return

    if is_text_file(src):
        try:
            content = src.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            print(f"  [WARN] Cannot read as UTF-8, binary copy: {src}")
            if not dry_run:
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dest)
            stats["binary_copied"] += 1
            return

        new_content, changes = transform_content(content, src)
        if changes > 0:
            print(f"  [transformed ({changes} replacements)] {src.name}")
            if not dry_run:
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text(new_content, encoding="utf-8")
            stats["transformed"] += 1
        else:
            print(f"  [copied] {src.name}")
            if not dry_run:
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text(content, encoding="utf-8")
            stats["copied_unchanged"] += 1
        return

    # Binary file: direct copy
    print(f"  [binary copy] {src.name}")
    if not dry_run:
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)
    stats["binary_copied"] += 1


# ---------------------------------------------------------------------------
# Cleanup
# ---------------------------------------------------------------------------

def cleanup_destination(dry_run, stats):
    """Remove unrelevant test directories from the destination."""
    print(f"\n{'='*70}")
    print(f"Cleanup: removing unrelevant paths from destination")
    print(f"{'='*70}")

    removed = 0
    for path in CLEANUP_PATHS:
        if path.exists():
            print(f"  [remove] {path.relative_to(DEST_BASE)}")
            if not dry_run:
                shutil.rmtree(path)
            removed += 1
        else:
            print(f"  [skip]   {path.relative_to(DEST_BASE)} (not found)")

    stats["cleaned"] = removed
    print(f"\n  Removed {removed} directories")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def process_job(job, dry_run):
    """Process a single copy job."""
    name = job["name"]
    source_root = job["source_root"]
    dest_root = job["dest_root"]
    skip_paths = job.get("skip_paths", [])

    stats = {
        "transformed": 0,
        "copied_unchanged": 0,
        "binary_copied": 0,
        "skipped": 0,
        "errors": 0,
    }

    print(f"\n{'='*70}")
    print(f"Job: {name}")
    print(f"  Source: {source_root}")
    print(f"  Dest:   {dest_root}")
    if skip_paths:
        print(f"  Exclude: {skip_paths}")
    print(f"{'='*70}")

    if not source_root.exists():
        print(f"  [ERROR] Source root does not exist: {source_root}")
        stats["errors"] += 1
        return stats

    if not dry_run:
        dest_root.mkdir(parents=True, exist_ok=True)

    files_processed = 0
    for root, dirs, files in os.walk(source_root):
        dirs[:] = [d for d in dirs if d not in SKIP_NAMES]

        root_path = Path(root)
        rel_dir = root_path.relative_to(source_root)

        if is_skipped(rel_dir, skip_paths):
            dirs[:] = []  # prune subdirs too
            continue

        for filename in files:
            if filename in SKIP_NAMES:
                continue

            src_file = root_path / filename
            rel_file = src_file.relative_to(source_root)
            dest_rel = map_dest_path(rel_file)
            dest_file = dest_root / dest_rel

            try:
                copy_file(src_file, dest_file, dry_run, stats)
                files_processed += 1
            except Exception as e:
                print(f"  [ERROR] {rel_file}: {e}")
                stats["errors"] += 1

    print(f"\n  Summary: {stats['transformed']} transformed, "
          f"{stats['copied_unchanged']} copied unchanged, "
          f"{stats['binary_copied']} binary copies, "
          f"{stats['skipped']} skipped, "
          f"{stats['errors']} errors")

    return stats


def main():
    dry_run = "--dry-run" in sys.argv or "-n" in sys.argv

    if dry_run:
        print("=" * 70)
        print("  DRY RUN MODE — no files will be written or deleted")
        print("=" * 70)

    start_time = time.time()
    grand_total = {
        "transformed": 0,
        "copied_unchanged": 0,
        "binary_copied": 0,
        "skipped": 0,
        "errors": 0,
    }

    for job in COPY_JOBS:
        stats = process_job(job, dry_run)
        for key in grand_total:
            grand_total[key] += stats[key]

    # Cleanup unrelevant tests from destination
    cleanup_stats = {"cleaned": 0}
    cleanup_destination(dry_run, cleanup_stats)

    elapsed = time.time() - start_time

    print(f"\n{'='*70}")
    print(f"  GRAND TOTAL")
    print(f"  Transformed:       {grand_total['transformed']}")
    print(f"  Copied unchanged:  {grand_total['copied_unchanged']}")
    print(f"  Binary copies:     {grand_total['binary_copied']}")
    print(f"  Skipped:           {grand_total['skipped']}")
    print(f"  Directories cleaned: {cleanup_stats['cleaned']}")
    print(f"  Errors:            {grand_total['errors']}")
    print(f"  Time:              {elapsed:.2f}s")
    print(f"{'='*70}")

    if dry_run:
        print("\n  This was a dry run. Run without --dry-run to execute.")

    if grand_total["errors"] > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
