# docs/spec/ — the portable shelf

Rules of the world, written so a stranger's implementation can verify itself
against them (D-058). The shelf exists because of one measured fact: sixteen
years after The Matrix Online's servers closed, its emulator scene has restored
the whole explorable world and still cannot restore combat — because the world
was data and the combat was logic inside a closed implementation.

**What a spec is:** a byte grammar, a record kind, a derivation, a line family —
stated implementation-independently, with conformance vectors a foreign
implementation can check itself against.

**What a spec is not:** narrative, rationale, or history. Those belong to the
five canon documents and to the MADR records under `docs/adr/`. A spec says what
is true of the world; it never says why we chose it.

**The price of admission:** a spec is checked against the running implementation
by a probe that reads it, so a stale spec fails a build instead of misleading a
reader. A spec nobody can falsify is not a spec — it is a wish with a filename.

**The shelf was empty and the reader did not exist.** This paragraph used to
name `SpecDrift` in the present tense when there was no such probe and nothing
for it to read (#1243), and the repair then was to date the promise rather than
to keep it undated:

```sh
ls docs/spec/                              # README.md, and nothing else
grep -rn 'SpecDrift' probes/ tools/ src/   # nothing
```

**The first spec paid it** (#603). `instrument-lines.md` landed with
`probes/SpecDrift.java` in one pull request — this shelf's own rule applied to
its first inhabitant rather than deferred past it — and the rule stands for the
second: a spec lands with the probe that reads it, or it does not land.

The order matters because this tree spent one day finding the same shape four
times elsewhere: a workflow that could not parse for eleven runs (#1203), a
fourteen-case suite no lane executed (#1212), a catalog row claiming a judge
that was never a step (#1210), and the determinism pass that had never run at
all (#1233). Every one was a reader promised and not built. This is the fifth,
caught before it could mislead anybody, and the repair is to date the promise
rather than to keep it undated.

| Spec | Covers |
|---|---|
| [instrument-lines.md](instrument-lines.md) | The family-wide laws and the eight-family roster, read on every push by `probes/SpecDrift.java` (#603) |
