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

**Baseline:** <!-- the main sha you measured against: `git rev-parse --short HEAD` before you branched -->
**Declared move:** no <!-- `yes` if the DIGEST chain legitimately changes here; then name the lock step above -->

<!-- One line per claim: the command and the line it printed. Probes welcome (probes/README.md).

     Why the baseline field: evidence has a shelf life (#822). On a day when
     several crews merge into one main, a number measured an hour ago may be
     about a world that no longer exists — #207's whole evidence table stopped
     reproducing because #205 landed underneath it, and every lock still passed.
     Stating the sha makes the staleness checkable instead of invisible. CI
     warns when it has drifted and names the intervening commits; for a declared
     move it fails, because a move whose 'before' is not the tree's before
     cannot be verified by anyone. -->
