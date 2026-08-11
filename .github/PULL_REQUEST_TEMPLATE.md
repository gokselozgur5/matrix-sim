<!-- One unit, one PR (D-039). Title says what exists now that didn't. -->

Closes #

## What this ships

<!-- Mechanism, not file list. Which decision(s) it executes. -->

## Locks (D-039 light tier)

- [ ] compile: `javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')`
- [ ] `--selftest` — paste the `SELFTEST OK` line (docs-only: state carried from `main`)
- [ ] digest leash — byte-identical `DIGEST` at an agreed tick, or the PR states why the universe legitimately changed
- [ ] `--bench` — required when the change is speed-adjacent (paste the `BENCH VERDICT` line)

## Evidence

<!-- One line per claim: the command and the line it printed. Probes welcome (probes/README.md). -->
