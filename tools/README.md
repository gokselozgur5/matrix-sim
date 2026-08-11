# tools/ — process instruments

Scripts that execute the process the documents promise. Nothing here is part
of the daemon build (D-009 still holds: the SIMULATION needs only `javac`);
these are the shop's own jigs, kept under D-030's rule that crew tooling is
part of the shop.

| Tool | What it does |
|---|---|
| `release.sh` | Cuts a phase release from `main` — refuses unless compile, `--selftest`, and `--bench` are green at cut time, then stamps their live output into the notes under your prose. Usage: `tools/release.sh vX.Y.Z "Title" notes.md` with notes written from `RELEASE_NOTES_TEMPLATE.md`. |

House rules:

- A tool never mutates `src/` or `docs/`; it builds, verifies, tags, publishes.
- A tool's failure mode is loud and early (`set -euo pipefail`, explicit FATAL
  lines) — a half-cut release is worse than none.
- Evidence is produced at run time, never pasted from memory. If a lock cannot
  be reproduced when the tool runs, the tool must refuse.
