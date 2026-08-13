# ci/fixtures/ — the things CI is identical *to*

## `neutral-baseline.chain` — the seal the control group is held against

Sixty links: the full `DIGEST` chain of a canonical run — seed 42, 6,000 ticks,
headless — ending at the sealed head
`be383798973dfe15006edaf96884e2ea4472e22142ada7518fc85d6f60cfa969`.

The head has moved twice before this lane could hold anything, and twice since —
see **Seal history** below, and read it before reading the law. The first two
were declared, correct, and landed while this file was still on an unmerged
branch. That is not a flaw in the law; it is the shape of the only window the
law cannot cover, and it is the reason the law binds on **units** rather than
on this file. The last two are reseals the law actually governed, and both
arrived with the argument the law asks for.

Two reseals in one evening is a rate this file should not sustain, and the
reason it can be read off the table below rather than guessed at: neither is a
leak, and neither could be fenced, because `--neutral` fences the character
layer and both units changed `matrix.realworld`. The lane's teeth arrive when
the character layer actually couples and the fence starts costing something to
honor; until then a declared move on the domain reaches this file by
construction, and the instrument's value is the **shape** of the divergence it
reports rather than the fact of one.

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
| `5b3b7c8f…4a3bb32b` | 2026-08-13, evening | **#764** (`fate keys to the birth event`, PR #787) executes #373's ruling and #212's law: `AcceptanceLoop.threshold` stops deriving from `human.name` and derives from the birth event, so every open link's threshold changes value, and `KID_BASE` moves 144 -> 112 because the birth-keyed bar at 144 admits 0 births of 11,760. The realworld segment digests those thresholds from the first accrual window, so the divergence is total: link 1 (`tick=100`) already differs. **Not fenced, deliberately.** `--neutral` is the character layer's fence — *no character coupling, no derived sheet, no new token in any line* — and this change is in `matrix.realworld`, not `matrix.character`. Fencing it would put a CLI flag inside the fate derivation, which is the one read #212's hygiene clause and #556 exist to forbid, and would leave the control group running different physics rather than the same world with a feature off. The positive evidence that nothing character-shaped leaked is that the flagged and unflagged lanes land on the **same** sha, `5b3b7c8f…4a3bb32b`, as they did before this unit. Declared against `.github/canonical-digest` in the same commit and gated by `tools/digest-move.sh` (`VERDICT ARGUED`). |
| `be383798…60cfa969` | 2026-08-13, evening | **#377** (`303: the unwriting`, PR #888) executed D-013's one canonical exception: on a firing the death is not written, so Ivan Adeyemi is alive after t=1850 and Thomas Frost after t=5950, and the world downstream of a mind who is still in it is a different world. Declared against `.github/canonical-digest` in the same commit and gated by `tools/digest-move.sh` (`VERDICT ARGUED`). Unlike the two before it, this divergence is **tail-only and dated**: `NEUTRALDIFF 18/60 byte-equal`, `first_unequal_link tick=1900` — every link up to the last digest boundary before the first firing at 1850 is byte-equal, and the divergence begins at the first boundary after it. #852's and #764's both began at link 1 because a value entered the seal; this one begins where the feature first acts, which is the attribution this instrument exists to produce and the first time it has produced one. **Not fenced, for #764's reason:** `--neutral` is the character layer's fence, `Main.neutral()` has no reader in `src/` at all, and the clause is `matrix.realworld` — a world mechanic behind that flag would redefine the control group as *the path without whichever unit last went red*. The flagged and unflagged lanes land on the same sha, `be383798…60cfa969`, as they did before this unit. |

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

