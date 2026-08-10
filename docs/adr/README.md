# How decisions are recorded here

One architectural decision, one D-numbered record, MADR format. This file is the manual; [TEMPLATE.md](TEMPLATE.md) is the starting point; [../DECISIONS.md](../DECISIONS.md) is the index. (This file and the template are machinery of the ADR system under [D-029](D-029-adr-expansion.md), not extra documents.)

## The format

We follow **MADR** ([adr.github.io/madr](https://adr.github.io/madr/)) with house conventions:

```
front matter   title · status · date · decision-makers ·
               consulted (the GitHub decision thread #N) ·
               informed (the phase tracker #N)
H1             D-0XX — short title: problem + solution
Y-statement    one italic sentence, right under the H1 (see below)
sections       Context and Problem Statement
               Decision Drivers
               Considered Options
               Decision Outcome        — "Chosen option: X, because …"
               Consequences            — "Good, because … / Bad, because …"
               Confirmation            — MANDATORY here (optional in MADR):
                                         the command, grep, test or review check
                                         that proves compliance. This house runs
                                         on evidence.
               Pros and Cons of the Options — per option: Good / Neutral / Bad, because …
               More Information        — supersession links, related ADRs, crowns, principles
```

**The Y-statement** (Zdun et al., "Sustainable Architectural Decisions"):

> *In the context of \<use case\>, facing \<concern\>, we decided for \<option\> and against \<alternatives\>, to achieve \<quality\>, accepting \<downside\>.*

If you cannot write the Y-statement, the decision is not understood yet.

## Lifecycle

`proposed → accepted | rejected` · parked ideas carry `proposed (parked)` · a replaced decision becomes `superseded by D-0XX`.

**Records are immutable.** A changed mind creates a *superseding* record; both link each other in More Information. History is an audit trail, not a draft. (Live example: [D-007](D-007-terminal-ui.md) → superseded by [D-019](D-019-backend-only.md)/[D-020](D-020-observability-contract.md).)

## A decision is done when (adapted from adr.github.io/ad-practices)

1. **Evidence** exists for the claims made,
2. **criteria and alternatives** were compared at the same level of abstraction,
3. **agreement** was reached in the decision thread,
4. it is **documented** as a record here, and
5. **realization is planned**: the Confirmation section says how compliance will be shown.

## Adding a decision

1. Take the next D-number; copy [TEMPLATE.md](TEMPLATE.md) to `D-0XX-short-slug.md`.
2. Open a `decision` issue (the thread), link it in `consulted`.
3. Fill the record with `status: proposed`; add the row to [../DECISIONS.md](../DECISIONS.md).
4. Discuss in the thread. The owner's verdict in the thread closes the gate; **the machine then performs the flip** — record status and index emoji together, citing the verdict comment. Never merge 🟡 into code first (Dev1/Ag4).

## Asking the right question — the lens catalog (D-035)

A decision yields to the *right* question, not to *all* questions. These are the house lenses; the craft is smelling which two or three will crack the decision at hand. Live proof: D-004 was cracked by a single Space question — "where does 72×20 actually come from?" (answer: the width of a deleted terminal) — not by twenty answers.

| Lens | Its sharpest questions | House receipt |
|---|---|---|
| ⏳ **Time** | When does this bite? What is its half-life? One-way or two-way door? When is the last responsible moment? | D-023/D-024 parked; D-031 taken early — both are Time answers |
| 📍 **Space** | Where does this live? Which side of which boundary? How far does the blast radius reach? | A1; the D-013 grep; the 72×20 fossil |
| 👥 **Who** | Who touches it, who pays, who can break it, who decides? | D-030, D-034, The Door |
| 🎯 **Why** | Why now, why at all? What dies if we skip it? Ask it five times in a row. | D-022: the counter died, the ledger was born |
| ⚙️ **Failure** | How do we know at 2am? What breaks first? How is it *proven*? | The mandatory Confirmation section; the digest chain |
| 🔄 **Inversion** | How would we guarantee failure? What happens if we do the opposite? What is this NOT? | D-014: "fix Smith → no film → delete the repo while at it" |
| 📈 **Scale** | What breaks at 10×? At 1000×? Which parts grow with load and which stay flat? | D-017/D-018; the 5,000-entity budget |
| 🎬 **Lore** | What does the fiction say? Is this the mechanic's true name or merely a cute one? | D-003, D-013; Dev8 |
| 🪜 **Maturity** | Desired / needed / feasible / committed / done — the owner's five. | The v1 gate creative pass |
| 🥄 **Spoon** | Is the problem real, or is only our framing of it real? Can *deletion* beat construction? | D-019: the question was never "how to render" — it was "why render at all". There is no spoon. |
| 🤫 **Secret** | Who is allowed to know what? What must one part *never* learn about another? | The Matrix cannot tell link kinds apart (D-032); entities know nothing of pods; the blue pill is an information policy |
| 🍖 **Pet** | Every yes is a mouth to feed: who feeds this, and what does it eat — attention, allocation, upkeep? | D-009: a build tool with nothing to build is a hungry pet; Dev4 crown upkeep |

Rule of use: pick the two or three lenses that fit the gate, and *say which you used* — the Y-statement is the residue of good lens work. Running all twelve on everything is not rigor; it is bureaucracy wearing rigor's coat.

## References

- Nygard, *Documenting Architecture Decisions* (2011) — the origin.
- [adr.github.io](https://adr.github.io/) — the ADR home.
- [MADR](https://adr.github.io/madr/) — our template's source.
- [AD practices](https://adr.github.io/ad-practices/) — drivers, lifecycle, decision definition-of-done.
- Zdun, Capilla, Tran, Zimmermann — Y-statements.
- [architecture-decision-record](https://github.com/architecture-decision-record/architecture-decision-record) — template zoo and examples.

*Note for AI sessions: this file plus TEMPLATE.md is your decision-writing onboarding. The Confirmation section is not optional for you either.*
