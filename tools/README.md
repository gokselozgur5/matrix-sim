# tools/ — process instruments

Scripts that execute the process the documents promise. Nothing here is part
of the daemon build (D-009 still holds: the SIMULATION needs only `javac`);
these are the shop's own jigs, kept under D-030's rule that crew tooling is
part of the shop.

| Tool | What it does |
|---|---|
| `release.sh` | Cuts a phase release from `main` — refuses unless compile, `--selftest`, and `--bench` are green at cut time, then stamps their live output into the notes under your prose. Usage: `tools/release.sh vX.Y.Z "Title" notes.md` with notes written from `RELEASE_NOTES_TEMPLATE.md`. |
| `dreamreader/DreamReader.java` | D-047's teleprinter (gate #217): boots its own quiet universe (the probe pattern — private `Simulation`, explicit seed, sink captured to memory), follows one mind through the daemon's `--follow` tap, and folds their frames + every log line naming them + the film's beats into one deterministic page of prose — the day, readable. Observer-only (D-019 stands; `src/` untouched); same args, same day, byte for byte — the double run diffs empty. Build: `javac -encoding UTF-8 --release 17 -cp out -d tools/dreamreader/out tools/dreamreader/DreamReader.java` · Run: `java -cp out:tools/dreamreader/out DreamReader --pilot "Otto Aydin" --seed 1 --ticks 6000 [--out day.txt]` — exit 0 a day rendered, 2 nobody by that name. |

House rules:

- A tool never mutates `src/` or `docs/`; it builds, verifies, tags, publishes.
- A tool's failure mode is loud and early (`set -euo pipefail`, explicit FATAL
  lines) — a half-cut release is worse than none.
- Evidence is produced at run time, never pasted from memory. If a lock cannot
  be reproduced when the tool runs, the tool must refuse.
