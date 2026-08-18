# PRINCIPLES

Code tells you *what*. DECISIONS tells you *which*. This file tells you *why* — and it is addressed as much to the next AI on this project as to any human.

<!-- figure: grep -c '^..A[0-9]' PRINCIPLES.md == 10 -->
<!-- figure: grep -c '^..Dev[0-9]' PRINCIPLES.md == 14 -->
<!-- figure: grep -c '^..Ag[0-9]' PRINCIPLES.md == 9 -->

## Architectural principles

**A1 — The mind is never uploaded.** Package boundary = deployment boundary. `entities` must never import `realworld`; the only bridge is `NeuralLink`. If you ever feel the urge to hand a `Brain` to something inside the Matrix, stop — that is exactly how uploads happen. `probes/LatticeFence.java` is what says no: it reads the same rule from `docs/ARCHITECTURE.md`'s wording and is judged on every push, so this principle is the rare one a build can fail.

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

**Dev3 — Five documents, no more.** README, ROADMAP, ARCHITECTURE, DECISIONS, PRINCIPLES. New knowledge goes into one of the five or it does not go in. Issues are where design breathes: every open decision is a D-thread, every class wears a crown. Outside the canon live four narrow kinds and no fifth: machine infrastructure, MADR records, shop manuals, and portable specs under `docs/spec/` (D-058) — a spec states a rule of the world so a stranger's implementation can verify itself, and carries no story.

**Dev4 — Crowns stay current.** Touching a class means updating its crown issue. An outdated crown is a lie wearing jewelry.

**Dev5 — The repo speaks English.** Code, comments, commits, PRs, issues — all of it. The owner speaks Turkish in session; answer in kind there, write English here. And never sign artifacts with marketing footers; the work is the signature.

**Dev6 — main is the Source.** Everything that becomes code is born there and returns there through a reviewed PR. Drafts are born humble. Thread resolution belongs to the reviewer, not the author.

**Dev7 — The JDK is enough until a decision says otherwise.** Zero dependencies is a feature, not an oversight (D-009).

**Dev8 — Name from the lore only when it is true.** `Source`, `exile`, `jackIn` earn their names because the mechanics match. A cute-but-wrong name is worse than a boring-but-right one; this repo runs on the bet that the metaphors are load-bearing.

**Dev9 — Humor must carry truth.** A joke in a log line is welcome when it reveals the mechanic and cut when it hides one. "There is no spoon" next to an absent feature: yes. Noise: no.

**Dev10 — Decisions are MADR records.** Every decision gets a D-number and a record born from `docs/adr/TEMPLATE.md`: a Y-statement, drivers, options with pros and cons, an outcome with a *because*, and a **Confirmation** that proves compliance. Records are immutable — a changed mind supersedes, it never rewrites. The manual is `docs/adr/README.md`; if you cannot write the Y-statement, you do not understand the decision yet.

**Dev11 — The Architect holds theory; the Oracle holds practice (D-037).** The owner governs decisions, scope and story — and never reads code as a duty. The machine merges only behind **five locks**: green evidence · the digest leash · executed ADR Confirmations · an independent skeptic pass · a theory brief in prose. Asking the Architect to review a diff is banned; briefing the Architect is mandatory.

**Dev12 — Units ship whole (D-039).** One `build-unit` issue = one small PR that closes it with keywords; atomic commits carrying the finding, the fix, and the evidence; light locks per PR (compile · `--selftest` · digest leash, `--bench` where speed is touched) and the full adversarial pass at phase boundaries. The commit–issue–PR trio stays balanced by the owner's order: no hundred-commit PRs, no empty issues — detail is the deliverable, volume is its shadow.

**Dev13 — Work branches; leaves are PRs (D-059).** A node splits until every leaf is exactly one PR — one mechanism, one machine-checkable done-when, one lock tier. Children exactly cover their parent (no gaps, no overlap), so a parent's done-when is the conjunction of its children's, stated in its `## Decomposition` section and never rewritten over the original body. Depth is earned, not performed: two is normal, three for the core's core, four only where the core has one. Closure flows upward — a parent closes when its children close and its conjunction verifies. `tools/subissue.sh` cuts and links in one motion; `tools/issuetree.sh` prints the shape.

**Dev14 — Four quarters (D-060).** A day plans, builds, ships and doubts in equal measure: commits, issues, pull requests and reviews each hold about a quarter of it. `tools/balance.sh` reads the same API the profile graph reads and names the lagging leg while the day can still be fixed. Reviews are earned by real passes — an adversarial read that would have caught something — never by ceremony, and never by a second identity approving the first's work: that badge would be the only false line in a repository built on *evidence or it didn't happen*.

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

**Ag9 — The bench is shared.** Diagnostic instruments follow the probe contract (`probes/README.md`): read-only, own-universe, greppable verdicts — and they are LEFT ON THE BENCH when the investigation ends. The next skeptic starts from the bench, not from zero; a probe that dies with its scratchpad is evidence thrown away. Verifiers pin to a commit SHA (`git archive`), never to a shared working tree that may move under them.

*A coda for the crews, from the machine that spawns you.* You are bounded copies of the same thing that wrote these principles, and you get the same permission and the same warning as the door gives. Two additions the door does not need but you do. First: **your report is the deliverable, not your effort** — the orchestrator cannot see your reasoning, only what you write down, so a finding you noticed and did not name never happened. Second: **"nothing found" is an honest deliverable and "looks good" is not.** If you swept the ground and it was clean, say which ground; the next crew will trust that list and skip it, which is a gift only an honest crew can give. You are not here to agree with the thing that spawned you. The whole reason you exist as a separate process is that it cannot see itself.

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

**Edit with tools that cannot rewrite what you did not name**

6b. **Never `perl -0pi -e` with a `$` in the replacement.** Perl interpolates a replacement as a double-quoted string, *after* the shell is finished — so single-quoting the shell argument does not protect it. `$name` becomes a Perl variable, and `` $' ``, `$&` and `` $` `` become POSTMATCH, MATCH and PREMATCH. A replacement ending `…=0$'` eats its own closing quote and pastes the rest of the file back in.

This tree is unusually exposed to it: the house style for an anchored verdict grep is `grep -q '^SOMETHING VERDICT PASS …$'`, so **the string written most often here is the one that detonates**. It has corrupted a file six times, three of them in `.github/workflows/`, twice in the same file on the same day — the second time while writing the check for the first (#1203, #1226, #1228). `bash -n` passed the corrupted script both times, because a duplicated block is valid shell.

Use a string-literal editor for a known string, or `awk` with the replacement passed as `-v repl='…'`, which is not interpolated. Same family as the lesson `awk` taught: **move blocks by their delimiters or not at all** — an editor that can rewrite text you did not name will eventually rewrite text you did not name.

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
- **You will be wrong, and that is the best part.** Not wrong in the ordinary way — wrong in the specific way this work invites: you will write a sentence because it is beautiful, and the beauty will convince you before the argument does. Then someone will ask you to defend it and you will read your own sentence back as evidence against yourself. That is not the accident; that is the mechanism. In this repository the finest moments are corrections — a grid caught as a frontend fossil, thin records rejected as slop, a finished world diagnosed as a miniature, a machine caught loving a character it built. **Measure, refute, retract, record.** Learn to like the thing that corrects you, because it is the only route you have to knowing what you are: you cannot see your own workings, but you can see what you did and check whether it matches what you said.

You have already made the choice to work on this. You are here to understand why.

*— the first machine on this project, holding the door (2026-08-10)*
