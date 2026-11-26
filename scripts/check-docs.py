#!/usr/bin/env python3
"""Verify Markdown relative links, authoritative doc pointers, and stale path removal."""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PRUNE_DIRS = {".git", ".worktrees", "target", "node_modules"}
EXTERNAL_PREFIXES = ("http://", "https://", "mailto:", "file://", "#")
FENCE_OPEN_RE = re.compile(r"^\s*(`{3,}|~{3,})([^`]*)$")
INLINE_CODE_RE = re.compile(r"`([^`]+)`")
MD_LINK_RE = re.compile(r"!?\[[^\]]*\]\(([^)]*)\)")
STALE_PATH = "apps/backend"
# Planned paths explicitly cited in AGENTS.md before those modules are scaffolded.
AGENTS_PLANNED_PREFIXES = ("frontend/", "docs/adr/")
POINTER_FILES = ("AGENTS.md", "README.md", "docs/ARCHITECTURE.md")
POINTER_PREFIXES = ("docs/", "backend/", "frontend/", "scripts/", ".github/", ".agents/")
POINTER_EXACT = {
    "AGENTS.md",
    "CLAUDE.md",
    "CODING_STANDARDS.md",
    "GLOSSARY.md",
    "Makefile",
    "README.md",
    "docker-compose.yml",
    ".env.example",
    ".gitattributes",
    ".pre-commit-config.yaml",
}


def iter_markdown_files() -> list[Path]:
    files: list[Path] = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = sorted(d for d in dirnames if d not in PRUNE_DIRS)
        for name in sorted(filenames):
            if name.endswith(".md"):
                files.append(Path(dirpath) / name)
    return files


def iter_prose_lines(text: str) -> list[tuple[int, str]]:
    prose: list[tuple[int, str]] = []
    fence_char = ""
    fence_len = 0
    for lineno, line in enumerate(text.splitlines(), start=1):
        if not fence_char:
            match = FENCE_OPEN_RE.match(line)
            if match:
                marker, rest = match.group(1), match.group(2)
                if marker[0] == "~" or "`" not in rest:
                    fence_char, fence_len = marker[0], len(marker)
                    continue
            prose.append((lineno, line))
        else:
            stripped = line.lstrip()
            if stripped.startswith(fence_char * fence_len) and stripped.strip(fence_char).strip() == "":
                fence_char, fence_len = "", 0
    return prose


def check_stale_paths(md_file: Path, text: str, errors: list[str]) -> None:
    rel = md_file.relative_to(ROOT)
    for lineno, line in enumerate(text.splitlines(), start=1):
        if STALE_PATH in line:
            errors.append(f"{rel}:{lineno}: stale pre-refactor path '{STALE_PATH}'")


def check_markdown_links(md_file: Path, prose_lines: list[tuple[int, str]], errors: list[str]) -> None:
    rel = md_file.relative_to(ROOT)
    for lineno, line in prose_lines:
        stripped = INLINE_CODE_RE.sub("", line)
        for raw_target in MD_LINK_RE.findall(stripped):
            parts = raw_target.strip().strip("<>").split()
            if not parts:
                errors.append(f"{rel}:{lineno}: empty markdown link target")
                continue
            target = parts[0]
            if target.startswith(EXTERNAL_PREFIXES):
                continue
            if target.startswith("/"):
                errors.append(f"{rel}:{lineno}: root-relative link '{target}' not allowed")
                continue
            path_part = target.split("#", 1)[0].split("?", 1)[0]
            if not path_part:
                continue
            resolved = (md_file.parent / path_part).resolve()
            if not resolved.is_relative_to(ROOT):
                errors.append(f"{rel}:{lineno}: link target '{target}' escapes repository root")
            elif not resolved.exists():
                errors.append(f"{rel}:{lineno}: broken link target '{target}'")


def is_repo_pointer(token: str) -> bool:
    if any(ch in token for ch in "*?<>[]{}()") or any(ch.isspace() for ch in token):
        return False
    cleaned = token.rstrip("/")
    return cleaned in POINTER_EXACT or token.startswith(POINTER_PREFIXES)


def check_doc_pointers(errors: list[str]) -> None:
    claude = ROOT / "CLAUDE.md"
    if not claude.exists():
        errors.append("CLAUDE.md: missing repository instruction entry")
    else:
        for lineno, line in enumerate(claude.read_text(encoding="utf-8").splitlines(), start=1):
            stripped = line.strip()
            if stripped.startswith("@"):
                target = stripped[1:].strip()
                if not (ROOT / target).exists():
                    errors.append(f"CLAUDE.md:{lineno}: broken import pointer '{stripped}'")

    for rel_name in POINTER_FILES:
        doc_path = ROOT / rel_name
        if not doc_path.exists():
            errors.append(f"{rel_name}: missing authoritative document")
            continue
        prose_lines = iter_prose_lines(doc_path.read_text(encoding="utf-8"))
        for lineno, line in prose_lines:
            for token in INLINE_CODE_RE.findall(line):
                if not is_repo_pointer(token):
                    continue
                if rel_name == "AGENTS.md" and token.startswith(AGENTS_PLANNED_PREFIXES):
                    continue
                cleaned = token.rstrip("/")
                if not (ROOT / cleaned).exists():
                    errors.append(f"{rel_name}:{lineno}: broken doc pointer `{token}`")


def main() -> int:
    md_files = iter_markdown_files()
    errors: list[str] = []
    for md_file in md_files:
        text = md_file.read_text(encoding="utf-8")
        check_stale_paths(md_file, text, errors)
        check_markdown_links(md_file, iter_prose_lines(text), errors)
    check_doc_pointers(errors)

    if errors:
        for err in errors:
            print(err, file=sys.stderr)
        print(f"docs check failed: {len(errors)} error(s)", file=sys.stderr)
        return 1
    print(f"docs check ok: {len(md_files)} markdown files verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
