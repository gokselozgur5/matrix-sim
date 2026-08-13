# ci/fixtures/ — the things CI is identical *to*

## `neutral-baseline.chain` — the seal the control group is held against

Sixty links: the full `DIGEST` chain of a canonical run — seed 42, 6,000 ticks,
headless — taken from `main` at the moment this lane was established, ending at
the sealed head
`e9c833ae59c66c93935c49faf0700f84c355890b3bee645fa5d99d81d4d6675a`.

The head has moved twice before this lane could hold anything — see **Seal
history** below, and read it before reading the law. Both moves were declared,
both were correct, and both landed while this file was still on an unmerged
branch. That is not a flaw in the law; it is the shape of the only window the
law cannot cover, and it is the reason the law binds on **units** rather than
on this file.

Regenerate the file exactly the way CI reads it:

```sh
javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java')
java -cp out matrix.Main --neutral --headless --seed 42 --ticks 6000 | grep '^DIGEST ' > ci/fixtures/neutral-baseline.chain
```

## The reseal law

NEUTRAL is not a migration step. It is a **permanent control group** (the D-042
ruling, #212, 2026-08-11), and this file is the thing it is measured against —
which makes the file's edit rule more important than its contents.

**This fixture is data, not a build artifact.** It is regenerated only by a
**declared move on the NEUTRAL lane**, and any such regeneration names, in the
PR that makes it: the move, why the control group's own bytes had to change,
and the old head beside the new one. Nothing else may touch it — not a rebase,
not a refactor, not a red build.

The rule exists because of the failure mode it prevents. A fixture that gets
regenerated whenever CI turns red is not a control group; it is a transcript of
whatever the code happened to do last, and the lane that reads it is a lane that
can never fail. The moment this file is updated to make a build green, the
phase has lost its only instrument for the question the two-die law actually
asks — not *"did the world change?"* but *"is every change attributable?"*

Two consequences worth stating plainly:

- **A red NEUTRAL lane is a finding, never a chore.** The correct first response
  is to find what leaked onto the control group's path, not to re-record the
  chain.
- **A legitimate reseal is rare and loud.** A change to the digest's own
  encoding, or to a value inside the seal, is a declared digest move under D-010
  and moves this file *and* the README's published sha together, in one PR that
  says so in its title.

The referee that reads this file is `probes/NeutralDiff.java`; the lane that
runs the referee on every push is the `neutral lane` step in
`.github/workflows/locks.yml`.

## Seal history

The law above applies from the lane's first green build. Every entry here names
a move, the reason the control group's own bytes had to change, and both heads.

| sealed head | taken at | why the seal moved |
|---|---|---|
| `4d1e827f…acf0759` | `2d480a2`, pre-v6 | the original pre-v6 baseline; superseded before the lane's first build |
| `a2baee59…10336d` | lane establishment | **#497** (`bond: the heart enters the chain — a declared move`, PR #864) put the bond book inside the digest while this lane was being built. The move was declared and correct; it simply landed first. The unit's own DoD anticipated exactly this: *"if main's seal moves before this lands, the seal's sha supersedes the literal — the invariant is byte-equality to the sealed baseline."* Two other v6 units in the same window (#525's character axes, #357's p-curve) touched the tree without moving the chain. |
| `e9c833ae…d4d6675a` | lane merge, 2026-08-13 | **#852** (`bond: the book learns to forget`, PR #963) gave every bond edge a *runs apart* field and let a candidate 300 windows apart leave the book. Declared, argued, and gated by `tools/digest-move.sh` (`VERDICT ARGUED`) — but declared against `.github/canonical-digest`, which is a *different* instrument from this one, and it landed while this lane was still an open PR. The divergence is total rather than tail-only: the bond segment is digested from the first link, so link 1 (`tick=100`) already differs. Against **49 units merged that day** it is the only move — the other 48 held the chain byte-identical, which is the discipline this file exists to keep honest. |

**What this seal is, precisely.** It is not "pre-v6 main" any more — three v6 units
had already merged, one of them a declared digest move, before the control group
existed. It is the chain *as of the moment the lane could first hold it*, and the
moves that preceded it are attributable through their own PRs, which declared
themselves, rather than through this instrument.

From here the law binds, and it binds on the units, not on this file: a v6 unit
that changes the world's bytes either runs its change **behind the `--neutral`
fence** — so the flagged lane still reproduces this chain — or it comes with a
reseal that argues, in its own PR, why the control group itself had to move. A
seal that follows `main` wherever it goes is not a control group; it is a
transcript with a longer changelog.

