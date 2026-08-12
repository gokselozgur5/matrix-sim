<!-- One unit, one PR (D-039). Title says what exists now that didn't. -->

Closes #

## What this ships

<!-- Mechanism, not file list. Which decision(s) it executes. -->

## Locks (D-039 light tier)

- [ ] compile: `javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')`
- [ ] `--selftest` — paste the `SELFTEST OK` line (docs-only: state carried from `main`)
- [ ] digest leash — byte-identical `DIGEST` at an agreed tick, or the seal moved and the section below is filled in
- [ ] `--bench` — required when the change is speed-adjacent (paste the `BENCH VERDICT` line)

## Declared digest move

<!-- Delete this heading if `tools/digest-move.sh` prints NONE. Otherwise: the old head, the
     new head, and why the world's bytes are allowed to be different — the argument, not the
     observation that they are. Editing `.github/canonical-digest` is the declaration; the
     commit that does it carries `Declared digest move: <old> -> <new>` and an issue number.
     Paste the `DIGEST MOVE VERDICT ARGUED` line. -->

## Evidence

<!-- One line per claim: the command and the line it printed. Probes welcome (probes/README.md). -->
