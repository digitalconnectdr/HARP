#!/usr/bin/env python3
"""Apply HARP context-blueprint patches to a local source checkout.

HARP's research *.patch files intentionally use context-only hunks ("@@")
while the implementation is still being staged. This tool makes those
blueprints executable without pretending they are standard unified diffs.

For every hunk:
  old block = context + removed lines
  new block = context + added lines

The old block must occur exactly once in the target file. Any missing or
ambiguous anchor is a hard failure.
"""

from __future__ import annotations

import argparse
import dataclasses
import pathlib
import subprocess
import sys
from typing import Iterable


@dataclasses.dataclass
class Hunk:
    path: str
    old_lines: list[str]
    new_lines: list[str]


def parse_blueprint(path: pathlib.Path) -> list[Hunk]:
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    hunks: list[Hunk] = []
    target: str | None = None
    i = 0

    while i < len(lines):
        line = lines[i]
        if line.startswith("diff --git a/"):
            parts = line.rstrip("\n").split()
            if len(parts) != 4 or not parts[3].startswith("b/"):
                raise ValueError(f"{path}: malformed diff header: {line!r}")
            target = parts[3][2:]
            i += 1
            continue

        if line.startswith("@@"):
            if target is None:
                raise ValueError(f"{path}: hunk before diff header")
            i += 1
            old: list[str] = []
            new: list[str] = []
            while i < len(lines):
                hline = lines[i]
                if hline.startswith("@@") or hline.startswith("diff --git a/"):
                    break
                if hline.startswith("--- ") or hline.startswith("+++ "):
                    break
                if hline.startswith("+"):
                    new.append(hline[1:])
                elif hline.startswith("-"):
                    old.append(hline[1:])
                elif hline.startswith(" "):
                    old.append(hline[1:])
                    new.append(hline[1:])
                elif hline == "\n":
                    # Outside a real unified hunk this is metadata spacing.
                    break
                else:
                    # Metadata after a context-only hunk.
                    break
                i += 1

            if not old:
                raise ValueError(
                    f"{path}: {target}: insertion hunk has no context anchor"
                )
            hunks.append(Hunk(target, old, new))
            continue

        i += 1

    if not hunks:
        raise ValueError(f"{path}: no context hunks found")
    return hunks


def find_all(haystack: list[str], needle: list[str]) -> list[int]:
    if not needle or len(needle) > len(haystack):
        return []
    first = needle[0]
    hits: list[int] = []
    stop = len(haystack) - len(needle) + 1
    for i in range(stop):
        if haystack[i] == first and haystack[i : i + len(needle)] == needle:
            hits.append(i)
    return hits


def apply_hunks(
    checkout: pathlib.Path,
    hunks: Iterable[Hunk],
    *,
    check_only: bool,
) -> int:
    by_file: dict[str, list[Hunk]] = {}
    for h in hunks:
        by_file.setdefault(h.path, []).append(h)

    total = 0
    staged: dict[pathlib.Path, list[str]] = {}

    for rel, file_hunks in by_file.items():
        target = checkout / rel
        if not target.is_file():
            raise FileNotFoundError(f"missing target file: {target}")
        content = target.read_text(encoding="utf-8").splitlines(keepends=True)

        for h in file_hunks:
            hits = find_all(content, h.old_lines)
            if len(hits) != 1:
                raise RuntimeError(
                    f"{rel}: expected one anchor match, found {len(hits)}"
                )
            at = hits[0]
            content[at : at + len(h.old_lines)] = h.new_lines
            total += 1

        staged[target] = content

    if not check_only:
        for target, content in staged.items():
            target.write_text("".join(content), encoding="utf-8")

    return total


def git_diff_check(checkout: pathlib.Path) -> None:
    result = subprocess.run(
        ["git", "diff", "--check"],
        cwd=checkout,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        sys.stderr.write(result.stdout)
        sys.stderr.write(result.stderr)
        raise RuntimeError("git diff --check failed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("checkout", type=pathlib.Path)
    parser.add_argument("blueprints", nargs="+", type=pathlib.Path)
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate all anchors without writing files",
    )
    args = parser.parse_args()

    checkout = args.checkout.resolve()
    if not (checkout / ".git").exists():
        parser.error(f"{checkout} is not a Git checkout")

    all_hunks: list[Hunk] = []
    for bp in args.blueprints:
        parsed = parse_blueprint(bp.resolve())
        print(f"{bp}: {len(parsed)} hunks")
        all_hunks.extend(parsed)

    count = apply_hunks(checkout, all_hunks, check_only=args.check)
    if args.check:
        print(f"PASS_CONTEXT_BLUEPRINT_CHECK hunks={count}")
        return 0

    git_diff_check(checkout)
    print(f"PASS_CONTEXT_BLUEPRINT_APPLY hunks={count}")
    print("Review with: git diff --check && git diff")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
