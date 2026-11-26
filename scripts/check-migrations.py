#!/usr/bin/env python3
"""Verify Flyway migration naming, ordering, and immutability against baseline."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

MIGRATION_DIR = Path("backend/src/main/resources/db/migration")
MIGRATION_RE = re.compile(r"^V(?!000)(\d{3})__([a-z][a-z0-9]*)_([a-z0-9]+(?:_[a-z0-9]+)*)\.sql$")


def run_git(repo: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args], cwd=repo, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check
    )


def ref_exists(repo: Path, ref: str) -> bool:
    return run_git(repo, "rev-parse", "--verify", "--quiet", ref, check=False).returncode == 0


def resolve_refs(repo: Path, explicit_base: str | None) -> tuple[str, str]:
    base_ref = explicit_base or "origin/dev"
    if not ref_exists(repo, base_ref):
        raise RuntimeError(f"unable to resolve baseline ref '{base_ref}'; fetch it or pass --base <ref>")
    if not ref_exists(repo, "HEAD"):
        raise RuntimeError("repository has no HEAD commit")

    head = run_git(repo, "rev-parse", "HEAD").stdout.strip()
    merge_base = run_git(repo, "merge-base", "HEAD", base_ref).stdout.strip()
    if merge_base != head:
        return merge_base, base_ref
    if explicit_base is None:
        dirty = run_git(repo, "status", "--porcelain", "--", str(MIGRATION_DIR)).stdout.strip()
        if not dirty and ref_exists(repo, "HEAD~1"):
            prev = run_git(repo, "rev-parse", "HEAD~1").stdout.strip()
            return prev, prev
    return head, base_ref


def list_tree_versions(repo: Path, ref: str) -> dict[str, int]:
    res = run_git(repo, "ls-tree", "-r", "--name-only", ref, "--", str(MIGRATION_DIR))
    versions: dict[str, int] = {}
    for rel in res.stdout.splitlines():
        if not rel.strip():
            continue
        match = MIGRATION_RE.match(Path(rel).name)
        versions[rel] = int(match.group(1)) if match else 0
    return versions


def check_repo(repo: Path, explicit_base: str | None = None) -> list[str]:
    errors: list[str] = []
    mig_root = repo / MIGRATION_DIR
    current_versions: dict[str, int] = {}
    seen_versions: dict[int, str] = {}

    if mig_root.exists():
        for path in sorted(mig_root.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(repo).as_posix()
            if path.parent != mig_root:
                errors.append(f"{rel}: nested subdirectories are not allowed under {MIGRATION_DIR}")
                continue
            match = MIGRATION_RE.match(path.name)
            if not match:
                errors.append(f"{rel}: invalid migration filename; expected V001__<module>_<description>.sql (V001+)")
                continue
            version = int(match.group(1))
            if version in seen_versions:
                errors.append(f"{rel}: duplicate migration version V{version:03d} (conflicts with {seen_versions[version]})")
            else:
                seen_versions[version] = rel
            current_versions[rel] = version

    try:
        diff_base, tip_ref = resolve_refs(repo, explicit_base)
    except RuntimeError as exc:
        errors.append(str(exc))
        return errors

    tampered: set[tuple[str, str]] = set()
    for extra in ([], ["--cached"]):
        diff = run_git(repo, "diff", *extra, "--no-renames", "--name-status", "--diff-filter=MD", diff_base, "--", str(MIGRATION_DIR))
        for line in diff.stdout.splitlines():
            if not line.strip():
                continue
            status, rel = line.split("\t", 1)
            tampered.add((status, rel))

    for status, rel in sorted(tampered):
        action = "deleted/renamed" if status == "D" else "modified"
        errors.append(f"{rel}: baseline migration was {action} (immutable once merged to {tip_ref})")

    tip_files = list_tree_versions(repo, tip_ref)
    max_tip_version = max(tip_files.values(), default=0)
    for rel, version in sorted(current_versions.items()):
        if rel not in tip_files and max_tip_version > 0 and version <= max_tip_version:
            errors.append(
                f"{rel}: new migration version V{version:03d} must be strictly greater than {tip_ref} max V{max_tip_version:03d}"
            )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Override baseline git ref (defaults to origin/dev)")
    args = parser.parse_args()

    errors = check_repo(Path(__file__).resolve().parent.parent, explicit_base=args.base)
    if errors:
        for err in errors:
            print(err, file=sys.stderr)
        print(f"migrations check failed: {len(errors)} error(s)", file=sys.stderr)
        return 1
    print("migrations check ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
