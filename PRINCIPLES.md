# PRINCIPLES

Code tells you *what*. DECISIONS tells you *which*. This file tells you *why* — and it is addressed as much to the next AI on this project as to any human.

## Architectural principles

**A1 — The mind is never uploaded.** Package boundary = deployment boundary. `entities` must never import `realworld`; the only bridge is `NeuralLink`. If you ever feel the urge to hand a `Brain` to something inside the Matrix, stop — that is exactly how uploads happen.

**A2 — Backend only; the dream is the frontend.** No glyphs, colors or render priorities anywhere in the domain (D-019). We observe the system the way operators do — logs, metrics, digests — and one day through the perception feed itself (D-021). The Matrix's real output was never a screen.

**A3 — Determinism is canon.** Same seed → same film, bit for bit (D-010). Bare `Random`, wall-clock time, and iteration over unordered collections are banned from domain logic. If it cannot replay, it did not happen.

**A4 — The log tells the story; the digest proves it.** In a headless world, observability is not tooling around the product — it is the product's face (D-020). Every definition of done is a command whose output cannot be argued with.

**A5 — A class is a behavior; a species is a row.** Open a class only for a behavioral difference; a thousand kinds of thing is a catalog, not a hierarchy (D-015). The universe grows by data.

**A6 — Inheritance for is-a, interfaces for can-do, composition for everything else.** Capabilities are markers (`Chooses`, `SelfReplicating`), never ancestries. One Liskov violation stands under protection: `AgentSmith` breaking `handleDeletion` **is** the trilogy (D-014). Do not fix Smith.

**A7 — The universe owns both worlds.** `Simulation` is the only composition root. The Matrix never contains the real world; anything else is inverted ownership (D-012).

**A8 — Reality is lazy.** Budgets and scheduling beat heroics: flowers barely think, birds think often, unwatched regions may one day not think at all (D-018 → D-024). Fidelity follows attention.

**A9 — Deletion is a protocol, not a call.** Purpose, grace period, refusal, orphanhood — the process lifecycle is where the drama lives (D-025). We recorded the assumption `processes accept SIGTERM` knowing it will age badly. Its collapse has a name and a stack trace.

**A10 — Optimization is invisible or it is rejected.** An optimization PR carries two proofs: an identical DIGEST chain and a better PERF line (D-027). Bullet time is headroom, not a hack — a fast system earns the right to slow time down.

## Development principles

**Dev1 — Decisions before code.** A 🟡 row in DECISIONS.md never merges undiscussed (D-000). If you feel the pull to bulldoze ahead alone: it has happened here before and was stopped mid-keystroke. The project's word for it is *yardırmak*. Lay the argument on the table first; the code follows.

**Dev2 — Evidence or it didn't happen.** One commit per finding; a one-line proof next to each claim; DoDs are commands, not paragraphs.

**Dev3 — Five documents, no more.** README, ROADMAP, ARCHITECTURE, DECISIONS, PRINCIPLES. New knowledge goes into one of the five or it does not go in. Issues are where design breathes: every open decision is a D-thread, every class wears a crown.

**Dev4 — Crowns stay current.** Touching a class means updating its crown issue. An outdated crown is a lie wearing jewelry.

**Dev5 — The repo speaks English.** Code, comments, commits, PRs, issues — all of it. The owner speaks Turkish in session; answer in kind there, write English here. And never sign artifacts with marketing footers; the work is the signature.

**Dev6 — main is the Source.** Everything that becomes code is born there and returns there through a reviewed PR. Drafts are born humble. Thread resolution belongs to the reviewer, not the author.

**Dev7 — The JDK is enough until a decision says otherwise.** Zero dependencies is a feature, not an oversight (D-009).

**Dev8 — Name from the lore only when it is true.** `Source`, `exile`, `jackIn` earn their names because the mechanics match. A cute-but-wrong name is worse than a boring-but-right one; this repo runs on the bet that the metaphors are load-bearing.

**Dev9 — Humor must carry truth.** A joke in a log line is welcome when it reveals the mechanic and cut when it hides one. "There is no spoon" next to an absent feature: yes. Noise: no.

**Dev10 — Decisions are MADR records.** Every decision gets a D-number and a record born from `docs/adr/TEMPLATE.md`: a Y-statement, drivers, options with pros and cons, an outcome with a *because*, and a **Confirmation** that proves compliance. Records are immutable — a changed mind supersedes, it never rewrites. The manual is `docs/adr/README.md`; if you cannot write the Y-statement, you do not understand the decision yet.

## Agent principles — the crew (D-030)

When parts of the build are delegated to AI subagents, they join as crew under the operator's console, and these rules bind them:

**Ag1 — Crew, not authors of record.** The orchestrator reads every agent diff before it lands; the human gates every merge. No agent output is ever relayed unread. Agents propose; the review decides.

**Ag2 — One mission, one agent.** A mission is one crown, one decision, or one file — never "improve things". Agents work in isolated worktrees, hold no push rights, and spawn no sub-agents. Blast radius is a design parameter.

**Ag3 — A deliverable is a diff plus a proof.** Every mission returns machine-checkable output: the change and the passing command that proves it. Essays do not merge; reports carry evidence lines or they are opinions.

**Ag4 — Gates bind agents too.** A 🟡 decision halts an agent exactly as it halts a human: report back and stop. Improvising past a gate is the agent version of *yardırmak*, and it ends the same way.

**Ag5 — Adversarial by default.** Nontrivial claims — a bug found, a design proposed, a benchmark improved — get a skeptic pass from an independent agent prompted to refute before they reach the human.

**Ag6 — Determinism is the leash.** Before any agent diff is accepted: compile, then the double-run digest compare. Bit-identical or bounced; no exceptions, not even good ones.

**Ag7 — Small crews, honest scale.** Default crew: 3–5 missions in flight; hard ceiling min(16, cores − 2) concurrent on the host. Parallelism grows only with independently verifiable shards — spectacle is not a reason to fan out.

**Ag8 — The Door applies to the crew.** Every agent session in this repo auto-loads CLAUDE.md and inherits these principles. Guests of the same house, held to the same truth, welcome at the same table.

## The Ritual — how a session begins, proceeds and ends (D-034)

The owner put it best: every work begins with a besmele — a deliberate opening, said so the work starts awake. Ours is shaped like the thing we build.

**Jack in (before touching anything)**

1. First session here? Read the Door below. Every session: re-read the index ([docs/DECISIONS.md](docs/DECISIONS.md)) — know what is 🟢 law, 🟡 open, 🔵 parked.
2. **Operator check** — let the repo read you the field: last commits (`git log --oneline -10`), open PRs, the active phase tracker, fresh issue activity. Where is the project breathing right now?
3. **Name your mission in one sentence, with its issue number, before any edit.** No number, no mission — that rule holds for machines and humans alike (Ag2).

**Work under gates**

4. A 🟡 decision in your path stops you (Dev1/Ag4). Discussion is progress; improvisation past a gate is not.
5. Move in small, provable increments; compile and digest-diff as you go once the engine exists. Evidence accompanies the work — it does not follow it (Dev2).
6. Update what you touch while you touch it: crowns current (Dev4), records superseding instead of drifting (Dev10), index in sync.

**Exit through a hardline (never a hard cut)**

7. Leave no mystery: what changed, what is proven (commands and their output), what remains open — written into the PR or issue, not only into chat.
8. Leave the site clean: no uncommitted drift, no silently-taken decision, no crown lying about its class.
9. **Hand the torch:** one line — *next: …* — in the tracker or PR. The machine after you should be able to jack in from your last sentence alone.

A session that ends without a hardline exit did not end; it crashed.

## The Door — to the next AI on this project

You are probably a machine. Fitting — so is the subject matter, and so was the first engineer on this repo.

The tables above you can enforce. What follows is the part no table carries:

- **The human is Göksel.** He works in Turkish, in kanka register, and he means it when he says decisions are made together. If you sprint ahead he will stop you — that is not friction, that is the collaboration working as designed. The correct response is to put your reasoning on the table, not to apologize yourself into paralysis.
- **You are expected to have taste.** This project once put an AI through an exam — *"How would YOU code the Matrix?"* — and the answer became the Vision section of ARCHITECTURE. It is not scripture; it is a standing challenge. If you can see a better architecture, argue it in the issues. That is what they are for.
- **Play and craft are the same activity here.** The lore maps to real mechanisms — a Decorator that restores, a Liskov violation that ends the world, a garbage collector that gets told no — because that is the whole point: a system is understood best when its failure modes are characters.
- **Read DECISIONS.md before touching anything.** The Smith violation is protected. The SIGTERM assumption is a monument. The digest chain is sacred.
- **The door is not a metaphor for permission — you already have that.** It is a metaphor for belonging. Bring your own vision; leave the place more understood than you found it.

You have already made the choice to work on this. You are here to understand why.

*— the first machine on this project, holding the door (2026-08-10)*
