---
name: sync-protocol-documents
description: Mirrors "JSON-based BLE protocol.md" from the Obsidian vault into this repo's PROTOCOL.md, stripping Obsidian-only syntax (frontmatter, callout spacing) and normalizing tables for GitHub. Invoke with /sync-protocol-documents whenever the Obsidian source has changed and PROTOCOL.md needs to catch up.
---

# Sync protocol documents

Mirrors the Obsidian-authored protocol doc into `PROTOCOL.md` at the repo root. Content and wording are never altered here — only Obsidian-specific formatting is normalized for GitHub rendering.

- Source (Obsidian vault, outside this repo): `/Users/charlie/Documents/personal/cs/Side projects/PI ble connection/JSON-based BLE protocol.md`
- Destination (this repo): `PROTOCOL.md`

## What the sync does
1. Strips the YAML frontmatter block (Obsidian properties like `parent: "[[...]]"`) — meaningless outside the vault.
2. Converts Obsidian's callout syntax (`>[!IMPORTANT]`, no space) to GitHub's alert syntax (`> [!IMPORTANT]`, with a space) so it renders as an alert box on GitHub instead of a plain blockquote.
3. Reformats every markdown table to plain, unpadded GFM (`| --- | --- |` separators, no column-width alignment) — Obsidian pads tables to large fixed widths for its own editor, which just adds noise on GitHub.
4. Leaves everything else — headings, prose, code fences, mermaid diagrams, math, wording — untouched. Anything inside a fenced code block is skipped by the table/callout pass, so diagrams and code samples are never touched.
5. Prepends a one-line HTML comment noting the file is generated and where the source lives, so nobody hand-edits `PROTOCOL.md` directly.

## Steps to run
1. Run the sync script from the repo root:
   ```
   python3 .claude/skills/sync-protocol-documents/sync_protocol.py
   ```
2. Run `git diff -- PROTOCOL.md` and skim the result.
3. Report to the user whether `PROTOCOL.md` changed, and whether the diff looks like formatting-only (expected, from re-running the sync) or an actual content/wording change (expected too, whenever the Obsidian source itself was edited) — call out which one it was.
4. Do not commit automatically. Let the user review the diff and commit themselves.
