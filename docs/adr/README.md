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

## References

- Nygard, *Documenting Architecture Decisions* (2011) — the origin.
- [adr.github.io](https://adr.github.io/) — the ADR home.
- [MADR](https://adr.github.io/madr/) — our template's source.
- [AD practices](https://adr.github.io/ad-practices/) — drivers, lifecycle, decision definition-of-done.
- Zdun, Capilla, Tran, Zimmermann — Y-statements.
- [architecture-decision-record](https://github.com/architecture-decision-record/architecture-decision-record) — template zoo and examples.

*Note for AI sessions: this file plus TEMPLATE.md is your decision-writing onboarding. The Confirmation section is not optional for you either.*
