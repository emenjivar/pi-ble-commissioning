#!/usr/bin/env python3
"""Mirror the Obsidian-authored BLE protocol doc into this repo's PROTOCOL.md.

Strips Obsidian-only syntax (YAML frontmatter properties, callout spacing)
and normalizes table formatting for GitHub rendering. Wording/content is
never altered - fenced code blocks (including mermaid diagrams) are copied
through untouched.
"""
import re
import sys
from pathlib import Path

SOURCE = Path(
    "/Users/charlie/Documents/personal/cs/Side projects/PI ble connection/"
    "JSON-based BLE protocol.md"
)
DEST = Path(__file__).resolve().parents[3] / "PROTOCOL.md"

GENERATED_NOTICE = (
    "<!-- Synced from the Obsidian vault via /sync-protocol-documents. "
    "Edit the source there, not this file directly. -->\n\n"
)

SEP_RE = re.compile(r"^\s*\|[\s:|-]+\|\s*$")


def strip_frontmatter(text: str) -> str:
    if text.startswith("---\n"):
        end = text.find("\n---", 4)
        if end != -1:
            text = text[end + len("\n---"):]
    return text.lstrip("\n")


def split_row(row: str) -> list[str]:
    row = row.strip()
    if row.startswith("|"):
        row = row[1:]
    if row.endswith("|"):
        row = row[:-1]
    return [cell.strip() for cell in row.split("|")]


def render_table(block: list[str]) -> list[str]:
    header = split_row(block[0])
    rows = [split_row(r) for r in block[2:]]

    def render_row(cells: list[str]) -> str:
        return "| " + " | ".join(cells) + " |"

    lines = [render_row(header), "| " + " | ".join(["---"] * len(header)) + " |"]
    lines.extend(render_row(r) for r in rows)
    return lines


def process(text: str) -> str:
    lines = text.split("\n")
    out: list[str] = []
    in_fence = False
    i = 0
    while i < len(lines):
        line = lines[i]

        if line.strip().startswith("```"):
            in_fence = not in_fence
            out.append(line)
            i += 1
            continue

        if in_fence:
            out.append(line)
            i += 1
            continue

        # Obsidian allows ">text" with no space after the marker; GitHub
        # alerts/blockquotes read cleaner as "> text" (both are valid
        # commonmark, this is a style normalization only).
        line = re.sub(r"^(>+)(?!>)(?=\S)", r"\1 ", line)

        if line.strip().startswith("|") and i + 1 < len(lines) and SEP_RE.match(lines[i + 1]):
            block = [line]
            j = i + 1
            while j < len(lines) and lines[j].strip().startswith("|"):
                block.append(lines[j])
                j += 1
            out.extend(render_table(block))
            i = j
            continue

        out.append(line)
        i += 1

    return "\n".join(out)


def main() -> None:
    if not SOURCE.exists():
        sys.exit(f"Source not found: {SOURCE}")

    text = SOURCE.read_text()
    text = strip_frontmatter(text)
    text = process(text)
    text = GENERATED_NOTICE + text

    DEST.write_text(text)
    print(f"Synced:\n  {SOURCE}\n  -> {DEST}")


if __name__ == "__main__":
    main()
