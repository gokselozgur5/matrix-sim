# tools/ — process instruments

Scripts that execute the process the documents promise. Nothing here is part
of the daemon build (D-009 still holds: the SIMULATION needs only `javac`);
these are the shop's own jigs, kept under D-030's rule that crew tooling is
part of the shop.

| Tool | What it does |
|---|---|
| `release.sh` | Cuts a phase release from `main` — refuses unless the locks are green at cut time, then stamps their live output into the notes under your prose. The locks are `locks.yml`'s 1 through 7, run in its order: a clean compile, `--selftest` at the standard budget and again over the full 6,000-tick arc, `--bench`, a clean probe compile, `probes/bench.sh`'s whole judged sweep, and the seal pinned in `.github/canonical-digest`. It ran the first, second and fourth of those and no others until #1052, which made the gate on the artifact that LEAVES the repository a strict subset of the gate on a merge into it: a tree whose seal had moved undeclared, or whose probe sweep was red, could be tagged and published while a pull request carrying the same bytes was being refused. The sweep is quoted in the notes by its summary line and the seal by the line the daemon printed, so the release notes carry the sha the release ships. `--check` runs the same seven and prints the evidence block a cut would stamp, then stops before the tag: the locks used to be runnable only by cutting a release, so nobody ran them, and lock 2 spent 62 commits refusing a green `main` because it matched `--selftest`'s whole stdout against a line that had stopped being first (#972). The verdict is now found by an anchored grep over that stdout, so the next law to become an assertion adds a line without breaking the release. Wired into CI as the last step in the lane — it rebuilds `out/` and `probes/out` from nothing, so nothing may follow it — which runs `--check` and judges its `RELEASE CHECK … VERDICT PASS` line, so the release path is exercised by every pull request instead of by the next release. Usage: `tools/release.sh vX.Y.Z "Title" notes.md` with notes written from `RELEASE_NOTES_TEMPLATE.md` · `tools/release.sh --check` (no tag, no push, no token). **Which locks a release skips, and why:** the ones that judge a PULL REQUEST rather than a tree — lock 0's evidence baseline, lock 9's declared digest move, `attribution.sh` — because a tag has no body, no base branch and no author to read, and by cut time those questions were answered at the merge; the lanes that judge the shop's own jigs rather than the daemon being shipped — `balance.sh --datecheck` and `--rulercheck`, the teleprinter's exit grammar — one of which also judges an account and not this repository; and three tree locks CI keeps that the cut does not — the charset pin (lock 8), the neutral control group (lock 10), and the teleprinter's golden day (lock 11). Those last three are cheap and they do judge this tree; they are outside the cut gate only because #1052 drew the floor at the daemon's own locks, and that is a difference stated here rather than left to be inferred from a grep. Exit 0 the locks are green and the cut may proceed · 1 a lock is red · 2 the invocation was refused. |
| `subissue.sh` | Cuts a child issue and hangs it on its parent in one motion — inherits the parent's milestone, refuses a closed parent (a tree does not grow from a closed branch). **Three of its refusals spent the wrong code until #1309** — `${1:?usage}` leaves with **1**, this tree's code for *the claim does not hold*, while two hand-written refusals in the same file used **2** correctly, so the tool disagreed with itself about what a refusal costs. Neither of `advice.sh`'s exit checks could see it: `codes_undocumented` reads LITERAL exits and bash's is not one, and `codes_unspent` asks whether a promised code is spent — 2 was, by the other two — so the hidden 1 was invisible from both directions. Usage: `tools/subissue.sh <parent> "<title>" body.md [--label L] [--milestone M]` · `--selftest` runs the DOOR's six cases: cutting an issue needs a token, refusing does not, and the token half stays out because a fixture that fakes `gh` tests the fake (#1273). Exit 0 the child was cut and hung on its parent · 2 the invocation was refused — no arguments, a parent that is not a number, a missing body file, an unknown flag, or a parent that is closed. |
| `backlog.sh` | Sorts the open backlog by EVIDENCE rather than by age: how many `build-unit` issues carry a measurement, and how many are a shape somebody recognised. #1246's argument is the day's own record — of the four issues filed from a run, none needed a correction; of the three filed from a shape, one was closed as already-checked, one had its central table wrong, and one had its timing claim withdrawn. A reader picking work up cannot tell those apart, and guessing wrong costs a unit. **It found its own commissioning defect:** #1246's headline measurement was `30` open and `20` unmeasured, taken with `gh issue list`'s undeclared 30-row default; the true figures are 508 and 425, so an issue about the danger of unmeasured claims was itself off by seventeen times. This tool pages explicitly and prints `limit=` for that reason. The classifier is generous on purpose — the word `measured` anywhere in the body counts — so `unmeasured=` is a FLOOR on the problem and never an exaggeration of it; a tool that argues its own case by rounding up is worth less than no tool. Reported, never judged: an issue is not an artefact CI can judge, and a lint demanding prose in a field is how a required field becomes `n/a` (#1246). **Both ends of the paging axis are refusals** (#1273): `NOTHING_READ` when the answer is empty — an empty answer is not an empty backlog (#1235) — and `TRUNCATED` when the count exactly fills the page, because the tool cannot tell a full page from a complete answer and guessing would be the defect it was written to name. The suite drives both over synthetic counts rather than over the API, since faking `gh` would be a bigger apparatus than the branch is worth and a fixture that fakes the transport tests the fake. **`--flow DATE` counts the day rather than the standing population** (#1323): a day that closed thirty-three and opened thirty-three prints the same `open=` as a day that closed none, and the ratio is what says whether a day drained the backlog or explored the tree — both correct at different times, since a survey unit SHOULD open more than it closes. There is no right ratio, only a visible one. Usage: `tools/backlog.sh` · `--list` prints one row per unmeasured issue · `--flow YYYY-MM-DD` · `--selftest` runs the classifier's, the pager's and the flow door's cases (no token, no network). Exit 0 counted · 2 the invocation was refused · 3 the backlog could not be read · 4 nothing was read at all, which is not an empty backlog · 5 the answer filled the page, so it is a page and not a backlog. |
| `issuetree.sh` | Prints an issue's tree root to leaves: parents carry their child count, leaves carry a dot. An answer it could not READ is a `?` row and never a leaf — a rate limit, an expired token and a genuinely childless issue used to produce the same dot, so a parent could be drawn as a leaf because the API was busy (#1235). The walk finishes and the trailer states the denominator: `ISSUETREE VERDICT COMPLETE|PARTIAL root=N unreadable=N`, exit 3 when anything was unreadable — this repository's code for *the answer could not be read*, spent the same way by `checkage.sh` and `prstate.sh`. A 404 is an empty answer rather than a failure, since that is what GitHub returns for an issue with no sub-issues. **Both refusals were wrong until #1276.** A missing argument left through bash's own `${1:?usage}`, which exits **1** — this tree's code for *the claim does not hold* — so a typo was reported the way a broken contract is, and this row wrote that down as though it were the rule. And a NON-NUMERIC argument was not refused at all: `issuetree.sh not-a-number` walked it as an issue number and printed `? #not-a-number <unreadable>` with exit 3, so a typo and a rate-limited API produced the same verdict — #1235's own defect (an unreadable node reported as an empty one) arriving through the argument door of the tool that fixed it. Both are exit 2 now, which is what `subissue.sh` has always done with a non-numeric parent. Usage: `tools/issuetree.sh <issue> [max-depth]` · `--selftest` runs the DOOR's five cases — argument handling only, no token and no network, because a fixture that faked `gh` would test the fake (#1307). Exit 0 the whole tree was read · 2 the invocation was refused — no argument, or one that is not a number · 3 at least one node was unreadable, so the tree is PARTIAL. |
| `digest-move.sh` | Asks the question lock 7 cannot: the seal's pin moved — was that a decision? Silent (`VERDICT NONE`) for every unit that leaves the world's bytes alone; when the pin's sha differs from the base's it demands a commit carrying `Declared digest move: <old> -> <new>` with both shas in full and an issue number, and a reason field that names the issue. Makes the chain of heads a query: `git log --format=%B \| grep '^Declared digest move:'`. Usage: `tools/digest-move.sh [--base <ref>]` · `--age` answers *how long has this world stood* as a READ — the seal's age, reported rather than judged · `--selftest` runs every path this gate has against fixtures built out of THIS history. Wired into CI as lock 9. Exit 0 ARGUED or NONE · 1 UNARGUED, the pin moved and no commit says why · 2 the invocation was refused. |
| `attribution.sh` | Asks the only question the contribution graph asks: does GitHub resolve this commit's author to the account that owns the repository? Answers per commit with a denominator, and prints the `git config` that fixes a FAIL. Usage: `tools/attribution.sh` (HEAD) · `--pr N` · `--sha SHA`, any of them with `--for OWNER/NAME` to name the repository instead of reading it off `origin` · `--fix-cmd` prints the repair for this branch and nothing else, for a script to consume · `--selftest` EXECUTES that repair against fixtures, which is the only way this tool's central claim can be falsified rather than asserted. Wired into CI on every pull request. Exit 0 every commit resolves to the owner · 1 one or more do not · 2 the invocation was refused · 3 the repository has never seen the sha · 4 nothing was read at all, which is not a pass · 5 the owner's address could not be determined. |
| `balance.sh` | Reads a day's contribution mix from the same API the profile graph uses and judges it against D-060's four quarters — names the lagging leg and what it would take to clear it. Under the artifact ruler that is two different answers and the line says which is which (#952): `DEFICIT` carries the four per-leg numbers, each solved with the other three standing still, and `PLAN` carries the set that composes, because every artifact added enlarges the day the other legs are measured against and work on a lagging leg can push a leg the DEFICIT line printed `0` for under the floor. Speaks both `date(1)` dialects, so it runs on the operator's macOS box and on `ubuntu-latest`. The API roots at `viewer`, so every run opens with a `SUBJECT` line naming the login it read and exits 6 unless that login owns the repository being judged — a token for the wrong account used to print a confident `verdict=EMPTY`. `contributionsCollection` is an *account* statistic, so the default verdict answers "was the account balanced" and not "was matrix-sim balanced" — every line carries `scope=`, `--repo` asks the narrower question, and a `SCOPE` line prints both readings and their delta so a green verdict can never quietly mean somewhere else. That line also carries `merge=`, the repository's own enabled merge strategies, because the merge button is a term of the law and not a detail of it (D-061) — a run whose token cannot read those settings says `merge=unreadable` rather than guessing. The default stays `account` on the record, not in the tool (D-060 errata, #821). Usage: `tools/balance.sh [--account\|--repo] [--for OWNER/NAME] [--week\|--month\|--days N] [--events] [YYYY-MM-DD]`; the window flags judge a rolling span ending on the named day and print each of its days judged alone, and `--events` restores the pre-#828 event ruler for reproducing an earlier reading; `tools/balance.sh --datecheck` judges the day arithmetic alone, `tools/balance.sh --rulercheck` the ruler alone, and `tools/balance.sh --judgecheck` the advice alone — the last does what the DEFICIT line says over pinned day vectors and asserts the PLAN set leaves no leg thin; all three run with no token and no network, and all three are wired into CI. Exit 0 the day is balanced · 2 the invocation was refused · 3 the API would not answer · 4 a window and its days disagree, so both readings are refused · 5 one of the three suites failed · 6 the token belongs to another account, or the day is EMPTY. |
| `checkage.sh` | Dates a green check against the litany it claims to have passed (#1017). `gh pr checks` reports the run attached to the current head, and a head that has not moved keeps its run forever — including after `.github/workflows/locks.yml` has been rewritten underneath it, which is how PR #889 displayed `locks pass` from a run that had met six of today's locks never. One rule: a check is current only if its run STARTED AFTER the base's last commit to the litany file, strictly after, with both timestamps on the verdict line so the answer is quotable. Both stamps arrive from the API in one RFC 3339 shape and are compared as strings once that shape is checked — no `date -d`, so no #901 dialect to be green on one box and dead on the other — and a stamp in an unknown shape is refused rather than guessed at. Exit 0 CURRENT · 1 STALE · 2 the invocation was refused · 3 the answer could not be read · 4 ABSENT, no run on this head at all (#1004's shape; an absent run is not a passing one) · 5 NOTGREEN, which merge rule one refuses before the age question arises. Usage: `tools/checkage.sh --pr N` · `--sha SHA` · either with `--base REF` / `--for OWNER/NAME` · `tools/checkage.sh --selftest` runs the ordering rule and the row picker with no token and no network. The live reading is the merge motion's, because a run cannot date itself; CI runs the suite. |
| `prstate.sh` | Names the state `gh pr checks` cannot say: a pull request is not only red, green or pending — it can be **UNBUILT**. GitHub builds a `pull_request` run from the head/base merge ref, so a branch that conflicts with `main` gets no run at all, and the sentence a crew reads (`no checks reported on the '<branch>' branch`) is the same one it prints for a run that has not started (#1004). One verdict line per pull request with its denominator (`runs=N green=… red=… pending=…`), a row per workflow run, and the rebase that fixes an UNBUILT. A stale green under a `CONFLICTING` pull request is still UNBUILT: that run was built from a merge ref that no longer exists. And a pull request based on a branch **no workflow triggers for** is **NOT_ELIGIBLE** rather than UNBUILT — it was never going to be built, which is a different thing from not built yet, and it is how every stacked unit PR sits while `branches: [main]` is the only trigger (#1210). The eligible bases are read out of `.github/workflows/*.yml` rather than listed here, so the verdict cannot drift from what CI actually does. This is deliberately NOT a step in `locks.yml` — the run that would carry the step is the thing that does not exist — so only its judge runs in the lane. It also answers the one question a cron can hide: `--schedules` reads every `on: schedule:` in the workflows, derives the period from the cron rather than from a number typed beside it, and reports **NEVER_RAN** apart from **OVERDUE** — a schedule nobody has ever seen work is a different fact from one that slipped, and `determinism.yml` was in the first state on the day it landed, carrying the only pass in the tree that byte-compares a probe against itself (#1233). Tolerance is 1.5× the period, bracketed on both sides by cases. Usage: `tools/prstate.sh N`, or `--pr N` for the same judgement in the spelling every other tool here uses · `tools/prstate.sh` (sweeps every open PR) · `--for OWNER/NAME` · `--schedules` · `--selftest` (twenty-six cases, no token, no network). Exit 0 green · 1 red · 2 the invocation was refused · 3 the repository could not be read · 4 UNBUILT · 5 pending · 6 NOT_ELIGIBLE. |
| `litany.sh` | Judges the file that judges every pull request (#1114). `locks.yml` gained floors, lost swallowing captures and grew two locks in one night, and none of it was verified by running the litany — because nothing did. Four questions: does it parse (conflict markers, literal tabs, a step with neither `run:` nor `uses:`, the three keys a workflow needs — not a YAML library, which is a dependency this tree does not take); does every verdict grep match something the tree PRINTS, by longest adjacent prefix with a floor on what survived, so a renamed or reordered verdict is refused (#1157, #1168); are the lock numbers contiguous; are two steps named the same. It runs from its OWN workflow (`litany.yml`), because a judge inside the litany cannot judge a litany that will not parse — that is the entire reason this repository has a second workflow. Usage: `tools/litany.sh [PATH]` · `--selftest` (ten cases) · `--shellcheck` parses every `run:` block as the shell that will actually run it, under the `shell:` that block declares · `--floorcheck` asks whether every suite gate carries a floor, so a suite that quietly stops running cases cannot pass. Exit 0 every question answered clean · 1 any of them broken. |
| `advice.sh` | A tool that tells you what to type owes you a command that works (#1095). Three tools in one day printed advice that was wrong in a way only EXECUTING it revealed: `--reset-author`, which destroys the author dates `attribution.sh` exists to protect (#1012); a `release.sh` grep matching a line that was no longer first (#972); a deficit naming a leg the arithmetic could not move (#952). This audits the half that is decidable from text — a printed flag exists in the tool it names — and REPORTS the half that is not: `unfalsifiable=` counts tools with no `--selftest` for their advice to be executed in. What it will not do is run the advice, because `git commit --amend` inside an audit would be a tool damaging the tree to check whether it damages the tree. It also asks, of every tool it runs, whether `--selftest` reaches a **suite** and whether any workflow **executes** it: `suites=` · `no_suite=` · `unrun=`, and a suite the lane never runs is `A_SUITE_NOBODY_RUNS` (#1212). The discriminator is the verdict line rather than the flag appearing in the file — `advice.sh` and `release.sh` were both counted as having suites they do not have, because the string `--selftest` appears in every tool this one runs. Refuses an unknown argument with exit 2, which is the same finding pointed at itself: `tools/advice.sh --selftest` used to run the ordinary audit and print a green line from a suite that does not exist. **Both flag directions are checked, and they read the row differently on purpose** (#1263). `flags_undocumented=` reads the WHOLE row, because a row legitimately names other programs' flags — `git commit --amend`, `balance.sh --rulercheck` inside release.sh's — so a whole-row read cannot false-positive there. `flags_phantom=` reads only the `Usage:` clause, bounded at the first `**` or ` Exit N`, which is what made the phantom direction checkable at all: three earlier extraction rules each traded one error for the other, because `Usage:` is prose inside a paragraph inside a table cell sharing its sentence run with the exit grammar and a bold aside. Usage: `tools/advice.sh` · `--list` · `--selftest` runs the flag audit against scratch tools and scratch catalog rows built in a temp directory (no token, no network) — the tool spent its whole life asking whether OTHER programs could execute their own advice while having no way to be watched failing itself (#1265). **The falsifiability count is JUDGED since #1311**, at the one moment it cost nothing: the reason it was a report — *four tools have no selftest today* — expired when #1307 and #1309 gave the last two their suites. The gate demands that every tool be falsifiable SOMEWHERE, not that every path is covered; `issuetree.sh` and `subissue.sh` both cover their door and not their till, and both satisfy it. Depth is guarded by each lane step's floor; this guards existence. Exit 0 every advised flag exists and every suite is run · 1 one does not · 2 the invocation was refused. |
| `balance.sh` | Reads a day's contribution mix from the same API the profile graph uses and judges it against D-060's four quarters — names the lagging leg and the count that clears it. Speaks both `date(1)` dialects, so it runs on the operator's macOS box and on `ubuntu-latest`. The API roots at `viewer`, so every run opens with a `SUBJECT` line naming the login it read and exits 6 unless that login owns the repository being judged — a token for the wrong account used to print a confident `verdict=EMPTY`. `contributionsCollection` is an *account* statistic, so the default verdict answers "was the account balanced" and not "was matrix-sim balanced" — every line carries `scope=`, `--repo` asks the narrower question, and a `SCOPE` line prints both readings and their delta so a green verdict can never quietly mean somewhere else. That line also carries `merge=`, the repository's own enabled merge strategies, because the merge button is a term of the law and not a detail of it (D-061) — a run whose token cannot read those settings says `merge=unreadable` rather than guessing. The default stays `account` on the record, not in the tool (D-060 errata, #821). Usage: `tools/balance.sh [--account\|--repo] [--for OWNER/NAME] [--week\|--month\|--days N] [--events] [YYYY-MM-DD]`; the window flags judge a rolling span ending on the named day and print each of its days judged alone, and `--events` restores the pre-#828 event ruler for reproducing an earlier reading; `tools/balance.sh --datecheck` judges the day arithmetic alone and `tools/balance.sh --rulercheck` the ruler alone, both with no token and no network. Exit 0 the day is balanced · 2 the invocation was refused · 3 the API would not answer · 4 a window and its days disagree, so both readings are refused · 5 one of the three suites failed · 6 the token belongs to another account, or the day is EMPTY. |
| `baseline.sh` | Dates a PR's evidence against the tree it is about to land on — the one lock that judges a claim rather than a compilation. Reads the body's `**Baseline:**` sha and compares it with the base: warns and *names* the intervening commits when it has drifted, because most drift touches no evidence and only the author can tell which does; fails when the stated sha resolves to no commit at all, and when this PR moves the seal and its stated `before` is not the tree's before — a move nobody can ever verify is a lock with nothing behind it. Whether the seal moved is read from `.github/canonical-digest`, here versus at the base, never from a field the author fills in. Refuses a shallow clone rather than calling every honest sha unknown. `tools/baseline.sh --selftest` runs its fifteen cases against this repository's own history, fixtures included. Usage: `tools/baseline.sh <pr-body-file> <base-sha>` · `tools/baseline.sh --selftest`. Wired into CI as lock 0. Exit 0 the claim holds · 1 it does not · 2 the invocation was refused. |
| `dreamreader/` | D-047's teleprinter: one mind's whole day, folded out of the record into deterministic prose. Boots its own quiet universe, takes three feeds (the pilot's perception frames off `--follow`, the log lines that carry their name, the world's beats), and writes a page in four movements — every sentence derived from a captured line, and where the feed is silent the silence written down. Observer-only: not one domain byte moves. The only tool here written in Java, so its build is two commands and the daemon's is the first of them — it compiles against `out/`, and skipping that line gets `package matrix does not exist`, not a shorter build. Usage: `javac -encoding UTF-8 --release 17 -d out $(find src -name '*.java') && javac -encoding UTF-8 --release 17 -cp out -d tools/dreamreader/out tools/dreamreader/*.java && java -cp out:tools/dreamreader/out DreamReader --pilot "Otto Aydin" --seed 1 --ticks 6000`. Flags: `--pilot NAME` (required), `--seed N`, `--ticks N`, `--voice cold\|none`, `--facts`, `--capture-only`, `--out FILE`, `--check-golden FILE`, `--help`. Exit 0 a day rendered · 1 the golden day drifted · 2 the record holds nobody by that name · 3 the invocation was refused (a flag, a value or a voice the tool has no reading for) · 4 no golden day at that path. Those five separate *refused* from *absent* from *drifted*, which one number for all three could not (#1011): a sweep that skips the names a seed did not grow used to skip a misspelled flag with them and render nobody. `--out FILE` is written under `--check-golden` too, where stdout carries only the verdict. `bash tools/dreamreader/exitgrammar.sh` runs one invocation per code against a golden page it blesses in a temp directory, and prints `EXIT VERDICT PASS cases=N fail=0`. Wired into CI beside lock 11. |

House rules:

- A tool never mutates `src/` or `docs/`; it builds, verifies, tags, publishes.
- A tool's failure mode is loud and early (`set -euo pipefail`, explicit FATAL
  lines) — a half-cut release is worse than none.
- Evidence is produced at run time, never pasted from memory. If a lock cannot
  be reproduced when the tool runs, the tool must refuse.
- A tool that reads a running universe keeps the probe contract
  (`probes/README.md`): its own private `Simulation`, an explicit seed, and
  read-only on everything it touches. D-047 put the reader "OUTSIDE (`tools/`
  or the bench)" and left the choice open; it lives here rather than on the
  bench because `--out FILE` writes a file the user names, which clause 1 of
  that contract forbids. Clause 6 it keeps — anything quoted as evidence comes
  from a pinned `git archive` copy. Clause 7 it keeps since #965: the page was
  always encoded once as UTF-8, but the verdicts and refusals printed around it
  went out on whatever charset the JVM inherited, so `matrix.Streams.utf8()` is
  now its first statement. The contract is the standard a tool is measured
  against here, not a claim that every tool already meets it.
- A capture that can fail carries a handler, or does not capture. `X="$(cmd)"`
  under `set -euo pipefail` dies ON THE ASSIGNMENT, so a tool that prints its
  failing cases and then exits nonzero has that report thrown away at the moment
  it became worth reading — the step goes red with an empty log, which is how
  #983 reported a dialect bug. Either write the handler,
  `out="$(cmd)" || { printf '%s\n' "$out"; echo "FATAL …" >&2; exit 1; }`, the
  shape `release.sh` already uses around `--bench`; or drop the variable, pipe
  the run through `tee "$LOG"`, and judge `$LOG`. Streaming is the default in a
  lane: under `pipefail` the tool's own exit code still fails the step, and by
  then `tee` has put the rows that say WHY into the log. The rule is about the
  capture and not about the caller — `tools/*.sh` and `locks.yml` steps alike.
- A tool that tells you what to type owes you a command that works, and the
  advice is the most expensive prose in the tool to be wrong in — it is read at
  the moment somebody has already decided to act. Three units in one day fixed
  the same defect in three tools: `attribution.sh` printed `--reset-author`,
  which destroys the author dates the tool exists to protect (#1012);
  `release.sh` matched a line that was no longer first, so a release could not
  be cut from a green `main` (#972); `balance.sh` named a leg its own arithmetic
  could not move (#952). Each was wrong in a way only EXECUTING it revealed.
  So: a printed flag exists in the tool it names, and a tool that prints advice
  has a `--selftest` for that advice to be executed in. `tools/advice.sh`
  audits the first half mechanically and reports the second — four tools have
  no selftest today, which is the gap this rule is about rather than a failure
  it asserts. What it cannot do is RUN the advice: `git commit --amend` inside
  an audit would be a tool damaging the tree to check whether it damages the
  tree.
- A tool whose output is PROSE carries a golden file, because a page that
  reads well is not a page that is still true. `dreamreader/golden/` holds one
  blessed day; `--check-golden` re-renders and prints the first line that
  moved. Re-blessing is a diff in a PR, never a quiet overwrite. A golden file
  no lock runs is a comment, and this one was one for its whole life: it landed
  198 bytes behind the tree that shipped it and drifted twice more before
  anybody re-read it. `locks.yml` now builds the reader and asks it (#965), so
  the next page to move moves in a PR.

## Read your arguments before you reach the network

A refusal that needs no API must not require one. `subissue.sh` sent `--nonsense` to
GitHub before anything read it and left with `gh`'s exit code instead of the refusal
its row promises — passing locally, where there was a token and an open parent to
read, and printing `want=2 got=4` on the runner (#1309). **Two exit codes for one
mistake, and which one you get depends on whether you happened to be authenticated.**

This is a rule and not a check, and #1314 is why. The obvious lint — does a `gh` call
appear above the last line that reads `$1` — was written, run over all twelve tools,
and produced three violations, all three false:

```
attribution.sh   the gh call is guarded (`|| true`) and the parse is above it
prstate.sh       the gh call is inside a function, nine definitions down —
                 a definition is not an execution, and line order says nothing
subissue.sh      real, and already fixed by #1309
```

A line-number reading cannot tell a function body from a straight run, and a bash
parser is a dependency (D-009). So the ordering lives here, next to the self-matching
clause, for the same reason that one does: it is followable, it is cheap, and the
check that would enforce it costs more in false accusations than the defect costs in
occurrences.

## Writing a checker here

Read clause 8 of `probes/README.md`'s contract first. Every tool in this directory
searches a population, and every tool in this directory is a file in one — `advice.sh`
alone has matched its own comments (#1157), a neighbour's row (#1222), its own suite
fixture (#1265) and its own `exit` pattern (#1276). That clause names the five repairs;
choosing among them deliberately costs a minute, and discovering the problem costs a
red first run at best and a check that silently covers nothing at worst.

## The exit grammar — what a low code means, and who may spend it

<!-- figure: ls tools/*.sh | wc -l == 12 -->
<!-- figure: ls probes/*.java | wc -l == 51 -->
Every program in this tree that a script branches on: twelve tools, fifty-odd
probes, and the teleprinter. Three populations, and until #1241 measured them,
three different meanings for `2`.

| code | meaning | who spends it |
|---|---|---|
| **0** | held · ok · the claim stands | everyone |
| **1** | broke · not-ok · the claim does not | everyone |
| **2** | **the invocation was refused** — a flag with no argument, a value the program will not take | **everyone**; this is the largest existing agreement in the tree, ten tools deep |
| **3** | **the answer could not be read** — a rate limit, an expired token, a sha this repository has never seen, a node the API would not return | **six tools**, by imitation until #1304 wrote it down |
| 4 | *contested.* Three tools spend it for **nothing was read at all, which is not a pass**; two spend it for something else | see each catalog row, and below |
| 5+ | local answers only some programs can give | see each catalog row |

The first **four** are universal: a caller may branch on them without knowing
which program it ran. Everything above is **local**, and a row that spends it
must say what it means — `advice.sh` fails the lane for a tool that spends a
code its own row does not name (#1222).

**Three became universal by imitation and the table said otherwise for months
(#1304).** Six tools spend it and all six mean *the answer could not be read*,
in six different phrasings:

```
attribution.sh   the repository has never seen the sha
backlog.sh       the backlog could not be read
checkage.sh      the answer could not be read
prstate.sh       the repository could not be read
issuetree.sh     at least one node was unreadable, so the tree is PARTIAL
balance.sh       the API would not answer
```

The phrasings stay. Six sentences of one meaning is not a defect — *the backlog
could not be read* is better prose in `backlog.sh`'s row than a generic
sentence would be, and the local colour is what makes a row worth reading. The
defect was the table calling 3 local while six tools treated it as universal,
so a reader branching on `$?` across two of them was right and the grammar told
them they were wrong.

**Four is contested and now says so.** `attribution.sh`, `backlog.sh` and
`checkage.sh` spend it for *nothing was read at all, which is not a pass* —
#1004's shape, an absent answer wearing an empty one's face. `prstate.sh`
spends it for `UNBUILT` (near enough) and `balance.sh` for *a window and its
days disagree*, which is a different thing entirely. That disagreement is real,
it is small, and it is visible here rather than discovered by a caller.

`advice.sh` cannot police either code the way it polices 2. Its
`codes_redefined=` check works because 2 is both universal AND phrased
identically in every row — the one code where a wording comparison is a check
rather than a style opinion. For 3 the wordings differ by design, so the
agreement lives here, in prose, and is kept by reading.

**Where this is not yet true, stated rather than hidden.** `probes/` spends
**2** for `NEVER_AROSE` and **3** for a refused invocation; `DreamReader` spends
**2** for `EXIT_NO_SUCH_PILOT` and **3** for `EXIT_USAGE`. Both disagree with
the table above, and #1239 moved the probes to 3 on a survey one program deep —
the defect it fixed was real (nine probes reported a typo as a world with no
births) and the constant it chose is provisional. #1241 owns the reconciliation:
`NEVER_AROSE` has to move up before `REFUSED` can come down to 2, and that is a
larger change than the unit that started it should have made alone.
