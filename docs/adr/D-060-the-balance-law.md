---
title: "D-060 — The balance law: four quarters, and the meter that proves it"
status: accepted
date: 2026-08-12
decision-makers: gokselozgur5 (owner), the resident machine (pair)
consulted: thread #781
informed: every crew, every day of the year
---

# D-060 — The balance law: four quarters, and the meter that proves it

*In the context of a year measured in daily contributions, facing a first day that produced 603 issues and zero reviews, we lean toward a four-quarter balance — commits, issues, pull requests and reviews each holding ~25% of a day — enforced by a meter that reads the same API the profile graph reads, and against unmeasured intent or a volume-only target, to achieve days that plan, build, ship and doubt in equal measure, accepting that the meter will call us lagging on days we felt productive.*

## Context and Problem Statement

The Architect's rule, in session (2026-08-12): the four kinds of contribution GitHub counts should each hold a quarter of the day. It began as a trio (D-039's "commits, issues and PRs grow in proportion") and grew a fourth leg when reviews entered the flow — and the first measurement showed why the rule was needed: **2026-08-11 ran 863‰ issues and 0‰ reviews**, a day that planned brilliantly and doubted nothing. The reasoning is not arithmetic hygiene:

* a day of only PRs is a day nobody planned;
* a day of only issues is a day nobody shipped;
* a day without reviews is a day nothing was doubted;
* a day without commits is a day nothing was built.

The balance is a shape of work, and the meter exists because in this house a rule that cannot be measured is a mood (D-027's founding line, reapplied to process).

## Decision Drivers

* The four legs are the four halves of a working day: plan, build, ship, doubt
* The measurement must come from the same source the profile graph uses, so it cannot flatter us
* Reviews must be REAL reviews — the rule creates pressure toward the ceremony, and the answer to that pressure is adversarial passes, not rubber stamps
* Deficits should be actionable: the meter names the lagging leg and the count that clears it
* A second identity approving one's own work is refused explicitly (see Consequences)

## Considered Options

* Four quarters with a meter (`tools/balance.sh`), reviews earned by real passes
* The old trio (commits/issues/PRs), reviews uncounted
* Volume only: hit the daily total however it lands

## Decision Outcome

Accepted by the owner's verdict, 2026-08-12, in session — *"hepsine agreed kanka barajı aç"* — all thirteen Season Three gates in one breath, the same word that opened Season Two; recorded in the gate thread.

Leaning: four quarters with the meter. `tools/balance.sh [YYYY-MM-DD]` prints the day's mix in per mille against a 250‰ target per leg, names the lagging leg, and reports how many artifacts of each kind close the gap — solving for the larger day that adding them creates, rather than the day already behind us.

**Explicitly refused, and recorded so it stays refused:** using the owner's second GitHub account to approve the first account's pull requests. It fails on three counts — it credits the reviewing account, so it does not even serve the graph it would be gaming; it drags a work identity into a personal repository's history; and an approval badge is the one artifact an outside reader takes as evidence that a second person looked. In a repository whose whole constitution is *evidence or it didn't happen*, that badge would be the only false line in it. Self-review COMMENTS are legitimate and count; the missing green stamp is a cost we accept.

### Consequences

* Good, because the day's shape becomes visible and correctable while the day is still running
* Good, because the review leg forces the adversarial passes to become routine rather than heroic
* Bad, because the meter will report LAGGING on days that felt excellent — which is the point, and will still sting

### Confirmation

`tools/balance.sh` prints a verdict line for any day, and its first two readings are recorded here: 2026-08-11 `verdict=LAGGING:review` (0‰ reviews against 863‰ issues) and 2026-08-12 `verdict=LAGGING:issue` at the time of writing, both from the live API.

**Errata (2026-08-13, #910):** Both readings above were taken with **the commit leg blind**, and the record should not be read without knowing it. `totalCommitContributions` credits a commit only when the author address is verified on the account being read. Every non-merge commit on `main` at the time — **258** of them, every unit commit this repository had ever shipped — carried `g.ozgur@archangelautonomy.com`, which GitHub resolves to the owner's **other** account. So the leg counted merge commits, and only the ones GitHub's UI authored. The match is exact on every day measured:

| day | merges by the verified address | merges by the other address | API commit leg |
|---|---|---|---|
| 2026-08-10 | 2 | 0 | **2** |
| 2026-08-11 | 47 | 15 | **47** |
| 2026-08-12 | 56 | 0 | **56** |

Three things follow, and only the first two are settled here.

*The readings stand as readings and fall as evidence for the leg.* 2026-08-11's `0‰ reviews` is untouched — reviews were never in question. But 2026-08-12's `commits=56 … deficit 29` was taken on a day that landed 77 unit commits, none of them counted. The shape this record was accepted on was real in three legs and under-read in the fourth.

*The rule now has a keeper rather than a note.* `tools/attribution.sh` asks GitHub's own resolution per commit and prints a denominator; CI runs it on every pull request and fails the build on a misattributed one. The fault was a global `git config` — the reason nobody saw it is that nobody typed it, which is exactly the class of thing a document cannot hold.

*What this errata does not do.* The 258 historical commits are left as they are. Correcting them means rewriting every SHA in the repository, which would break every pinned `git archive`, every SHA quoted in an ADR, and every evidence line in a merged PR body — a cost only the Architect can weigh against a graph that is retrospectively honest. The question is recorded here so the answer is a decision rather than a drift.

*One consequence that is not this record's to rule.* This errata makes the commit leg count unit commits, and while merge commits remain the merge habit each unit will produce two. Under that arithmetic the four quarters are unreachable by construction — see #911, filed the same day.

**Errata (2026-08-13, #821):** Every number in this record is an **account** statistic, and the record should say so in its own text rather than leave it to the tool. `contributionsCollection` roots at `viewer` and spans every repository the account touched, so the readings above answer *was the account balanced*, which is not the same sentence as *was matrix-sim balanced*. #785 taught the meter to say it on every line — `scope=account|repo`, and a `SCOPE` line carrying both readings and their delta.

On every day the meter has been run, the two readings are the same number:

| day | account total | repo total | delta |
|---|---|---|---|
| 2026-08-09 | 1 | 1 | **0** |
| 2026-08-10 | 106 | 106 | **0** |
| 2026-08-11 | 699 | 699 | **0** |
| 2026-08-12 | 310 | 310 | **0** |
| 2026-08-13 | 79 | 79 | **0** |

That is stronger than it looks. A repository's contributions are a subset of the account's in every leg, so an equal total forces equality leg by leg — the two readings are identical, not merely balanced against each other. **The Confirmation's rows are therefore scope-independent:** 2026-08-11 and 2026-08-12 read the same account-wide or repo-scoped, and the caveat above is about what a future reading could mean, not about what these ones did.

*The default stays `account`, and this is the record that says why.* #821 argued for flipping it to `repo` on the ground that `delta=0` makes the flip a provable no-op today, and that the cheapest moment to make a change is the moment it costs nothing. The measurement is real and it is reproduced above, but it does not carry that weight: two identical readings cannot distinguish the two defaults, so the same zero is exactly as good an argument for changing nothing. What it settles is that the question is not urgent and that these rows are safe either way. Three things then keep the wide reading in front.

*It is the only reading whose object is asserted.* The subject check (#902) proves the token's login owns the repository under measurement, but it resolves the **owner** half of `OWNER/NAME` and never asks whether the name exists — `tools/balance.sh --repo --for gokselozgur5/matrix-simm 2026-08-12` prints `verdict=EMPTY` at exit 0 for a day that ran 310 contributions (#923). Behind a flag that hole is reached only by asking for it. As the default it would sit under every headline number the meter prints.

*The warning belongs on the reading that can flatter.* When the two part, `scope=account` prints `WARN this verdict counts N contribution(s) made outside REPO; rerun with --repo`; `scope=repo` prints a plain note about what it excluded. The loud line is on the wide reading, which is the direction the risk runs.

*And the subject of the law is genuinely arguable, which is the reason this is recorded and not decided quietly.* This record's drivers put the measurement at the same source the profile graph reads, and the graph's squares are account-wide by construction; D-054's directive is narrower — *"bu projeye"*, to this project. Both readings come from that one API, so "same source" does not settle it. A meter defaulting to `repo` would call a day lagging that the graph draws balanced, and the flip would make the year's law measure a set the graph never draws.

*What this errata does not do.* It does not close the question — it prices it. The flip remains one line behind `--repo`, and the day the account first contributes somewhere else the `SCOPE` delta stops being zero and says by how much, unasked. That is the day to re-ask, with a reading that can finally tell the two defaults apart.
**Errata (2026-08-13, #828) — the ruler and the target both move.** The meter could not print `verdict=OK`. Two separate faults held it shut, and #828 named one of them.

*The ruler.* The meter counted contribution **events**, and 2026-08-11 produced 603 issues — 63% of that entire week. A window averages the *shape* of adjacent days; it cannot average away a day whose *volume* exceeds every other day put together, so widening the span from two days to thirty changed nothing. #784 landed the windows on the theory that adjacent days of different character would balance, ran the proof it had named, and the proof failed. Measured with the old ruler, live, on 2026-08-13 with those days closed:

| span | commits | issues | prs | reviews | total | verdict | deficit on the lagging leg |
|---|---|---|---|---|---|---|---|
| 2026-08-11 alone | 47 | **603** (863‰) | 49 | 0 | 699 | `LAGGING:review` | +233 reviews |
| 2026-08-12 alone | 56 | 87 (281‰) | 64 | 103 | 310 | `LAGGING:commit` | +29 commits |
| the pair (`--days 2`) | 103 | **690** (684‰) | 113 | 103 | 1009 | `LAGGING:commit` | +199 commits |
| `--week` | 105 | **791** (709‰) | 117 | 103 | 1116 | `LAGGING:review` | +235 reviews |
| `--month` | 105 | 791 (709‰) | 117 | 103 | 1116 | `LAGGING:review` | +235 reviews |

Thirty days and seven days are the same reading, which is the whole argument in one row. The unit was wrong: 603 issues is one act — a decomposition sweep — recorded as 603 units of thought.

**So each day now contributes its own shape, weighted equally.** A working day is 1000 per mille split across its four legs, and a window's leg is the sum of those splits over the days that had work. One sweep is worth one day, and the most any single day can move a leg is 1000/days — bounded, where the event ruler was not. A single day *is* its own shape, so every one-day reading in this record is arithmetically unchanged, and `tools/balance.sh --events` reproduces any window reading taken before today.

*The target, which #828 did not name and which alone would have kept the meter shut.* A leg cleared the bar when `4*leg >= total`. Four legs that sum to the total can all satisfy that only at **exact equality**, so `verdict=OK` demanded 250‰/250‰/250‰/250‰ to the artifact — a day of 5 commits, 5 issues, 5 PRs and 4 reviews read `LAGGING`. This record's own English is "each holding ~25%", and every failure it names is an *absence*: a day of only issues is a day nobody shipped, a day without reviews is a day nothing was doubted. The meter had turned "about a quarter" into "at least a quarter on all four at once", which is a different sentence and an unsatisfiable one. **A leg is now thin below half its quarter — under 125‰, an eighth of the whole.** The floor is one-sided deliberately: a leg can only be large at another leg's expense, and that expense is what the floor reads.

Both together, measured back to back at 2026-08-12T22:47Z against the same live API:

| reading | before | after |
|---|---|---|
| 2026-08-12 | `LAGGING:commit`, +29 commits | **`OK`** |
| 2026-08-13, at 30/37/38/35 (214‰/264‰/271‰/250‰) | `LAGGING:commit`, +7 commits | **`OK`** |
| week ending 2026-08-13 | `LAGGING:commit`, **+239 commits** | `LAGGING:commit`, **146‰-days** — 0.146 of a day |

The middle row is the target fault standing alone: a day that planned, built, shipped and doubted within six per cent of a quarter on every leg was called lagging. The bottom row is the ruler fault: the same week that wanted three months of ordinary work now wants an afternoon. *The law did not change. It became answerable.*

*The cost, recorded rather than buried.* A day holding one contribution now casts a whole day's vote. 2026-08-09 held a single pull request and nothing else, and it carries a whole day's 1000‰ of PR shape into its week: at 2026-08-12T22:47Z the week ending 2026-08-13 read 315‰ prs by shape against 123‰ by events, off one PR. The old distortion was unbounded and this one is bounded by 1000/days, which is the trade — but it is a real distortion and it is open. `--rulercheck` asserts it as a known property rather than leaving it to be discovered.

*What this errata does not do.* It does not touch `--repo`. The per-day query is account-wide and carries no per-repository breakdown, so no repository-scoped day shape can be taken; a `--repo` window falls back to `ruler=events` and prints why. Every line of every run now carries `ruler=`, so no reading in this record or after it is ambiguous about what produced it.

*Confirmation for both moves.* `tools/balance.sh --rulercheck` judges the ruler and the floor against day vectors whose answers arithmetic already settles, with no token and no network; CI runs it on every pull request. The row that carries the change is the pair where the same seven days — one sweep beside six ordinary days — read `LAGGING:review` by events and `OK` by day-shape.

## Pros and Cons of the Options

### Four quarters with a meter

* Good, because balance stops being a promise and becomes a reading
* Good, because the deficit is actionable mid-day
* Bad, because a quarter is a blunt target — some days genuinely belong to one leg (a phase-boundary skeptic pass is a review day by nature)

### The old trio

* Good, because it is already law (D-039) and needs nothing
* Bad, because it counts the day's doubt as free, and yesterday proved it is not

### Volume only

* Good, because simplest to chase
* Bad, because volume without shape is the hollow-row failure the constitution bans

## More Information

Related: [D-039](D-039-unit-pr-granularity.md) (the trio this extends), [D-054](D-054-the-year.md) (the year this shapes), [D-030](D-030-agent-operating-model.md) (the crews whose reviews fill the fourth leg), [D-027](D-027-performance-budgets.md) (the falsifiability principle borrowed here). Thread: #781. Principle: Dev14.
