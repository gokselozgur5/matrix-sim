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

**Baseline:** <!-- the main sha you measured against: `git rev-parse --short origin/main` before you branched.

     STACKED ON ANOTHER UNIT? Write MAIN's sha anyway, and name the parent PR on
     the next line. Not because it is the honest answer — it is not: your numbers
     were produced with the parent's changes present, so the tree you measured is
     the parent's head and not this one. It is the answer lock 0 can check.

     The parent's head cannot be used, and three PRs failed the lock on it in one
     day (#1453): the ritual rebases before every push, a rebase rewrites the
     parent's sha, and the lock then reports `the stated baseline <sha> is not a
     commit in this repository` — about evidence that was never wrong. Naming a
     sha that a mandated step destroys is a field nobody can fill correctly.

     So: main's sha, the parent named beside it, and the gap stated rather than
     hidden. -->

<!-- One line per claim: the command and the line it printed. Probes welcome (probes/README.md).

     Why the baseline field: evidence has a shelf life (#822). On a day when
     several crews merge into one main, a number measured an hour ago may be
     about a world that no longer exists — #207's whole evidence table stopped
     reproducing because #205 landed underneath it, and every lock still passed.
     Stating the sha makes the staleness checkable instead of invisible. Lock 0
     warns when it has drifted and names the intervening commits, and fails when
     this PR also moves the seal, because a move whose 'before' is not the
     tree's before cannot be verified by anyone. It reads the move off
     .github/canonical-digest, not off a field here: this section states one
     fact once, and the argument for a move goes under the heading above.

     Put this line above any example baseline you quote further down — the
     reader takes the first `Baseline:` line in the body and cannot tell a code
     fence from a field (#1014). -->
