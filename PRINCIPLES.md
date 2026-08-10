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
