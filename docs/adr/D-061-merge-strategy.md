---
title: "D-061 — The merge strategy is a term of the balance law: rebase, and the meter reads the button"
status: accepted
date: 2026-08-13
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #911
informed: every crew, every day of the year
---

# D-061 — The merge strategy is a term of the balance law: rebase, and the meter reads the button

*In the context of a balance law read from GitHub's four contribution legs, facing a merge button that authors one extra commit per unit and so holds the commit leg at roughly twice the pull request leg, we chose **rebase merge as a term of the law itself** — with `tools/balance.sh` printing the repository's enabled merge strategies on every SCOPE line — over squash merge and over reweighting the commit leg, to achieve four quarters that are reachable by construction, accepting that we lose `Merge pull request #N` as a history landmark and that a term of a written law now lives in a settings page no reader of a verdict can see.*

## Context and Problem Statement

D-060 asks each of the four contribution legs to hold a quarter of a day. Let a unit be one pull request closing one issue, carrying `k ≥ 1` atomic commits (D-039), reviewed by one adversarial pass (D-030). Landing `U` units in a day, plus `I` issues opened:

```
prs     = U
reviews = U
commits = U·(k+1)      under merge commits: the k unit commits, plus the one GitHub authors
issues  = I
```

`commits = prs` forces `U·(k+1) = U`, so `k = 0` — a pull request carrying no commits. For any day of honest unit work the commit leg is at least twice the pull request leg. This is not a target being missed; it is a target the arithmetic refuses before the discipline is asked for.

**The direction of that failure is the opposite of the one #911 attributes to it, and this record carries the correction rather than inheriting the mistake.** #911 says the merge habit "is why a day can ship 64 pull requests, 103 reviews and 87 issues and still read `verdict=LAGGING:commit`". It is not. That reading is #910's fault: the commit leg was blind, counting merge commits and only the ones GitHub's UI authored, so it read `56` on a day whose `main` grew by `133`. Merge commits *inflate* the commit leg — they cannot produce a commit-leg deficit. Feeding 2026-08-12's true counts through the meter's own `need_in`:

| 2026-08-12 | commits | issues | prs | reviews | total | verdict |
|---|---|---|---|---|---|---|
| as the meter read it | 56 | 87 | 64 | 103 | 310 | `LAGGING:commit`, deficit 29 |
| with the commit leg reading (56 merges + 77 unit commits) | 133 | 87 | 64 | 103 | 387 | `LAGGING:pr`, deficit 44 |

The second row is arithmetic, not a reading: the counts come from `git log` over `main`, the deficits from the same closed form the tool uses. Both of #911's halves are true — #910's fix doubles the commit leg overnight, and under merge commits the four quarters are unreachable — but the sentence that pinned one specific `LAGGING:commit` line on the merge button was wrong about which of the two faults produced it. Fixing #910 without ruling here is what would have made the imbalance visible; the two issues had to be answered in the same week or neither answer was safe.

## Decision Drivers

* The atomic commit message is the artifact in this house (D-039); a strategy that discards it is not on the table however good its arithmetic
* A law's terms belong in a record, not in a constant — a meter that hard-codes a merge-button setting has put a decision somewhere no one will look for it
* Whichever strategy is chosen, a verdict must not be readable without it: the merge habit is an input to the commit leg and it lives outside the repository
* The keeper problem from #910 repeats here — the fault that hid for 258 commits was a setting nobody typed, and a document cannot hold a setting

## Considered Options

* **Rebase merge** — the unit's commits land on `main` as they were written
* **Squash merge** — one commit per unit, composed by GitHub
* **Keep merge commits and reweight the commit leg** — amend the law to a 2:1:1:1 target

## Decision Outcome

Chosen: **rebase merge**, stated here as a term of the balance law rather than as a preference about history. A D-060 verdict is well-formed only under it.

The practice went first and this record states what the button has already been doing. #891 was the last unit merged with a merge commit (`d4b611f`, 2026-08-12T06:26:23Z); #909 was the first merged by rebase (2026-08-12T21:29:37Z, which is 2026-08-13 on the operator's clock), and every unit merged since has landed the same way — `git log --merges d4b611f..origin/main` returns nothing. #911 itself put rebase first among the three ways out and refused squash outright ("refusing this one is easy"), which is the owner's framing and is what this record is accepted on.

**Rejected — squash merge.** Its arithmetic is identical and fine: `commits = U` regardless of `k`, a clean `1 : 1 : 1 : 1`. It is refused on D-039, and the usual reason given for that — "squash throws the message away" — is not true here and should not be written down as though it were. This repository's settings are `squash_merge_commit_title=COMMIT_OR_PR_TITLE` and `squash_merge_commit_message=COMMIT_MESSAGES`, so for a one-commit unit the title and the body both survive verbatim; squash and rebase are textually indistinguishable at `k = 1`. The real objection is the one that survives `k > 1`: squash *fuses*, and `k` commits each carrying one finding, one fix and one piece of evidence become a single commit that is atomic about nothing. So squash is correct exactly while `k = 1` — a value nothing in this repository enforces — and rebase is correct for every `k`. A term of a law does not get to depend on a number no lock holds.

**Rejected — keep merge commits and reweight the leg.** Honest about what is happening, and wrong about where the knob goes: it makes `tools/balance.sh` encode a repository setting as a constitutional constant, so flipping a checkbox in a settings page silently falsifies a law. The same objection in one line: a target of `2×` is only correct while `k = 1`, and nothing enforces `k = 1`.

**The term, stated so it can be checked.** `allow_rebase_merge` true; `allow_merge_commit` and `allow_squash_merge` false. **At the time of writing all three are true** — the habit changed and the button did not — so the law currently has a door left open beside it. Closing it is a repository-settings change: it is the Architect's to make, no diff can carry it, and reverting a commit does not undo it. Until it is closed the meter says so on every run — a keeper rather than a note, which is the lesson #910 paid for:

```
SCOPE WARN D-061 makes rebase the term of this law and gokselozgur5/matrix-sim still offers
merge+squash+rebase: merge commits author a second commit per unit, which holds the commit leg
near twice the PR leg; squash merges land one commit per unit but discard the atomic message
D-039 makes the artifact
```

### Consequences

* Good, because `k = 1` now gives `commits = prs` exactly, and the four quarters become a target discipline can actually hit
* Good, because the unit commit lands on `main` with its own message and its own author, which is what D-039 asked for and what the merge button was quietly overwriting as a *second* commit
* Good, because the meter reports the strategy rather than assuming it — the failure mode that made #910 possible was an unspoken setting, and this one is spoken on every line
* Bad, because `Merge pull request #N` is gone as a history landmark. The landed commit still carries `(#N)` and GitHub still links the pull request to it, so nothing is unrecoverable; a `git log --merges` sweep is
* Bad, because rebase rewrites the branch's SHAs on the way in: a SHA quoted in a pull request body is not the SHA that ends up on `main`. Evidence lines must name the tree they were measured in (#889's rule), and a body that says "measured at `abc1234`" now points at an object only the branch had
* Bad, because a term of a written law lives in a settings page rather than in the tree, and the tree cannot check it. `tools/balance.sh` reports what it reads and nothing enforces it; REST omits the `allow_*` fields from anyone without push access, so the field is `merge=unreadable` whenever the reader is not an administrator of the repository being judged

### Confirmation

The reading after the ruling, taken at 2026-08-12T22:15:06Z on the first day whose units all landed by rebase. The day is a UTC day and it is still running — this is a timestamped reading of a live day, not a closing total:

```
$ tools/balance.sh --repo 2026-08-13
SUBJECT login=gokselozgur5 repo=gokselozgur5/matrix-sim owner=gokselozgur5 owner_kind=User match=OK
BALANCE day=2026-08-13 commits=16(211‰) issues=20(263‰) prs=21(276‰) reviews=19(250‰) total=76 verdict=LAGGING:commit scope=repo
SCOPE day=2026-08-13 repo=gokselozgur5/matrix-sim account_total=76 repo_total=76 delta=0 judged=repo merge=merge+squash+rebase  (the account touched nothing outside this repository today, so the two readings are the same number)
SCOPE WARN D-061 makes rebase the term of this law and gokselozgur5/matrix-sim still offers merge+squash+rebase: merge commits author a second commit per unit, which holds the commit leg near twice the PR leg; squash merges land one commit per unit but discard the atomic message D-039 makes the artifact
DEFICIT commits=4 issues=0 prs=0 reviews=0  (the commit leg is furthest behind: 4 to clear it)
```

`211 / 263 / 276 / 250` against a 250‰ target is the first shape this meter has printed that is inside a sixth of its own law on all four legs. The day before, with the merge button in the loop and the leg blind, read `181 / 281 / 206 / 332`. The `LAGGING:commit` that survives is a four-commit gap on a day still in progress, not a structural one — which is exactly the difference this record is about.

What makes the reading trustworthy is not the shape, it is that the leg and `main` agree commit for commit at the instant it was taken:

| day | merge commits on `main` | unit commits on `main` | API commit leg |
|---|---|---|---|
| 2026-08-12 | 56 | 77 | **56** |
| 2026-08-13 (at 22:15:06Z) | 0 | 16 | **16** |

Exact on both rows and for opposite reasons: on the first the leg saw every merge commit and no unit commit, on the second there were no merge commits and it saw every unit commit. That is the same exactness #910 used to prove the leg was blind, now used to show it is not.

## Pros and Cons of the Options

### Rebase merge

* Good, because the commit leg counts units rather than units plus buttons
* Good, because the atomic message survives onto `main` unedited, author included
* Bad, because the merge loses its own commit, and with it the one-line summary of the branch a `--merges` log used to give
* Bad, because the branch's SHAs change on the way in, so pre-merge evidence must name its tree

### Squash merge

* Good, because the arithmetic is the same `1 : 1 : 1 : 1` and it holds for any `k`
* Good, because `main` gets exactly one commit per unit without asking the crew to squash by hand
* Good, because under this repository's `COMMIT_OR_PR_TITLE` / `COMMIT_MESSAGES` settings it loses no text at `k = 1` — the common case today
* Bad, because at `k > 1` it fuses `k` findings into one commit that is atomic about none of them, so its correctness is conditional on a number no lock holds

### Keep merge commits and reweight the leg

* Good, because it is honest about the merge habit instead of changing it
* Bad, because the weight belongs to a checkbox, and a law whose constants track a settings page is not a law
* Bad, because `2×` is only right while `k = 1`, and a two-commit unit falsifies it silently

## More Information

Related: [D-060](D-060-the-balance-law.md) (the law this adds a term to, and whose #910 errata forward-references this ruling), [D-039](D-039-unit-pr-granularity.md) (the atomic commit message that refuses squash), [D-030](D-030-agent-operating-model.md) (the crews whose one-review-per-unit makes `reviews = U`). Thread: #911. Tool: `tools/balance.sh`, `SCOPE … merge=`.
